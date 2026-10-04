package org.bookreader.mobile.repository

import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.createJvmDatabase
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RoomProgressWriterTest {
    @Test fun existingProgressCannotStartWithNullOrDifferentRestoredLocator() = fixture { db, writer ->
        val book = book()
        db.bookDao().insertBook(book)
        val progress = progress()
        db.progressDao().insertProgress(progress)
        assertIs<ReadySessionResult.Error>(writer.startReadySession(book.id, "r1", null))
        assertIs<ReadySessionResult.Error>(writer.startReadySession(book.id, "r1", locator(1)))
        assertEquals(book, db.bookDao().findBook(book.id))
        assertEquals(progress, db.progressDao().findProgress(book.id, "r1"))
        assertNull(db.progressWriterDao().readOrder())
    }

    @Test fun missingProgressCannotAcceptInventedRestoreAndReadyOnlyMarksActualRead() = fixture { db, writer ->
        val original = book()
        db.bookDao().insertBook(original)
        assertIs<ReadySessionResult.Error>(writer.startReadySession(original.id, "r1", locator(3)))
        assertEquals(original, db.bookDao().findBook(original.id))
        val ready = assertIs<ReadySessionResult.Ready>(writer.startReadySession(original.id, "r1", null)).session
        val marked = db.bookDao().findBook(original.id)!!
        assertEquals(1L, ready.sessionEpoch)
        assertEquals(100L, marked.lastReadAt)
        assertEquals(1L, marked.readOrder)
        assertNull(db.progressDao().findProgress(original.id, "r1"))
    }

    @Test fun corruptAndUnsupportedRecordsDoNotChangeEpochOrReadOrder() = fixture { db, writer ->
        db.bookDao().insertBook(book())
        val corrupt = progress().copy(locatorJson = "{bad")
        db.progressDao().insertProgress(corrupt)
        assertEquals(ReadySessionResult.Error(ProgressErrorCode.INVALID_RECORD), writer.startReadySession("a", "r1", null))
        assertEquals(book(), db.bookDao().findBook("a"))
        assertEquals(corrupt, db.progressDao().findProgress("a", "r1"))
        val unsupported = progress().copy(locatorVersion = 2, locatorJson = """{"type":"txt","version":2,"contentRevision":"r1","payload":{}}""")
        db.progressWriterDao().updateProgress(unsupported)
        assertEquals(ReadySessionResult.Error(ProgressErrorCode.UNSUPPORTED_LOCATOR), writer.startReadySession("a", "r1", null))
        assertEquals(unsupported, db.progressDao().findProgress("a", "r1"))
        assertNull(db.progressWriterDao().readOrder())
    }

    @Test fun saveUsesSequenceNotMaximumOffsetAndRejectsOldSession() = fixture { db, writer ->
        db.bookDao().insertBook(book())
        val first = assertIs<ReadySessionResult.Ready>(writer.startReadySession("a", "r1", null)).session
        assertEquals(ProgressWriteResult.Committed, writer.save(event(first, 1, 90)))
        assertEquals(ProgressWriteResult.Committed, writer.save(event(first, 3, 10)))
        assertEquals(ProgressWriteResult.RejectedStale, writer.save(event(first, 2, 100)))
        assertEquals(10L, assertIs<ProgressReadResult.Found>(RoomProgressRepository(db).loadProgress("a", "r1")).progress.let { (it.locator as TxtLocator).payload.utf16Offset })
        val next = assertIs<ReadySessionResult.Ready>(writer.startReadySession("a", "r1", locator(10))).session
        assertEquals(ProgressWriteResult.RejectedStale, writer.save(event(first, 4, 110)))
        assertEquals(ProgressWriteResult.Committed, writer.save(event(next, 1, 4)))
        assertEquals(4L, (assertIs<ProgressReadResult.Found>(RoomProgressRepository(db).loadProgress("a", "r1")).progress.locator as TxtLocator).payload.utf16Offset)
    }

    @Test fun changedRestoreRevisionOrDeletingBookRejectsWithoutMutation() = fixture { db, writer ->
        db.bookDao().insertBook(book())
        val original = progress()
        db.progressDao().insertProgress(original)
        val changed = original.copy(locatorJson = LocatorCodec().encode(locator(30)))
        db.progressWriterDao().updateProgress(changed)
        assertIs<ReadySessionResult.Error>(writer.startReadySession("a", "r1", locator(20)))
        assertEquals(changed, db.progressDao().findProgress("a", "r1"))
        assertEquals(book(), db.bookDao().findBook("a"))
        assertIs<ReadySessionResult.Error>(writer.startReadySession("a", "old-revision", null))
        db.importDao().beginDelete("a", 100)
        val deleting = db.bookDao().findBook("a")!!
        assertIs<ReadySessionResult.Error>(writer.startReadySession("a", "r1", locator(30)))
        assertEquals(ProgressWriteResult.RejectedStale, writer.save(event(ProgressSession("a", "r1", 1), 1, 1)))
        assertEquals(deleting, db.bookDao().findBook("a"))
        assertEquals(changed, db.progressDao().findProgress("a", "r1"))
    }

    @Test fun fixedBookEventsStaySeparateAndDeletedBookIsNeverRecreated() = fixture { db, writer ->
        db.bookDao().insertBook(book())
        db.bookDao().insertBook(book().copy(id = "b", sourceSha256 = "b".repeat(64), managedRelativePath = "books/b/original"))
        val a = assertIs<ReadySessionResult.Ready>(writer.startReadySession("a", "r1", null)).session
        val b = assertIs<ReadySessionResult.Ready>(writer.startReadySession("b", "r1", null)).session
        writer.save(event(b, 1, 2))
        writer.save(event(a, 1, 42))
        assertEquals(42L, (assertIs<ProgressReadResult.Found>(RoomProgressRepository(db).loadProgress("a", "r1")).progress.locator as TxtLocator).payload.utf16Offset)
        assertEquals(2L, (assertIs<ProgressReadResult.Found>(RoomProgressRepository(db).loadProgress("b", "r1")).progress.locator as TxtLocator).payload.utf16Offset)
        db.bookDao().deleteBook("a")
        assertEquals(ProgressWriteResult.RejectedStale, writer.save(event(a, 2, 70)))
        assertNull(db.bookDao().findBook("a"))
        assertNull(db.progressDao().findProgress("a", "r1"))
    }

    @Test fun verifiedAvailabilityRestoreCannotReviveDeletingOrChangedRevisionAndPreservesProgress() = fixture { db, writer ->
        val original = book()
        db.bookDao().insertBook(original)
        val session = assertIs<ReadySessionResult.Ready>(writer.startReadySession("a", "r1", null)).session
        writer.save(event(session, 1, 20))
        val committed = db.progressDao().findProgress("a", "r1")!!
        val read = db.bookDao().findBook("a")!!
        assertEquals(0, db.bookDao().markAvailability("a", "different", "MISSING", 200))
        assertEquals(1, db.bookDao().markAvailability("a", "r1", "MISSING", 200))
        val missing = db.bookDao().findBook("a")!!
        assertEquals(read.lastReadAt, missing.lastReadAt)
        assertEquals(read.readOrder, missing.readOrder)
        assertEquals(0, db.bookDao().restoreVerifiedAvailability("a", "r1", "b".repeat(64), 100, 300))
        assertEquals(0, db.bookDao().restoreVerifiedAvailability("a", "other-revision", original.sourceSha256, 100, 300))
        assertEquals(1, db.bookDao().restoreVerifiedAvailability("a", "r1", original.sourceSha256, 100, 300))
        assertEquals(committed, db.progressDao().findProgress("a", "r1"))
        assertEquals(ProgressWriteResult.RejectedStale, writer.save(event(session, 2, 40)))
        val resumed = assertIs<ReadySessionResult.Ready>(writer.startReadySession("a", "r1", locator(20))).session
        val readyBook = db.bookDao().findBook("a")!!
        assertEquals(0, db.bookDao().markAvailability("a", "r1", "MISSING", 350, session.sessionEpoch))
        assertEquals(readyBook, db.bookDao().findBook("a"))
        assertEquals(ProgressWriteResult.Committed, writer.save(event(resumed, 1, 15)))
        val latest = db.progressDao().findProgress("a", "r1")!!
        db.importDao().beginDelete("a", 400)
        assertEquals(0, db.bookDao().restoreVerifiedAvailability("a", "r1", original.sourceSha256, 100, 500))
        assertEquals("DELETING", db.bookDao().findBook("a")!!.availability)
        assertEquals(latest, db.progressDao().findProgress("a", "r1"))
    }

    private fun locator(offset: Long) = TxtLocator("r1", TxtLocatorPayload(offset))
    private fun event(session: ProgressSession, sequence: Long, offset: Long) = ProgressWriteEvent(session, sequence, locator(offset), null)
    private fun book() = BookEntity("a", "TXT", "Fixture", "fixture.txt", "books/a/original", "a".repeat(64), 100, 1, 1, 1, currentRevision = "r1")
    private fun progress() = ReadingProgressEntity("a", "r1", "txt", 1, LocatorCodec().encode(locator(20)), 20.0, 0, 1, 2)
    private fun fixture(block: suspend (BookReaderDatabase, RoomProgressWriter) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("bookreader-writer-")
        val db = createJvmDatabase(directory.resolve("db.sqlite"))
        try { block(db, RoomProgressWriter(db) { 100 }) }
        finally {
            db.close()
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
