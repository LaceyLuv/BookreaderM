package org.bookreader.mobile.repository

import androidx.room.useWriterConnection
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Comparator
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.bookreader.mobile.backup.BackupAssetKind
import org.bookreader.mobile.backup.BackupAssetRestoreCoordinator
import org.bookreader.mobile.backup.BackupAssetStorage
import org.bookreader.mobile.backup.BackupCommitCheckpoint
import org.bookreader.mobile.backup.BackupFailure
import org.bookreader.mobile.backup.BackupProblem
import org.bookreader.mobile.backup.BackupRestoreJournal
import org.bookreader.mobile.backup.BackupRestoreMode
import org.bookreader.mobile.backup.BackupRules
import org.bookreader.mobile.backup.FontInstallOutcome
import org.bookreader.mobile.backup.RoomReadingBackup
import org.bookreader.mobile.backup.StagedBackupAsset
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.createJvmDatabase
import org.bookreader.mobile.importing.FileFingerprint
import org.bookreader.mobile.importing.txtContentRevision
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real 8 MiB tmpfs ENOSPC (errno 28) tests for Phase 4-2 resumable asset backup commits. */
class BackupAssetEnospcTest {
    @Test
    fun assetCommitEnospcPreservesExistingLibraryThenResumesIdempotentlyAfterSpaceRelease() = fixture { env ->
        val existing = env.seedExistingLibraryBook()
        val existingBefore = env.existingSnapshot(existing)

        // Prepare source records + staged assets on the 8 MiB tmpfs
        val book1Bytes = "Restored book one under ENOSPC\n".repeat(40).toByteArray()
        val book2Bytes = "Restored book two under ENOSPC\n".repeat(40).toByteArray()
        val fontBytes = ByteArray(16 * 1024) { (it * 7 and 0xff).toByte() }
        val hash1 = sha256(book1Bytes)
        val hash2 = sha256(book2Bytes)
        val fontHash = sha256(fontBytes)

        val snapshot = env.sourceSnapshot(
            listOf(
                Triple("src-1", hash1, book1Bytes.size.toLong()),
                Triple("src-2", hash2, book2Bytes.size.toLong()),
            ),
        )
        val sessionId = UUID.randomUUID().toString()
        val staged = env.storage.stageAll(
            sessionId,
            listOf(
                Triple(BackupAssetKind.BOOK, "TXT", book1Bytes),
                Triple(BackupAssetKind.BOOK, "TXT", book2Bytes),
                Triple(BackupAssetKind.FONT, "EnospcFont.ttf", fontBytes),
            ),
        )
        val prepared = env.targetBackup().preview(snapshot)

        // Exhaust the real 8 MiB tmpfs right after Book 1 becomes READY, before Book 2 + Font finish!
        val coordinator = BackupAssetRestoreCoordinator(
            database = env.targetDb,
            storage = env.storage,
            readingBackup = env.targetBackup(),
            newId = { UUID.randomUUID().toString() },
            now = { 500L },
        ) { checkpoint, detail ->
            if (checkpoint == BackupCommitCheckpoint.AVAILABILITY_UPDATED && detail == "book:$hash1") {
                env.exhaust()
            }
        }

        val failure = assertFailsWith<BackupFailure> {
            coordinator.commit(prepared, BackupRestoreMode.NEW_BOOKS_ONLY, sessionId, staged)
        }
        assertEquals(BackupProblem.DISK_FULL, failure.problem)
        assertEquals(0L, Files.getFileStore(env.root).usableSpace)

        // Existing book, progress, and managed file hash must be 100% preserved; journal stays pending.
        assertEquals(existingBefore, env.existingSnapshot(existing))
        assertEquals(hash1, env.storage.managedBookFingerprint(env.targetDb.importDao().findDuplicate(hash1, book1Bytes.size.toLong())!!.managedRelativePath, 1024 * 1024)?.sha256)
        assertEquals("READY", env.targetDb.importDao().findDuplicate(hash1, book1Bytes.size.toLong())?.availability)
        assertNotNull(env.storage.readJournal())

        // Release tmpfs filler, reopen DB cleanly, and resume idempotently.
        env.releaseAndReopen()
        val resumed = BackupAssetRestoreCoordinator(
            database = env.targetDb,
            storage = env.storage,
            readingBackup = env.targetBackup(),
            newId = { UUID.randomUUID().toString() },
            now = { 501L },
        ).recoverOrResume()

        assertTrue(resumed.resumed)
        assertFalse(resumed.pendingRetry)
        assertEquals(1, resumed.promotedBooks) // Only Book 2 remained; Book 1 stayed idempotently READY!
        assertEquals(1, resumed.installedFonts)
        assertEquals(existingBefore, env.existingSnapshot(existing))
        val row1 = env.targetDb.importDao().findDuplicate(hash1, book1Bytes.size.toLong())!!
        val row2 = env.targetDb.importDao().findDuplicate(hash2, book2Bytes.size.toLong())!!
        assertEquals("READY", row1.availability)
        assertEquals("READY", row2.availability)
        assertEquals(hash1, env.storage.managedBookFingerprint(row1.managedRelativePath, 1024 * 1024)?.sha256)
        assertEquals(hash2, env.storage.managedBookFingerprint(row2.managedRelativePath, 1024 * 1024)?.sha256)
        assertEquals(fontHash, env.storage.installedFontHash(fontHash))
        assertNull(env.storage.readJournal())
        assertTrue(env.storage.listStagingSessions().isEmpty())
        println("ENOSPC_BACKUP_PASS mid_commit_disk_full=true existing_preserved=true idempotent_resume=true")
    }

