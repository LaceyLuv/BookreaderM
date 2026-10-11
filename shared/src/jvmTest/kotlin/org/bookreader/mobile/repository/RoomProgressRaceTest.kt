package org.bookreader.mobile.repository

import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.createJvmDatabase
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.LocatorDecodeResult
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Real file-backed Room; barriers delay event delivery, not a mocked SQL commit. */
class RoomProgressRaceTest {
    @Test fun delayedBookASaveCannotChangeNewlyActiveBookB() = fixture { store ->
        store.db.bookDao().insertBook(book("a"))
        store.db.bookDao().insertBook(book("b"))
        val a = store.start("a")
        val pending = delayedSave(store.writer, event(a, 1, 80))
        val b = store.start("b")
        assertEquals(ProgressWriteResult.Committed, store.writer.save(event(b, 1, 15)))
        val bRecord = store.record("b")
        val bBook = store.db.bookDao().findBook("b")
        assertFalse(pending.result.isCompleted)
        assertEquals(ProgressWriteResult.Committed, pending.deliver())
        val aRecord = store.record("a")
        assertEquals(bRecord, store.record("b"))
        assertEquals(bBook, store.db.bookDao().findBook("b"))
        store.reopen()
        assertEquals(aRecord, store.record("a"))
        assertEquals(bRecord, store.record("b"))
        assertAnchor(aRecord, a, 1, 80)
        assertAnchor(bRecord, b, 1, 15)
    }

    @Test fun rapidSameBookSessionsRejectDelayedOldEventsAcrossDatabaseReopens() = fixture { store ->
        store.db.bookDao().insertBook(book("a"))
        var session = store.start("a")
        assertEquals(ProgressWriteResult.Committed, store.writer.save(event(session, 1, 100)))
        repeat(12) { iteration ->
            val committed = store.record("a")
            val pending = delayedSave(store.writer, event(session, 2, 900))
            val next = store.start("a", locator(offset(committed)))
            assertEquals(session.sessionEpoch + 1, next.sessionEpoch)
            val backwards = 90L - iteration
            assertEquals(ProgressWriteResult.Committed, store.writer.save(event(next, 1, backwards)))
            val latest = store.record("a")
            val latestBook = store.db.bookDao().findBook("a")
            assertEquals(ProgressWriteResult.RejectedStale, pending.deliver())
            assertEquals(latest, store.record("a"))
            assertEquals(latestBook, store.db.bookDao().findBook("a"))
            store.reopen()
            assertEquals(latest, store.record("a"))
            assertEquals(latestBook, store.db.bookDao().findBook("a"))
            assertAnchor(store.record("a"), next, 1, backwards)
            session = next
        }
    }

    @Test fun delayedForwardEventCannotOverwriteNewerBackwardSequence() = fixture { store ->
        store.db.bookDao().insertBook(book("a"))
        val session = store.start("a")
        assertEquals(ProgressWriteResult.Committed, store.writer.save(event(session, 1, 30)))
        val pending = delayedSave(store.writer, event(session, 2, 95))
        assertEquals(ProgressWriteResult.Committed, store.writer.save(event(session, 3, 10)))
        val latest = store.record("a")
        assertEquals(ProgressWriteResult.RejectedStale, pending.deliver())
        assertEquals(latest, store.record("a"))
        store.reopen()
        assertEquals(latest, store.record("a"))
        assertAnchor(store.record("a"), session, 3, 10)
    }

    @Test fun concurrentDuplicateSequenceCommitsExactlyOnce() = fixture { store ->
        store.db.bookDao().insertBook(book("a"))
        val session = store.start("a")
        val gate = CompletableDeferred<Unit>()
        // Both real save calls race after the same barrier. Either locator may win.
        val first = async { gate.await(); store.writer.save(event(session, 1, 20)) }
        val second = async { gate.await(); store.writer.save(event(session, 1, 80)) }
        gate.complete(Unit)
        val results = listOf(first.await(), second.await())
        assertEquals(1, results.count { it == ProgressWriteResult.Committed })
        assertEquals(1, results.count { it == ProgressWriteResult.RejectedStale })
        val winner = if (results.first() == ProgressWriteResult.Committed) 20L else 80L
        val durable = store.record("a")
        store.reopen()
        assertEquals(durable, store.record("a"))
        assertAnchor(store.record("a"), session, 1, winner)
    }

