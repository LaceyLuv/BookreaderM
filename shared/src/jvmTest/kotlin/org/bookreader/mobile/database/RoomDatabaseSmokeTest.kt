package org.bookreader.mobile.database

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.ComicLocator
import org.bookreader.mobile.locator.ComicLocatorPayload
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.repository.LibraryErrorCode
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.repository.ProgressErrorCode
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.RoomBookRepository
import org.bookreader.mobile.repository.RoomProgressRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every test opens an actual Room database backed by the bundled SQLite driver. */
class RoomDatabaseSmokeTest {
    @Test
    fun createsInsertsQueriesAndReopensRealDatabase() = withDatabase { path, database ->
        assertEquals(LibraryState.Empty, RoomBookRepository(database).loadBooks())
        val book = fixtureBook()
        val progress = fixtureProgress()
        database.bookDao().insertBook(book)
        database.progressDao().insertProgress(progress)
        assertEquals(book, database.bookDao().findBook(book.id))
        assertIs<LibraryState.Content>(RoomBookRepository(database).loadBooks())
        assertIs<ProgressReadResult.Found>(RoomProgressRepository(database).loadProgress(book.id, "revision-1"))
        assertTrue(Files.isRegularFile(path))
        assertTrue(Files.size(path) > 0)
        database.close()
        val reopened = createJvmDatabase(path)
        try {
            assertEquals(book, reopened.bookDao().findBook(book.id))
            assertEquals(progress, reopened.progressDao().findProgress(book.id, "revision-1"))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun foreignKeyRejectsOrphanProgressAndCascadesExplicitDeletion() = withDatabase { _, database ->
        assertFailsWith<Exception> { database.progressDao().insertProgress(fixtureProgress()) }
        database.bookDao().insertBook(fixtureBook())
        database.progressDao().insertProgress(fixtureProgress())
        database.bookDao().deleteBook("book-1")
        assertNull(database.progressDao().findProgress("book-1", "revision-1"))
    }

    @Test
    fun duplicateBookInsertAbortsWithoutReplacingBookOrProgress() = withDatabase { _, database ->
        val book = fixtureBook()
        val progress = fixtureProgress()
        database.bookDao().insertBook(book)
        database.progressDao().insertProgress(progress)
        assertFailsWith<Exception> { database.bookDao().insertBook(book.copy(title = "replacement")) }
        assertFailsWith<Exception> { database.bookDao().insertBook(book.copy(id = "same-hash")) }
        assertEquals(book, database.bookDao().findBook(book.id))
        assertEquals(progress, database.progressDao().findProgress(book.id, "revision-1"))
    }

    @Test
    fun corruptProgressReadKeepsOriginalCommittedRecord() = withDatabase { _, database ->
        database.bookDao().insertBook(fixtureBook())
        val corrupt = fixtureProgress().copy(locatorJson = "{broken", percent = 78.5)
        database.progressDao().insertProgress(corrupt)
        assertEquals(
            ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD),
            RoomProgressRepository(database).loadProgress("book-1", "revision-1"),
        )
        assertEquals(corrupt, database.progressDao().findProgress("book-1", "revision-1"))
    }

    @Test
    fun unsupportedLocatorReadKeepsOriginalCommittedRecord() = withDatabase { _, database ->
        database.bookDao().insertBook(fixtureBook())
        val unsupported = fixtureProgress().copy(
            locatorVersion = 2,
            locatorJson = """{"type":"txt","version":2,"contentRevision":"revision-1","payload":{"futureOffset":500}}""",
        )
        database.progressDao().insertProgress(unsupported)
        assertEquals(
            ProgressReadResult.Error(ProgressErrorCode.UNSUPPORTED_LOCATOR),
            RoomProgressRepository(database).loadProgress("book-1", "revision-1"),
        )
        assertEquals(unsupported, database.progressDao().findProgress("book-1", "revision-1"))
    }

    @Test
    fun metadataMismatchNeverReturnsAUsablePosition() = withDatabase { _, database ->
        database.bookDao().insertBook(fixtureBook())
        val mismatched = fixtureProgress().copy(contentRevision = "revision-2")
        database.progressDao().insertProgress(mismatched)
        assertEquals(
            ProgressReadResult.Error(ProgressErrorCode.REVISION_MISMATCH),
            RoomProgressRepository(database).loadProgress("book-1", "revision-2"),
        )
        val typeMismatch = fixtureProgress().copy(contentRevision = "revision-3", locatorType = "epub", locatorJson = LocatorCodec().encode(TxtLocator("revision-3", TxtLocatorPayload(100))))
        database.progressDao().insertProgress(typeMismatch)
        assertEquals(
            ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD),
            RoomProgressRepository(database).loadProgress("book-1", "revision-3"),
        )
        assertEquals(typeMismatch, database.progressDao().findProgress("book-1", "revision-3"))
        assertEquals(mismatched, database.progressDao().findProgress("book-1", "revision-2"))
        val versionMismatch = fixtureProgress().copy(locatorVersion = 9)
        database.progressDao().insertProgress(versionMismatch)
        assertEquals(
            ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD),
            RoomProgressRepository(database).loadProgress("book-1", "revision-1"),
        )
        assertEquals(versionMismatch, database.progressDao().findProgress("book-1", "revision-1"))
    }

    @Test
    fun locatorFormatMustMatchItsBook() = withDatabase { _, database ->
        database.bookDao().insertBook(fixtureBook())
        val comicOnTxt = fixtureProgress().copy(
            locatorType = "comic",
            locatorJson = LocatorCodec().encode(ComicLocator("revision-1", ComicLocatorPayload("1.png"))),
        )
        database.progressDao().insertProgress(comicOnTxt)
        assertEquals(
            ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD),
            RoomProgressRepository(database).loadProgress("book-1", "revision-1"),
        )
        assertEquals(comicOnTxt, database.progressDao().findProgress("book-1", "revision-1"))
    }

    @Test
    fun unavailableDatabaseReadIsErrorAndPreservesDiskRecord() = withDatabase { path, database ->
        database.bookDao().insertBook(fixtureBook())
        val progress = fixtureProgress()
        database.progressDao().insertProgress(progress)
        database.close()
        assertEquals(
            LibraryState.Error(LibraryErrorCode.DATABASE_UNAVAILABLE),
            RoomBookRepository(database).loadBooks(),
        )
        assertEquals(
            ProgressReadResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE),
            RoomProgressRepository(database).loadProgress("book-1", "revision-1"),
        )
        val reopened = createJvmDatabase(path)
        try { assertEquals(progress, reopened.progressDao().findProgress("book-1", "revision-1")) }
        finally { reopened.close() }
    }

    @Test
    fun callerCancellationIsNotConvertedToDatabaseError() = withDatabase { _, database ->
        database.bookDao().insertBook(fixtureBook())
        val progress = fixtureProgress()
        database.progressDao().insertProgress(progress)
        // Cancel after the child has started so the repository call is actually entered.
        suspend fun assertCancellationPropagates(read: suspend () -> Any) = coroutineScope {
            var returnedNormally = false
            var cancellationPropagated = false
            val caller = launch(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().cancel()
                try {
                    read()
                    returnedNormally = true
                } catch (_: CancellationException) {
                    cancellationPropagated = true
                }
            }
            caller.join()
            assertTrue(cancellationPropagated)
            assertTrue(!returnedNormally)
            currentCoroutineContext().ensureActive()
        }
        assertCancellationPropagates { RoomBookRepository(database).loadBooks() }
        assertCancellationPropagates { RoomProgressRepository(database).loadProgress("book-1", "revision-1") }
        assertEquals(progress, database.progressDao().findProgress("book-1", "revision-1"))
    }

    @Test
    fun revisionRecordsAndUnknownPercentRemainDistinctFromMissing() = withDatabase { _, database ->
        database.bookDao().insertBook(fixtureBook())
        val first = fixtureProgress()
        val second = fixtureProgress().copy(
            contentRevision = "revision-2", percent = null,
            locatorJson = LocatorCodec().encode(TxtLocator("revision-2", TxtLocatorPayload(0))),
        )
        database.progressDao().insertProgress(first)
        database.progressDao().insertProgress(second)
        val repository = RoomProgressRepository(database)
        assertNull(assertIs<ProgressReadResult.Found>(repository.loadProgress("book-1", "revision-2")).progress.percent)
        assertEquals(ProgressReadResult.Missing, repository.loadProgress("book-1", "revision-3"))
        assertEquals(first, database.progressDao().findProgress("book-1", "revision-1"))
    }

    @Test
    fun newestLibraryOrderUsesStableIdWhenAdditionTimesMatch() = withDatabase { _, database ->
        val older = fixtureBook().copy(id = "older", addedAt = 1, sourceSha256 = "b".repeat(64))
        val sameTimeA = fixtureBook().copy(id = "a", addedAt = 3, sourceSha256 = "c".repeat(64))
        val sameTimeB = fixtureBook().copy(id = "b", addedAt = 3, sourceSha256 = "d".repeat(64))
        database.bookDao().insertBook(older)
        database.bookDao().insertBook(sameTimeB)
        database.bookDao().insertBook(sameTimeA)
        val state = assertIs<LibraryState.Content>(RoomBookRepository(database).loadBooks())
        assertEquals(listOf("a", "b", "older"), state.books.map { it.id })
    }

    @Test
    fun invalidStoredBookIsErrorRatherThanEmptyLibrary() = withDatabase { _, database ->
        val invalid = fixtureBook().copy(managedRelativePath = "content://untrusted/1")
        database.bookDao().insertBook(invalid)
        assertEquals(LibraryState.Error(LibraryErrorCode.INVALID_RECORD), RoomBookRepository(database).loadBooks())
        assertEquals(invalid, database.bookDao().findBook("book-1"))
    }

    private fun withDatabase(block: suspend (Path, BookReaderDatabase) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("bookreader-room-test-")
        val path = directory.resolve("bookreader.db")
        val database = createJvmDatabase(path)
        try { block(path, database) }
        finally {
            database.close()
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun fixtureBook() = BookEntity(
        id = "book-1", format = "TXT", title = "Self-authored smoke fixture",
        originalDisplayName = "fixture.txt", managedRelativePath = "books/book-1/original",
        sourceSha256 = "a".repeat(64), sourceByteSize = 123, currentRevision = "revision-1",
        addedAt = 1, createdAt = 1, updatedAt = 1,
    )

    private fun fixtureProgress() = ReadingProgressEntity(
        bookId = "book-1", contentRevision = "revision-1", locatorType = "txt", locatorVersion = 1,
        locatorJson = LocatorCodec().encode(TxtLocator("revision-1", TxtLocatorPayload(42))),
        percent = 33.0, writerSessionEpoch = 1, writerSequence = 2, updatedAt = 3,
    )
}