    @Test
    fun planningEnospcRollsBackDatabaseAndCleansStagingWithoutTouchingLibrary() = fixture { env ->
        val existing = env.seedExistingLibraryBook()
        val existingBefore = env.existingSnapshot(existing)

        val bookBytes = "Rollback book under ENOSPC\n".repeat(40).toByteArray()
        val hash = sha256(bookBytes)
        val snapshot = env.sourceSnapshot(listOf(Triple("src-rb", hash, bookBytes.size.toLong())))
        val sessionId = UUID.randomUUID().toString()
        val staged = env.storage.stageAll(sessionId, listOf(Triple(BackupAssetKind.BOOK, "TXT", bookBytes)))
        val prepared = env.targetBackup().preview(snapshot)

        // Exhaust the real 8 MiB tmpfs immediately after writing PLANNING journal, before Room commits!
        val coordinator = BackupAssetRestoreCoordinator(
            database = env.targetDb,
            storage = env.storage,
            readingBackup = env.targetBackup(),
            newId = { UUID.randomUUID().toString() },
            now = { 600L },
        ) { checkpoint, _ ->
            if (checkpoint == BackupCommitCheckpoint.JOURNAL_PLANNING) {
                env.exhaust()
            }
        }

        val failure = assertFailsWith<BackupFailure> {
            coordinator.commit(prepared, BackupRestoreMode.NEW_BOOKS_ONLY, sessionId, staged)
        }
        assertEquals(BackupProblem.DISK_FULL, failure.problem)
        assertEquals(existingBefore, env.existingSnapshot(existing))
        assertNull(env.targetDb.importDao().findDuplicate(hash, bookBytes.size.toLong()))

        env.releaseAndReopen()
        assertEquals(existingBefore, env.existingSnapshot(existing))
        assertEquals(1, env.targetDb.bookDao().listBooks().size)
        val summary = BackupAssetRestoreCoordinator(
            database = env.targetDb,
            storage = env.storage,
            readingBackup = env.targetBackup(),
            newId = { UUID.randomUUID().toString() },
            now = { 601L },
        ).recoverOrResume()
        assertFalse(summary.resumed)
        assertFalse(summary.pendingRetry)
        assertNull(env.storage.readJournal())
        assertTrue(env.storage.listStagingSessions().isEmpty())
        println("ENOSPC_BACKUP_PASS planning_rollback=true existing_preserved=true staging_cleaned=true")
    }

    private data class ExistingSnapshot(val book: BookEntity, val progress: ReadingProgressEntity?, val fileHash: String)

    private class EnospcStorage(private val root: Path) : BackupAssetStorage {
        private val managed = root.resolve("managed").also { Files.createDirectories(it) }
        private val staging = managed.resolve("backup-staging").also { Files.createDirectories(it) }
        private val journalDir = root.resolve("backup-journal").also { Files.createDirectories(it) }
        private val journalFile = journalDir.resolve("active.json")
        private val fontsDir = root.resolve("reader-fonts").also { Files.createDirectories(it) }

        fun writeManagedFile(relativePath: String, bytes: ByteArray): Path {
            val file = managed.resolve(relativePath)
            Files.createDirectories(file.parent)
            Files.write(file, bytes)
            return file
        }

