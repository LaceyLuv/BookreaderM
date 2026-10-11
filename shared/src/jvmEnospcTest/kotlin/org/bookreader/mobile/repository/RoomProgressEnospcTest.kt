package org.bookreader.mobile.repository

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.createJvmDatabase
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real filesystem ENOSPC with the production WAL writer; no page limit, trigger or mock. */
class RoomProgressEnospcTest {
    @Test fun walUpdatePreservesRecordsAndFilesThenSameEventRetries() = fixture { store ->
        val session = store.seed()
        val before = store.snapshot()
        store.exhaust()
        val pending = event(session, 2, 10)
        assertEquals(ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE), store.writer.save(pending))
        store.assertFull(DatabaseFailureStage.SAVE_COMMIT)
        assertEquals(before, store.snapshot())
        store.assertFiles()
        store.releaseAndReopen()
        assertEquals(before, store.snapshot())
        assertEquals(ProgressWriteResult.Committed, store.writer.save(pending))
        val after = store.snapshot()
        assertEquals(LocatorCodec().encode(locator(10)), assertNotNull(after.first).locatorJson)
        assertEquals(2, after.first.writerSequence)
        assertEquals(before.books, after.books)
        assertEquals(before.other, after.other)
        assertEquals(before.order, after.order)
        assertEquals(ProgressWriteResult.RejectedStale, store.writer.save(pending))
        store.reopen()
        assertEquals(after, store.snapshot())
        store.assertFiles()
        println("ENOSPC_PASS update rollback=true reopened=true identical_event_retry=true files_preserved=true")
    }

    @Test fun walFirstSaveRemainsMissingThenSameEventRetries() = fixture { store ->
        val session = store.seed(saveFirst = false)
        val before = store.snapshot()
        store.exhaust()
        val pending = event(session, 1, 40)
        assertEquals(ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE), store.writer.save(pending))
        store.assertFull(DatabaseFailureStage.SAVE_COMMIT)
        assertEquals(before, store.snapshot())
        assertNull(store.db.progressDao().findProgress("a", "r1"))
        assertEquals(ProgressReadResult.Missing, RoomProgressRepository(store.db).loadProgress("a", "r1"))
        store.assertFiles()
        store.releaseAndReopen()
        assertEquals(before, store.snapshot())
        assertEquals(ProgressWriteResult.Committed, store.writer.save(pending))
        val after = store.snapshot()
        assertEquals(LocatorCodec().encode(locator(40)), assertNotNull(after.first).locatorJson)
        assertEquals(1, after.first.writerSequence)
        assertEquals(before.books, after.books)
        assertEquals(before.other, after.other)
        assertEquals(before.order, after.order)
        store.reopen()
        assertEquals(after, store.snapshot())
        store.assertFiles()
        println("ENOSPC_PASS insert rollback=true reopened=true identical_event_retry=true files_preserved=true")
    }

    @Test fun walActivationPreservesEpochAndReadOrderThenRetries() = fixture { store ->
        store.seed()
        val before = store.snapshot()
        store.exhaust()
        assertEquals(ReadySessionResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE),
            store.writer.startReadySession("a", "r1", locator(80)))
        store.assertFull(DatabaseFailureStage.ACTIVATE)
        assertEquals(before, store.snapshot())
        store.assertFiles()
        store.releaseAndReopen()
        assertEquals(before, store.snapshot())
        val session = assertIs<ReadySessionResult.Ready>(store.writer.startReadySession("a", "r1", locator(80))).session
        val after = store.snapshot()
        assertEquals(before.books.first { it.id == "a" }.activeSessionEpoch + 1, session.sessionEpoch)
        assertEquals(assertNotNull(before.order) + 1, after.order)
        assertEquals(before.first, after.first)
        assertEquals(before.other, after.other)
        assertEquals(before.books.first { it.id == "b" }, after.books.first { it.id == "b" })
        store.reopen()
        assertEquals(after, store.snapshot())
        store.assertFiles()
        println("ENOSPC_PASS activation rollback=true reopened=true activation_retry=true files_preserved=true")
    }

    private data class Snapshot(val books: List<BookEntity>, val first: ReadingProgressEntity?,
        val other: ReadingProgressEntity?, val order: Long?)

    private class Store : AutoCloseable {
        private val root = Path.of(requireNotNull(System.getProperty("bookreader.enospc.root"))
            .also { require(it.isNotBlank()) { "Run scripts/enospc-check.sh; a private mounted fixture is mandatory" } })
        init {
            val fs = Files.getFileStore(root)
            check(fs.type() == "tmpfs" && fs.name() == "bookreader-enospc" && fs.totalSpace == 8_388_608L)
        }
        private val directory = Files.createTempDirectory(root, "case-")
        private val path = directory.resolve("db.sqlite")
        private val wal = directory.resolve("db.sqlite-wal")
        private val filler = directory.resolve("filler")
        private val fileHashes = mutableMapOf<Path, String>()
        var db = createJvmDatabase(path)
            private set
        private val failures = mutableListOf<Pair<DatabaseFailureStage, Exception>>()
        val writer get() = RoomProgressWriter(db, { stage, cause -> failures += stage to cause }) { 100 }

        suspend fun seed(saveFirst: Boolean = true): ProgressSession {
            for (id in listOf("a", "b")) {
                val file = directory.resolve("$id.txt")
                Files.writeString(file, "Generated ENOSPC fixture $id\n".repeat(100))
                val hash = hash(file)
                fileHashes[file] = hash
                db.bookDao().insertBook(BookEntity(id, "TXT", "Generated fixture", "$id.txt", file.toString(),
                    hash, Files.size(file), 1, 1, 1, currentRevision = "r1"))
                println("FIXTURE id=$id bytes=${Files.size(file)} sha256=$hash")
            }
            val b = assertIs<ReadySessionResult.Ready>(writer.startReadySession("b", "r1", null)).session
            assertEquals(ProgressWriteResult.Committed, writer.save(event(b, 1, 25)))
            val a = assertIs<ReadySessionResult.Ready>(writer.startReadySession("a", "r1", null)).session
            if (saveFirst) assertEquals(ProgressWriteResult.Committed, writer.save(event(a, 1, 80)))
            return a
        }

        suspend fun snapshot() = Snapshot(db.bookDao().listBooks(), db.progressDao().findProgress("a", "r1"),
            db.progressDao().findProgress("b", "r1"), db.progressWriterDao().readOrder())

        suspend fun exhaust() {
            db.useWriterConnection { connection ->
                connection.usePrepared("PRAGMA journal_mode") {
                    assertTrue(it.step()); assertEquals("wal", it.getText(0))
                }
                connection.usePrepared("PRAGMA max_page_count") {
                    assertTrue(it.step()); assertTrue(it.getLong(0) > 8_388_608) // No artificial SQLite quota.
                }
                // Remove reusable WAL allocation so a normal production commit must allocate OS blocks.
                connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") {
                    assertTrue(it.step()); assertEquals(listOf(0L, 0L, 0L), (0..2).map(it::getLong))
                }
            }
            assertEquals(0, Files.size(wal))
            val process = ProcessBuilder("python3", System.getProperty("bookreader.enospc.helper"), filler.toString())
                .redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(15, TimeUnit.SECONDS), "bounded filler timeout")
                val output = process.inputStream.bufferedReader().use { it.readText() }
                assertEquals(0, process.exitValue(), output)
                val proof = Json.parseToJsonElement(output).jsonObject
                assertEquals(28, proof.getValue("errno").jsonPrimitive.long)
                assertEquals(8_388_608, proof.getValue("capacity_bytes").jsonPrimitive.long)
                assertEquals(0, proof.getValue("available_bytes").jsonPrimitive.long)
                assertTrue(proof.getValue("written_bytes").jsonPrimitive.long in 1..8_388_608)
                println("OS_ENOSPC ${output.trim()}")
            } finally {
                if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
            }
            assertEquals(0, Files.getFileStore(root).usableSpace)
            println("WAL_BEFORE_FAILURE bytes=${Files.size(wal)} journal_mode=wal")
        }

        fun assertFull(stage: DatabaseFailureStage) {
            val (actualStage, cause) = failures.single()
            assertEquals(stage, actualStage)
            assertIs<SQLiteException>(cause)
            assertTrue(cause.message.orEmpty().contains("Error code: 13,"), cause.toString())
            assertEquals(0, Files.getFileStore(root).usableSpace)
            println("NATIVE_SQLITE_FULL code=13 stage=$actualStage wal_bytes=${Files.size(wal)} available_bytes=0")
        }

        fun assertFiles() { fileHashes.forEach { (file, expected) -> assertEquals(expected, hash(file)) } }
        fun releaseAndReopen() {
            Files.delete(filler)
            assertTrue(Files.getFileStore(root).usableSpace > 0)
            reopen()
        }
        fun reopen() { db.close(); db = createJvmDatabase(path) }
        override fun close() {
            // Release fixture space even on assertion failure, before Room closes/checkpoints.
            Files.deleteIfExists(filler)
            try { db.close() } finally {
                Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
            }
        }
    }

    private fun fixture(block: suspend (Store) -> Unit) = runBlocking { Store().use { block(it) } }
    private companion object {
        fun hash(path: Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
        fun locator(offset: Long) = TxtLocator("r1", TxtLocatorPayload(offset))
        fun event(session: ProgressSession, sequence: Long, offset: Long) = ProgressWriteEvent(session, sequence, locator(offset), null)
    }
}
