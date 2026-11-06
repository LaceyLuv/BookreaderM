package org.bookreader.mobile.annotation

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.TxtAnnotationEntity
import org.bookreader.mobile.repository.ProgressSession

class RoomTxtAnnotationsRepository(private val database: BookReaderDatabase) : TxtAnnotationsAccess {
    override suspend fun load(bookId: String, revision: String, length: Long): TxtAnnotationsRead = try {
        currentCoroutineContext().ensureActive()
        database.useReaderConnection { connection -> connection.deferredTransaction {
            val book = requireNotNull(database.bookDao().findBook(bookId))
            require(book.format == "TXT" && book.currentRevision == revision && book.availability == "READY")
            TxtAnnotationsRead.Ready(rows(bookId, revision, length))
        } }
    } catch (_: Exception) { currentCoroutineContext().ensureActive(); TxtAnnotationsRead.Error }

    override suspend fun save(session: ProgressSession, length: Long, id: String,
        expected: TxtAnnotation?, replacement: TxtAnnotation?): AnnotationWrite = try {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        expected?.let { TxtAnnotations.validate(it, length); require(it.id == id) }
        replacement?.let { TxtAnnotations.validate(it, length); require(it.id == id && it.changeId != expected?.changeId) }
        database.useWriterConnection { connection -> connection.immediateTransaction {
            caller.ensureActive()
            val book = database.bookDao().findBook(session.bookId)
            if (book == null || book.format != "TXT" || book.availability != "READY" ||
                book.currentRevision != session.contentRevision || book.activeSessionEpoch != session.sessionEpoch)
                return@immediateTransaction AnnotationWrite.CONFLICT
            val entries = rows(session.bookId, session.contentRevision, length)
            if (entries.firstOrNull { it.id == id } != expected) return@immediateTransaction AnnotationWrite.CONFLICT
            if (replacement == null) database.txtAnnotationDao().delete(session.bookId, session.contentRevision, id)
            else {
                require(expected != null || entries.size < TxtAnnotations.MAX_ENTRIES)
                database.txtAnnotationDao().save(TxtAnnotationEntity(session.bookId, session.contentRevision, id, TxtAnnotations.VERSION,
                    replacement.quote.start, replacement.quote.end, replacement.quote.text, replacement.color.name, replacement.note, replacement.changeId))
            }
            caller.ensureActive()
            AnnotationWrite.SAVED
        } }
    } catch (_: Exception) { currentCoroutineContext().ensureActive(); AnnotationWrite.ERROR }

    private suspend fun rows(bookId: String, revision: String, length: Long): List<TxtAnnotation> {
        require(!database.txtAnnotationDao().hasOversizedFields(bookId, revision))
        val rows = database.txtAnnotationDao().entries(bookId, revision)
        require(rows.size <= TxtAnnotations.MAX_ENTRIES)
        return rows.map {
            require(it.schemaVersion == TxtAnnotations.VERSION)
            TxtAnnotation(it.id, TxtQuote(it.startOffset, it.endOffset, it.quote), HighlightColor.valueOf(it.color), it.note, it.changeId)
                .also { entry -> TxtAnnotations.validate(entry, length) }
        }
    }
}
