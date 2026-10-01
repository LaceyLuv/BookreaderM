package org.bookreader.mobile.repository

import kotlinx.coroutines.CancellationException
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.locator.ComicLocator
import org.bookreader.mobile.locator.ContentLocator
import org.bookreader.mobile.locator.EpubLocator
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.LocatorDecodeResult
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.model.ReadingProgress

class RoomBookRepository(private val database: BookReaderDatabase) : BookRepository {
    override suspend fun loadBooks(): LibraryState {
        val records = try {
            database.bookDao().listBooks()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return LibraryState.Error(LibraryErrorCode.DATABASE_UNAVAILABLE)
        }
        return try {
            val books = records.map { it.toBook() }
            if (books.isEmpty()) LibraryState.Empty else LibraryState.Content(books)
        } catch (_: IllegalArgumentException) {
            LibraryState.Error(LibraryErrorCode.INVALID_RECORD)
        }
    }
}

class RoomProgressRepository(
    private val database: BookReaderDatabase,
    private val codec: LocatorCodec = LocatorCodec(),
) : ProgressRepository {
    override suspend fun loadProgress(bookId: String, contentRevision: String): ProgressReadResult {
        val record = try {
            database.progressDao().findProgress(bookId, contentRevision)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ProgressReadResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE)
        } ?: return ProgressReadResult.Missing

        val bookFormat = try {
            database.bookDao().findBook(bookId)?.format
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ProgressReadResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE)
        }

        return when (val decoded = codec.decode(record.locatorJson)) {
            is LocatorDecodeResult.Malformed -> ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD)
            is LocatorDecodeResult.Unsupported -> ProgressReadResult.Error(ProgressErrorCode.UNSUPPORTED_LOCATOR)
            is LocatorDecodeResult.Success -> {
                val locator = decoded.locator
                when {
                    locator.contentRevision != record.contentRevision ->
                        ProgressReadResult.Error(ProgressErrorCode.REVISION_MISMATCH)
                    locator.version != record.locatorVersion || locator.typeName() != record.locatorType ->
                        ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD)
                    bookFormat != locator.typeName().uppercase() ->
                        ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD)
                    else -> try {
                        ProgressReadResult.Found(ReadingProgress(
                            bookId = record.bookId, locator = locator, percent = record.percent,
                            writerSessionEpoch = record.writerSessionEpoch,
                            writerSequence = record.writerSequence, updatedAt = record.updatedAt,
                        ))
                    } catch (_: IllegalArgumentException) {
                        ProgressReadResult.Error(ProgressErrorCode.INVALID_RECORD)
                    }
                }
            }
        }
    }
}

private fun ContentLocator.typeName(): String = when (this) {
    is TxtLocator -> "txt"
    is EpubLocator -> "epub"
    is ComicLocator -> "comic"
}
