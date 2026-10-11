package org.bookreader.mobile.repository

import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
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

/** Native SQLITE_FULL from a bounded file DB, not a thrown/mock repository error or OS ENOSPC. */
class RoomProgressFullTest {
    @Test fun fullUpdatePreservesLastCommitAcrossReopenAndSameEventCanRetry() = fixture { store ->
        val session = store.seed()
        val before = store.snapshot()
        store.exhaustOn("AFTER UPDATE ON reading_progress")
        val pending = event(session, 2, 10)
        assertEquals(ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE), store.writer.save(pending))
        store.assertNativeFull(DatabaseFailureStage.SAVE_COMMIT)
        assertEquals(before, store.snapshot())
        assertEquals(0, store.payloadRows())
        store.reopen()
        assertEquals(before, store.snapshot())
        store.releaseCapacity()
        assertEquals(ProgressWriteResult.Committed, store.writer.save(pending))
        val latest = assertNotNull(store.db.progressDao().findProgress("a", "r1"))
        assertEquals(LocatorCodec().encode(locator(10)), latest.locatorJson)
        assertEquals(2, latest.writerSequence)
        assertEquals(1, store.payloadRows()) // Same trigger really allocates after capacity is restored.
        assertEquals(ProgressWriteResult.RejectedStale, store.writer.save(pending))
        assertEquals(before.books, store.snapshot().books)
        assertEquals(before.other, store.snapshot().other)
        assertEquals(before.order, store.snapshot().order)
        store.reopen()
        assertEquals(latest, store.db.progressDao().findProgress("a", "r1"))
    }

    @Test fun fullFirstSaveDoesNotInventProgressAndCanRetryAfterCapacityReturns() = fixture { store ->
        val session = store.seed(saveFirst = false)
        val before = store.snapshot()
        store.exhaustOn("AFTER INSERT ON reading_progress WHEN NEW.bookId = 'a'")
        val pending = event(session, 1, 40)
        assertEquals(ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE), store.writer.save(pending))
        store.assertNativeFull(DatabaseFailureStage.SAVE_COMMIT)
        assertEquals(before, store.snapshot())
        assertNull(store.db.progressDao().findProgress("a", "r1"))
        assertEquals(ProgressReadResult.Missing, RoomProgressRepository(store.db).loadProgress("a", "r1"))
        store.reopen()
        assertEquals(before, store.snapshot())
        store.releaseCapacity()
        assertEquals(ProgressWriteResult.Committed, store.writer.save(pending))
        val after = store.snapshot()
        assertEquals(before.books, after.books)
        assertEquals(before.other, after.other)
        assertEquals(before.order, after.order)
        assertEquals(LocatorCodec().encode(locator(40)), assertNotNull(after.first).locatorJson)
        assertEquals(1, store.payloadRows())
    }

    @Test fun fullActivationRollsBackEpochAndReadOrderThenRestoresExistingRecord() = fixture { store ->
        store.seed()
        val before = store.snapshot()
        // This runs after activate() changed the book AND inserted the new read-order counter.
        store.exhaustOn("AFTER INSERT ON app_metadata WHEN NEW.`key` = 'read_order'")
        assertEquals(ReadySessionResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE),
            store.writer.startReadySession("a", "r1", locator(80)))
        store.assertNativeFull(DatabaseFailureStage.ACTIVATE)
        assertEquals(before, store.snapshot())
        assertEquals(0, store.payloadRows())
        store.reopen()
        assertEquals(before, store.snapshot())
        store.releaseCapacity()
        val session = assertIs<ReadySessionResult.Ready>(store.writer.startReadySession("a", "r1", locator(80))).session
        assertEquals(before.books.first { it.id == "a" }.activeSessionEpoch + 1, session.sessionEpoch)
        assertEquals(assertNotNull(before.order) + 1, store.db.progressWriterDao().readOrder())
        assertEquals(before.first, store.db.progressDao().findProgress("a", "r1"))
        assertEquals(before.other, store.db.progressDao().findProgress("b", "r1"))
        assertEquals(before.books.first { it.id == "b" }, store.db.bookDao().findBook("b"))
        assertEquals(1, store.payloadRows())
        val after = store.snapshot()
        store.reopen()
        assertEquals(after, store.snapshot())
    }

    private data class Snapshot(val books: List<BookEntity>, val first: ReadingProgressEntity?,
        val other: ReadingProgressEntity?, val order: Long?)

    private class Store : AutoCloseable {
        private val directory = Files.createTempDirectory("bookreader-full-")
        private val path = directory.resolve("db.sqlite")
        var db = createJvmDatabase(path)
            private set
        private val failures = mutableListOf<Pair<DatabaseFailureStage, Exception>>()
        private var originalLimit = 0L
        val writer get() = RoomProgressWriter(db, { stage, cause -> failures += stage to cause }) { 100 }

        suspend fun seed(saveFirst: Boolean = true): ProgressSession {
            for (id in listOf("a", "b")) db.bookDao().insertBook(BookEntity(id, "TXT", "Generated fixture",
                "$id.txt", "books/$id/original", id.repeat(64), 1000, 1, 1, 1, currentRevision = "r1"))
            val b = assertIs<ReadySessionResult.Ready>(writer.startReadySession("b", "r1", null)).session
            assertEquals(ProgressWriteResult.Committed, writer.save(event(b, 1, 25)))
            val a = assertIs<ReadySessionResult.Ready>(writer.startReadySession("a", "r1", null)).session
            if (saveFirst) assertEquals(ProgressWriteResult.Committed, writer.save(event(a, 1, 80)))
            return a
        }
        suspend fun snapshot() = Snapshot(db.bookDao().listBooks(), db.progressDao().findProgress("a", "r1"),
            db.progressDao().findProgress("b", "r1"), db.progressWriterDao().readOrder())
        suspend fun scalar(sql: String): Long = db.useWriterConnection { connection ->
            connection.usePrepared(sql) { check(it.step()); it.getLong(0) }
        }
        suspend fun exhaustOn(trigger: String) {
            originalLimit = scalar("PRAGMA max_page_count")
            db.useWriterConnection { it.execSQL("CREATE TABLE full_test_payload (payload BLOB NOT NULL)") }
            val bytes = (scalar("PRAGMA page_count") + 4) * scalar("PRAGMA page_size")
            check(bytes in 1..1_048_576) // Bounded fixture allocation even if the schema grows unexpectedly.
            db.useWriterConnection { it.execSQL("CREATE TRIGGER full_test_write $trigger BEGIN " +
                "INSERT INTO full_test_payload VALUES (zeroblob($bytes)); END") }
            val pages = scalar("PRAGMA page_count")
            assertEquals(pages, scalar("PRAGMA max_page_count = $pages"))
            assertTrue(originalLimit > pages)
            println("FULL_FIXTURE page_count=$pages page_size=${scalar("PRAGMA page_size")} growth_bytes=$bytes")
        }
        fun assertNativeFull(stage: DatabaseFailureStage) {
            val (actualStage, failure) = failures.single()
            assertEquals(stage, actualStage)
            assertIs<SQLiteException>(failure)
            assertTrue(failure.message.orEmpty().contains("Error code: 13,"), failure.toString())
            println("NATIVE_SQLITE_FULL code=13 stage=$actualStage")
        }
        suspend fun payloadRows() = scalar("SELECT count(*) FROM full_test_payload").toInt()
        suspend fun releaseCapacity() { assertEquals(originalLimit, scalar("PRAGMA max_page_count = $originalLimit")) }
        fun reopen() { db.close(); db = createJvmDatabase(path) }
        override fun close() {
            db.close()
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
    private fun fixture(block: suspend (Store) -> Unit) = runBlocking { Store().use { block(it) } }
    private companion object {
        fun locator(offset: Long) = TxtLocator("r1", TxtLocatorPayload(offset))
        fun event(session: ProgressSession, sequence: Long, offset: Long) = ProgressWriteEvent(session, sequence, locator(offset), null)
    }
}
