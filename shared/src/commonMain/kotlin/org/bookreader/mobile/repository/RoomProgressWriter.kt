package org.bookreader.mobile.repository

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.locator.ComicLocator
import org.bookreader.mobile.locator.ContentLocator
import org.bookreader.mobile.locator.EpubLocator
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.model.ReadingProgress

class RoomProgressWriter(private val database: BookReaderDatabase, private val now: () -> Long) : ProgressWriter {
    private val codec = LocatorCodec()

    override suspend fun startReadySession(bookId: String, contentRevision: String, restoredLocator: ContentLocator?): ReadySessionResult {
        currentCoroutineContext().ensureActive()
        return try {
            val expected = database.progressDao().findProgress(bookId, contentRevision)
            when (val read = RoomProgressRepository(database).loadProgress(bookId, contentRevision)) {
                is ProgressReadResult.Error -> return ReadySessionResult.Error(read.code)
                ProgressReadResult.Missing -> if (restoredLocator != null || expected != null) return ReadySessionResult.Error(ProgressErrorCode.INVALID_RECORD)
                is ProgressReadResult.Found -> if (restoredLocator != read.progress.locator || expected == null) return ReadySessionResult.Error(ProgressErrorCode.INVALID_RECORD)
            }
            val epoch = database.progressWriterDao().activate(bookId, contentRevision, expected, now())
                ?: return ReadySessionResult.Error(ProgressErrorCode.REVISION_MISMATCH)
            ReadySessionResult.Ready(ProgressSession(bookId, contentRevision, epoch))
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            ReadySessionResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE)
        }
    }

    override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult {
        currentCoroutineContext().ensureActive()
        val session = event.session
        if (event.locator.contentRevision != session.contentRevision || event.sequence < 1 || session.sessionEpoch < 1) return ProgressWriteResult.Error(ProgressErrorCode.INVALID_RECORD)
        return try {
            ReadingProgress(session.bookId, event.locator, event.percent, session.sessionEpoch, event.sequence, now())
            val type = when (event.locator) { is TxtLocator -> "txt"; is EpubLocator -> "epub"; is ComicLocator -> "comic" }
            val book = database.bookDao().findBook(session.bookId)
            if (book == null || book.format.lowercase() != type) return ProgressWriteResult.RejectedStale
            val committed = database.progressWriterDao().commit(ReadingProgressEntity(
                session.bookId, session.contentRevision, type, event.locator.version,
                codec.encode(event.locator), event.percent, session.sessionEpoch, event.sequence, now(),
            ))
            if (committed) ProgressWriteResult.Committed else ProgressWriteResult.RejectedStale
        } catch (_: IllegalArgumentException) {
            ProgressWriteResult.Error(ProgressErrorCode.INVALID_RECORD)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE)
        }
    }
}