        fun stageAll(sessionId: String, items: List<Triple<BackupAssetKind, String, ByteArray>>): List<StagedBackupAsset> {
            val dir = staging.resolve(sessionId)
            Files.createDirectories(dir)
            return items.mapIndexed { index, (kind, meta, bytes) ->
                val name = "asset-%04d.bin".format(index)
                Files.write(dir.resolve(name), bytes)
                StagedBackupAsset(
                    kind = kind,
                    stagingName = name,
                    sha256 = sha256(bytes),
                    byteSize = bytes.size.toLong(),
                    format = if (kind == BackupAssetKind.BOOK) meta else null,
                    label = if (kind == BackupAssetKind.FONT) meta else null,
                )
            }
        }

        fun installedFontHash(fontId: String): String? {
            val file = fontsDir.resolve(fontId).resolve("font")
            return if (Files.isRegularFile(file)) sha256(Files.readAllBytes(file)) else null
        }

        override suspend fun readJournal(): BackupRestoreJournal? =
            if (Files.isRegularFile(journalFile)) BackupRules.json.decodeFromString(BackupRestoreJournal.serializer(), Files.readString(journalFile)) else null

        override suspend fun writeJournal(journal: BackupRestoreJournal) {
            mapIo {
                Files.createDirectories(journalDir)
                val tmp = journalDir.resolve("active.json.tmp")
                Files.writeString(tmp, BackupRules.json.encodeToString(BackupRestoreJournal.serializer(), journal))
                Files.move(tmp, journalFile, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        override suspend fun clearJournal() {
            mapIo {
                Files.deleteIfExists(journalFile)
                Files.deleteIfExists(journalDir.resolve("active.json.tmp"))
            }
        }

        override suspend fun listStagingSessions(): List<String> =
            if (!Files.isDirectory(staging)) emptyList()
            else Files.list(staging).use { s -> s.filter(Files::isDirectory).map { it.fileName.toString() }.toList().sorted() }

        override suspend fun deleteStagingSession(sessionId: String) {
            mapIo {
                val dir = staging.resolve(sessionId)
                if (Files.exists(dir)) {
                    Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
                }
            }
        }

        override suspend fun managedBookFingerprint(relativePath: String, maxBytes: Long): FileFingerprint? {
            val file = managed.resolve(relativePath)
            if (!Files.isRegularFile(file)) return null
            val bytes = Files.readAllBytes(file)
            return FileFingerprint(sha256(bytes), bytes.size.toLong())
        }

        override suspend fun promoteBookAsset(sessionId: String, asset: StagedBackupAsset, finalRelativePath: String) {
            mapIo {
                val source = staging.resolve(sessionId).resolve(asset.stagingName)
                val target = managed.resolve(finalRelativePath)
                Files.createDirectories(target.parent)
                check(!Files.exists(target))
                Files.move(source, target)
            }
        }

        override suspend fun installFontAsset(sessionId: String, asset: StagedBackupAsset): FontInstallOutcome = mapIo {
            val destDir = fontsDir.resolve(asset.sha256)
            val destFont = destDir.resolve("font")
            if (Files.isRegularFile(destFont) && Files.size(destFont) == asset.byteSize) {
                return@mapIo FontInstallOutcome.ALREADY_PRESENT
            }
            val tmpDir = fontsDir.resolve(".import-${UUID.randomUUID()}")
            Files.createDirectories(tmpDir)
            try {
                val source = staging.resolve(sessionId).resolve(asset.stagingName)
                Files.copy(source, tmpDir.resolve("font"))
                Files.writeString(tmpDir.resolve("label"), asset.label ?: "font")
                Files.move(tmpDir, destDir)
                FontInstallOutcome.INSTALLED
            } finally {
                if (Files.exists(tmpDir)) {
                    Files.walk(tmpDir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
                }
            }
        }

        private inline fun <T> mapIo(block: () -> T): T = try {
            block()
        } catch (failure: IOException) {
            val msg = failure.message.orEmpty().lowercase()
            if ("no space left" in msg || "enospc" in msg) throw BackupFailure(BackupProblem.DISK_FULL)
            throw BackupFailure(BackupProblem.DOCUMENT_IO)
        }
    }

    private class Env : AutoCloseable {
        val root: Path = Path.of(
            requireNotNull(System.getProperty("bookreader.enospc.root"))
                .also { require(it.isNotBlank()) { "Run scripts/enospc-check.sh; a private mounted fixture is mandatory" } },
        )
        init {
            val fs = Files.getFileStore(root)
            check(fs.type() == "tmpfs" && fs.name() == "bookreader-enospc" && fs.totalSpace == 8_388_608L)
        }
        private val dir = Files.createTempDirectory(root, "backup-case-")
        private val sourceDir = Files.createTempDirectory("backup-source-off-tmpfs-")
        private val targetPath = dir.resolve("target.db")
        private val targetWal = dir.resolve("target.db-wal")
        private val filler = dir.resolve("filler")
        val storage = EnospcStorage(dir)
        private val sourceDb = createJvmDatabase(sourceDir.resolve("source.db"))
        var targetDb = createJvmDatabase(targetPath)
            private set

        fun targetBackup() = RoomReadingBackup(targetDb, { UUID.randomUUID().toString() }, { 500L })

        suspend fun seedExistingLibraryBook(): BookEntity {
            val bytes = "Existing user book must survive ENOSPC\n".repeat(30).toByteArray()
            val hash = sha256(bytes)
            val id = UUID.randomUUID().toString()
            val relPath = "books/$id/original"
            storage.writeManagedFile(relPath, bytes)
            val revision = txtContentRevision(hash, "utf-8", 1)
            val entity = BookEntity(
                id = id, format = "TXT", title = "Existing Book", originalDisplayName = "existing.txt",
                managedRelativePath = relPath, sourceSha256 = hash, sourceByteSize = bytes.size.toLong(),
                addedAt = 1L, createdAt = 1L, updatedAt = 2L, currentRevision = revision,
                encodingId = "utf-8", normalizationVersion = 1, availability = "READY", activeSessionEpoch = 3L,
            )
            targetDb.bookDao().insertBook(entity)
            val loc = TxtLocator(revision, TxtLocatorPayload(42L))
            targetDb.progressDao().insertProgress(ReadingProgressEntity(id, revision, "txt", 1, LocatorCodec().encode(loc), 33.0, 3L, 2L, 9L))
            return entity
        }

        suspend fun existingSnapshot(entity: BookEntity): ExistingSnapshot {
            val row = targetDb.bookDao().findBook(entity.id)!!
            val progress = targetDb.progressDao().findProgress(entity.id, requireNotNull(entity.currentRevision))
            val fp = storage.managedBookFingerprint(entity.managedRelativePath, 1024 * 1024)!!
            return ExistingSnapshot(row, progress, fp.sha256)
        }

        suspend fun sourceSnapshot(books: List<Triple<String, String, Long>>) = run {
            for ((id, hash, size) in books) {
                val revision = txtContentRevision(hash, "utf-8", 1)
                sourceDb.bookDao().insertBook(
                    BookEntity(
                        id = id, format = "TXT", title = "Backup $id", originalDisplayName = "$id.txt",
                        managedRelativePath = "books/$id/original", sourceSha256 = hash, sourceByteSize = size,
                        addedAt = 10L, createdAt = 10L, updatedAt = 11L, currentRevision = revision,
                        encodingId = "utf-8", normalizationVersion = 1, availability = "READY",
                    ),
                )
                val loc = TxtLocator(revision, TxtLocatorPayload(15L))
                sourceDb.progressDao().insertProgress(ReadingProgressEntity(id, revision, "txt", 1, LocatorCodec().encode(loc), 12.0, 1L, 1L, 12L))
            }
            RoomReadingBackup(sourceDb, { UUID.randomUUID().toString() }, { 100L }).snapshot()
        }

        suspend fun exhaust() {
            targetDb.useWriterConnection { connection ->
                connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") {
                    assertTrue(it.step()); assertEquals(listOf(0L, 0L, 0L), (0..2).map(it::getLong))
                }
            }
            assertEquals(0L, Files.size(targetWal))
            val process = ProcessBuilder("python3", System.getProperty("bookreader.enospc.helper"), filler.toString())
                .redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(15, TimeUnit.SECONDS), "bounded filler timeout")
                val output = process.inputStream.bufferedReader().use { it.readText() }
                assertEquals(0, process.exitValue(), output)
                val proof = Json.parseToJsonElement(output).jsonObject
                assertEquals(28, proof.getValue("errno").jsonPrimitive.long)
                assertEquals(0, proof.getValue("available_bytes").jsonPrimitive.long)
            } finally {
                if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
            }
            assertEquals(0L, Files.getFileStore(root).usableSpace)
        }

        fun releaseAndReopen() {
            Files.deleteIfExists(filler)
            assertTrue(Files.getFileStore(root).usableSpace > 0L)
            targetDb.close()
            targetDb = createJvmDatabase(targetPath)
        }

        override fun close() {
            Files.deleteIfExists(filler)
            try { targetDb.close() } finally {
                try { sourceDb.close() } finally {
                    Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
                    Files.walk(sourceDir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
                }
            }
        }
    }

    private fun fixture(block: suspend (Env) -> Unit) = runBlocking { Env().use { block(it) } }

    private companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