    @Test fun delayedSavesDuringAndAfterDeletionNeverRecreateBookOrTouchOtherBook() = fixture { store ->
        store.db.bookDao().insertBook(book("a"))
        store.db.bookDao().insertBook(book("b"))
        val a = store.start("a")
        val b = store.start("b")
        assertEquals(ProgressWriteResult.Committed, store.writer.save(event(a, 1, 25)))
        assertEquals(ProgressWriteResult.Committed, store.writer.save(event(b, 1, 60)))
        val aRecord = store.record("a")
        val bRecord = store.record("b")
        val bBook = store.db.bookDao().findBook("b")
        val during = delayedSave(store.writer, event(a, 2, 70))
        val after = delayedSave(store.writer, event(a, 3, 90))
        store.db.importDao().beginDelete("a", 200)
        val deleting = store.db.bookDao().findBook("a")
        assertEquals("DELETING", deleting?.availability)
        assertEquals(ProgressWriteResult.RejectedStale, during.deliver())
        assertEquals(deleting, store.db.bookDao().findBook("a"))
        assertEquals(aRecord, store.record("a"))
        store.db.bookDao().deleteBook("a")
        assertEquals(ProgressWriteResult.RejectedStale, after.deliver())
        store.reopen()
        assertNull(store.db.bookDao().findBook("a"))
        assertNull(store.db.progressDao().findProgress("a", "r1"))
        assertEquals(bRecord, store.record("b"))
        assertEquals(bBook, store.db.bookDao().findBook("b"))
    }

    private class PendingSave(val result: Deferred<ProgressWriteResult>, private val release: CompletableDeferred<Unit>) {
        suspend fun deliver(): ProgressWriteResult { release.complete(Unit); return result.await() }
    }

    private suspend fun CoroutineScope.delayedSave(writer: RoomProgressWriter, event: ProgressWriteEvent): PendingSave {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val result = async {
            entered.complete(Unit)
            release.await()
            writer.save(event)
        }
        entered.await()
        return PendingSave(result, release)
    }

    private class Store : AutoCloseable {
        private val directory = Files.createTempDirectory("bookreader-race-")
        private val path = directory.resolve("db.sqlite")
        var db = createJvmDatabase(path)
            private set
        val writer get() = RoomProgressWriter(db) { 100 }
        suspend fun start(id: String, restored: TxtLocator? = null) =
            assertIs<ReadySessionResult.Ready>(writer.startReadySession(id, "r1", restored)).session
        suspend fun record(id: String) = assertNotNull(db.progressDao().findProgress(id, "r1"))
        fun reopen() { db.close(); db = createJvmDatabase(path) }
        override fun close() {
            db.close()
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun fixture(block: suspend CoroutineScope.(Store) -> Unit) = runBlocking {
        Store().use { store ->
            // Structured children are joined/cancelled before closing the caller-owned database.
            withTimeout(15_000) { block(store) }
        }
    }

    private fun book(id: String) = BookEntity(id, "TXT", "Fixture", "fixture.txt", "books/$id/original",
        id.repeat(64), 1_000, 1, 1, 1, currentRevision = "r1")
    private fun locator(offset: Long) = TxtLocator("r1", TxtLocatorPayload(offset))
    private fun event(session: ProgressSession, sequence: Long, offset: Long) =
        ProgressWriteEvent(session, sequence, locator(offset), null)
    private fun offset(record: ReadingProgressEntity) =
        (assertIs<LocatorDecodeResult.Success>(LocatorCodec().decode(record.locatorJson)).locator as TxtLocator)
            .payload.utf16Offset
    private fun assertAnchor(record: ReadingProgressEntity, session: ProgressSession, sequence: Long, offset: Long) {
        assertEquals(session.bookId, record.bookId)
        assertEquals(session.contentRevision, record.contentRevision)
        assertEquals(session.sessionEpoch, record.writerSessionEpoch)
        assertEquals(sequence, record.writerSequence)
        assertEquals(offset, offset(record))
    }
}
