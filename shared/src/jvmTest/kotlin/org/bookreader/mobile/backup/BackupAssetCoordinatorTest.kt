package org.bookreader.mobile.backup

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.createJvmDatabase
import org.bookreader.mobile.importing.FileFingerprint
import org.bookreader.mobile.importing.archiveContentRevision
import org.bookreader.mobile.importing.txtContentRevision
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupAssetCoordinatorTest {
    @Test
    fun bundleValidationRejectsTraversalDuplicatesOrphansAndLimitViolations() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        val fontHash = "f".repeat(64)
        val bookRecord = sampleBackupBook(hashA, 64L, BookFormat.TXT)
        val records = ReadingBackup(100L, listOf(bookRecord), emptyList(), null, null)

        val valid = BackupBundleV2(
            records = records,
            assets = listOf(
                BackupAssetEntry(BackupAssetKind.BOOK, "books/$hashA.bin", hashA, 64L, "TXT", null),
                BackupAssetEntry(BackupAssetKind.FONT, "fonts/$fontHash.bin", fontHash, 1024L, null, "TestFont.ttf"),
            ),
        )
        BackupAssetRules.validateBundle(valid)
        assertEquals(BackupAssetOverview(2, 1, 64L, 1, 1024L), BackupAssetRules.overview(valid))

        // Path traversal / absolute path / backslash / non-canonical name
        for (badPath in listOf("../books/$hashA.bin", "/books/$hashA.bin", "books\\$hashA.bin", "books/other.bin")) {
            val bad = valid.copy(assets = listOf(valid.assets[0].copy(path = badPath)))
            assertEquals(BackupProblem.FORMAT, assertFailsWith<BackupFailure> { BackupAssetRules.validateBundle(bad) }.problem)
        }

        // Duplicate entry path/key
        val dup = valid.copy(assets = listOf(valid.assets[0], valid.assets[0]))
        assertEquals(BackupProblem.FORMAT, assertFailsWith<BackupFailure> { BackupAssetRules.validateBundle(dup) }.problem)

        // Unreferenced orphan book blob
        val orphan = valid.copy(assets = listOf(BackupAssetEntry(BackupAssetKind.BOOK, "books/$hashB.bin", hashB, 64L, "TXT")))
        assertEquals(BackupProblem.FORMAT, assertFailsWith<BackupFailure> { BackupAssetRules.validateBundle(orphan) }.problem)

        // Font count limit (>12) and single font size limit (>20 MiB)
        val tooManyFonts = (0..12).map { i ->
            val h = "%064x".format(i + 1)
            BackupAssetEntry(BackupAssetKind.FONT, "fonts/$h.bin", h, 100L, null, "font-$i.ttf")
        }
        assertEquals(BackupProblem.LIMIT, assertFailsWith<BackupFailure> {
            BackupAssetRules.validateBundle(BackupBundleV2(records, tooManyFonts))
        }.problem)

        val hugeFont = BackupAssetEntry(BackupAssetKind.FONT, "fonts/$fontHash.bin", fontHash, BackupAssetRules.MAX_FONT_BYTES + 1, null, "huge.ttf")
        assertEquals(BackupProblem.LIMIT, assertFailsWith<BackupFailure> {
            BackupAssetRules.validateBundle(BackupBundleV2(records, listOf(hugeFont)))
        }.problem)
    }

    @Test
    fun coldAndMergeRestorePromoteBooksToReadyPreserveExistingOriginalsAndDeduplicateFonts() = fixture { root, source, target, storage ->
        val bytesA = "Alpha book content for Phase 4-2\n".repeat(4).toByteArray()
        val bytesB = "Beta book content for Phase 4-2\n".repeat(4).toByteArray()
        val fontBytes = ByteArray(256) { (it and 0xff).toByte() }
        val hashA = sha256(bytesA)
        val hashB = sha256(bytesB)
        val fontHash = sha256(fontBytes)

        val bookA = seedTxt(source, "src-a", hashA, bytesA.size.toLong(), 14L)
        val bookB = seedTxt(source, "src-b", hashB, bytesB.size.toLong(), 28L)
        val snapshot = backupService(source).snapshot()

        // Cold target restore with both books + 1 font
        val session1 = UUID.randomUUID().toString()
        val staged1 = storage.stageAll(
            session1,
            listOf(
                Triple(BackupAssetKind.BOOK, "TXT", bytesA),
                Triple(BackupAssetKind.BOOK, "TXT", bytesB),
                Triple(BackupAssetKind.FONT, "SampleFont.ttf", fontBytes),
            ),
        )
        val targetBackup = backupService(target)
        val coordinator = BackupAssetRestoreCoordinator(target, storage, targetBackup, { UUID.randomUUID().toString() }, { 200L })
        val outcome = coordinator.commit(targetBackup.preview(snapshot), BackupRestoreMode.NEW_BOOKS_ONLY, session1, staged1)

        assertEquals(2, outcome.records.newBooks)
        assertEquals(2, outcome.promotedBooks)
        assertEquals(1, outcome.installedFonts)
        assertEquals(0, outcome.skippedFonts)
        assertNull(storage.readJournal())
        assertTrue(storage.listStagingSessions().isEmpty())

        val restoredA = target.importDao().findDuplicate(hashA, bytesA.size.toLong())!!
        val restoredB = target.importDao().findDuplicate(hashB, bytesB.size.toLong())!!
        assertNotEquals(bookA.id, restoredA.id)
        assertNotEquals(bookB.id, restoredB.id)
        assertEquals("READY", restoredA.availability)
        assertEquals("READY", restoredB.availability)
        assertEquals(hashA, storage.managedBookFingerprint(restoredA.managedRelativePath, 1024L)?.sha256)
        assertEquals(hashB, storage.managedBookFingerprint(restoredB.managedRelativePath, 1024L)?.sha256)
        assertEquals(fontHash, storage.installedFonts[fontHash]?.let(::sha256))

        // Mark bookB as MISSING and delete its file; keep bookA READY with sentinel timestamp check.
        // Re-running with ADD_MISSING_RECORDS must never touch bookA, must restore bookB to READY, and must deduplicate the font.
        Files.delete(root.resolve("managed").resolve(restoredB.managedRelativePath))
        target.bookDao().markAvailability(restoredB.id, requireNotNull(restoredB.currentRevision), "MISSING", 210L)
        assertEquals("MISSING", target.bookDao().findBook(restoredB.id)?.availability)

        val session2 = UUID.randomUUID().toString()
        val staged2 = storage.stageAll(
            session2,
            listOf(
                Triple(BackupAssetKind.BOOK, "TXT", bytesA),
                Triple(BackupAssetKind.BOOK, "TXT", bytesB),
                Triple(BackupAssetKind.FONT, "SampleFont.ttf", fontBytes),
            ),
        )
        val mergeOutcome = coordinator.commit(targetBackup.preview(snapshot), BackupRestoreMode.ADD_MISSING_RECORDS, session2, staged2)
        assertEquals(0, mergeOutcome.records.newBooks)
        assertEquals(1, mergeOutcome.promotedBooks)
        assertEquals(0, mergeOutcome.installedFonts)
        assertEquals("READY", target.bookDao().findBook(restoredB.id)?.availability)
        assertEquals(restoredA, target.bookDao().findBook(restoredA.id))
        assertEquals(1, storage.promoteCallsByHash[hashA]) // Never overwritten once READY
        assertEquals(2, storage.promoteCallsByHash[hashB])
    }

    @Test
    fun crashCheckpointsAcrossPlanningCommitAndPromotionResumeIdempotently() = fixture { _, source, target, storage ->
        val bytes1 = "First resumable book payload\n".repeat(4).toByteArray()
        val bytes2 = "Second resumable book payload\n".repeat(4).toByteArray()
        val fontBytes = ByteArray(128) { (it * 3 and 0xff).toByte() }
        val hash1 = sha256(bytes1)
        val hash2 = sha256(bytes2)
        val fontHash = sha256(fontBytes)

        seedTxt(source, "b1", hash1, bytes1.size.toLong(), 10L)
        seedTxt(source, "b2", hash2, bytes2.size.toLong(), 20L)
        val snapshot = backupService(source).snapshot()
        val targetBackup = backupService(target)

        // Case 1: Crash right after JOURNAL_PLANNING (before DB commit) -> recover cleans up without modifying DB.
        val sPlan = UUID.randomUUID().toString()
        val assetsPlan = storage.stageAll(sPlan, listOf(Triple(BackupAssetKind.BOOK, "TXT", bytes1)))
        val crashPlanning = BackupAssetRestoreCoordinator(target, storage, targetBackup, { UUID.randomUUID().toString() }, { 300L }) { cp, _ ->
            if (cp == BackupCommitCheckpoint.JOURNAL_PLANNING) throw SimulatedCrash()
        }
        assertFailsWith<SimulatedCrash> {
            crashPlanning.commit(targetBackup.preview(snapshot), BackupRestoreMode.NEW_BOOKS_ONLY, sPlan, assetsPlan)
        }
        assertNotNull(storage.readJournal())
        val recoveredPlan = BackupAssetRestoreCoordinator(target, storage, targetBackup, { UUID.randomUUID().toString() }, { 301L }).recoverOrResume()
        assertFalse(recoveredPlan.resumed)
        assertEquals(0, recoveredPlan.promotedBooks)
        assertTrue(target.bookDao().listBooks().isEmpty())
        assertNull(storage.readJournal())
        assertTrue(storage.listStagingSessions().isEmpty())

        // Case 2: Crash right after DB_COMMITTED (while journal still says PLANNING) -> recover upgrades & finishes all assets.
        val sDb = UUID.randomUUID().toString()
        val assetsDb = storage.stageAll(
            sDb,
            listOf(
                Triple(BackupAssetKind.BOOK, "TXT", bytes1),
                Triple(BackupAssetKind.BOOK, "TXT", bytes2),
                Triple(BackupAssetKind.FONT, "ResumeFont.ttf", fontBytes),
            ),
        )
        val crashAfterDb = BackupAssetRestoreCoordinator(target, storage, targetBackup, { UUID.randomUUID().toString() }, { 400L }) { cp, _ ->
            if (cp == BackupCommitCheckpoint.DB_COMMITTED) throw SimulatedCrash()
        }
        assertFailsWith<SimulatedCrash> {
            crashAfterDb.commit(targetBackup.preview(snapshot), BackupRestoreMode.NEW_BOOKS_ONLY, sDb, assetsDb)
        }
        assertEquals(BackupJournalPhase.PLANNING, storage.readJournal()?.phase)
        assertEquals(setOf("MISSING"), target.bookDao().listBooks().map { it.availability }.toSet())

        // Crash again right after 1st book file is promoted (before DB availability update!) to verify double-crash idempotency.
        val crashMidPromote = BackupAssetRestoreCoordinator(target, storage, targetBackup, { UUID.randomUUID().toString() }, { 401L }) { cp, detail ->
            if (cp == BackupCommitCheckpoint.ASSET_PROMOTED && detail == "book:$hash1") throw SimulatedCrash()
        }
        assertFailsWith<SimulatedCrash> { crashMidPromote.recoverOrResume() }
        assertEquals(BackupJournalPhase.COMMITTING_ASSETS, storage.readJournal()?.phase)
        val book1Row = target.importDao().findDuplicate(hash1, bytes1.size.toLong())!!
        assertEquals("MISSING", book1Row.availability) // File already promoted, DB not yet flipped!
        assertEquals(hash1, storage.managedBookFingerprint(book1Row.managedRelativePath, 1024L)?.sha256)

        // Final recovery must detect already-promoted book1, flip it to READY without re-moving, promote book2 + font, and clean up.
        val finalSummary = BackupAssetRestoreCoordinator(target, storage, targetBackup, { UUID.randomUUID().toString() }, { 402L }).recoverOrResume()
        assertTrue(finalSummary.resumed)
        assertFalse(finalSummary.pendingRetry)
        assertEquals(2, finalSummary.promotedBooks)
        assertEquals(1, finalSummary.installedFonts)
        assertEquals("READY", target.importDao().findDuplicate(hash1, bytes1.size.toLong())?.availability)
        assertEquals("READY", target.importDao().findDuplicate(hash2, bytes2.size.toLong())?.availability)
        assertNotNull(storage.installedFonts[fontHash])
        assertNull(storage.readJournal())
        assertTrue(storage.listStagingSessions().isEmpty())
    }

    private class SimulatedCrash : Error("simulated process death")

    private class TempFsAssetStorage(private val root: Path) : BackupAssetStorage {
        private val managed = root.resolve("managed").also { Files.createDirectories(it) }
        private val staging = managed.resolve("backup-staging").also { Files.createDirectories(it) }
        private val journalFile = root.resolve("backup-journal/active.json")
        val installedFonts = linkedMapOf<String, ByteArray>()
        val promoteCallsByHash = mutableMapOf<String, Int>()

        fun stageAll(sessionId: String, items: List<Triple<BackupAssetKind, String, ByteArray>>): List<StagedBackupAsset> {
            val dir = staging.resolve(sessionId)
            Files.createDirectories(dir)
            return items.mapIndexed { index, (kind, meta, bytes) ->
                val name = "asset-%04d.bin".format(index)
                Files.write(dir.resolve(name), bytes)
                val hash = sha256(bytes)
                StagedBackupAsset(
                    kind = kind,
                    stagingName = name,
                    sha256 = hash,
                    byteSize = bytes.size.toLong(),
                    format = if (kind == BackupAssetKind.BOOK) meta else null,
                    label = if (kind == BackupAssetKind.FONT) meta else null,
                )
            }
        }

        override suspend fun readJournal(): BackupRestoreJournal? =
            if (Files.isRegularFile(journalFile)) BackupRules.json.decodeFromString(BackupRestoreJournal.serializer(), Files.readString(journalFile)) else null

        override suspend fun writeJournal(journal: BackupRestoreJournal) {
            Files.createDirectories(journalFile.parent)
            Files.writeString(journalFile, BackupRules.json.encodeToString(BackupRestoreJournal.serializer(), journal))
        }

        override suspend fun clearJournal() { Files.deleteIfExists(journalFile) }

        override suspend fun listStagingSessions(): List<String> =
            Files.list(staging).use { stream -> stream.filter(Files::isDirectory).map { it.fileName.toString() }.toList().sorted() }

        override suspend fun deleteStagingSession(sessionId: String) {
            val dir = staging.resolve(sessionId)
            if (Files.exists(dir)) {
                Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }

        override suspend fun managedBookFingerprint(relativePath: String, maxBytes: Long): FileFingerprint? {
            val file = managed.resolve(relativePath)
            if (!Files.isRegularFile(file)) return null
            val bytes = Files.readAllBytes(file)
            return FileFingerprint(sha256(bytes), bytes.size.toLong())
        }

        override suspend fun promoteBookAsset(sessionId: String, asset: StagedBackupAsset, finalRelativePath: String) {
            val source = staging.resolve(sessionId).resolve(asset.stagingName)
            val target = managed.resolve(finalRelativePath)
            Files.createDirectories(target.parent)
            check(!Files.exists(target))
            Files.move(source, target)
            promoteCallsByHash[asset.sha256] = (promoteCallsByHash[asset.sha256] ?: 0) + 1
        }

        override suspend fun installFontAsset(sessionId: String, asset: StagedBackupAsset): FontInstallOutcome {
            if (asset.sha256 in installedFonts) return FontInstallOutcome.ALREADY_PRESENT
            val bytes = Files.readAllBytes(staging.resolve(sessionId).resolve(asset.stagingName))
            installedFonts[asset.sha256] = bytes
            return FontInstallOutcome.INSTALLED
        }
    }

    private fun sampleBackupBook(hash: String, bytes: Long, format: BookFormat): BackupBook {
        val revision = if (format == BookFormat.TXT) txtContentRevision(hash, "utf-8", 1) else archiveContentRevision(format, hash)
        return BackupBook(
            sha256 = hash, byteSize = bytes, format = format.name, title = "Sample", displayName = "sample.txt",
            author = null, revision = revision, encoding = if (format == BookFormat.TXT) "utf-8" else null,
            normalizationVersion = if (format == BookFormat.TXT) 1 else null, addedAt = 1L, lastReadAt = null,
            details = null, preferences = null, shelves = emptyList(), progress = emptyList(),
            bookmarks = emptyList(), txtHighlights = emptyList(), epubHighlights = emptyList(), tocEdits = emptyList(),
        )
    }

    private suspend fun seedTxt(db: BookReaderDatabase, id: String, hash: String, bytes: Long, offset: Long): BookEntity {
        val revision = txtContentRevision(hash, "utf-8", 1)
        val entity = BookEntity(
            id = id, format = "TXT", title = "Book $id", originalDisplayName = "$id.txt",
            managedRelativePath = "books/$id/original", sourceSha256 = hash, sourceByteSize = bytes,
            addedAt = 1L, createdAt = 1L, updatedAt = 2L, currentRevision = revision,
            encodingId = "utf-8", normalizationVersion = 1, availability = "READY",
        )
        db.bookDao().insertBook(entity)
        val locator = TxtLocator(revision, TxtLocatorPayload(offset))
        db.progressDao().insertProgress(ReadingProgressEntity(id, revision, "txt", 1, LocatorCodec().encode(locator), 25.0, 1L, 1L, 5L))
        return entity
    }

    private fun backupService(db: BookReaderDatabase) = RoomReadingBackup(db, { UUID.randomUUID().toString() }, { 100L })

    private fun fixture(block: suspend (Path, BookReaderDatabase, BookReaderDatabase, TempFsAssetStorage) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("backup-asset-coord-")
        val source = createJvmDatabase(dir.resolve("source.db"))
        val target = createJvmDatabase(dir.resolve("target.db"))
        val storage = TempFsAssetStorage(dir.resolve("storage"))
        try {
            block(dir.resolve("storage"), source, target, storage)
        } finally {
            source.close()
            target.close()
            Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
