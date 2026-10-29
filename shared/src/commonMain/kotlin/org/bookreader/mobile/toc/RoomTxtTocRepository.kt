package org.bookreader.mobile.toc

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.TxtTocOverrideEntity
import org.bookreader.mobile.repository.ProgressSession

class RoomTxtTocRepository(private val database: BookReaderDatabase) : TxtTocAccess {
    override suspend fun load(bookId: String, revision: String, length: Long): TxtTocRead = try {
        currentCoroutineContext().ensureActive()
        database.useReaderConnection { connection -> connection.deferredTransaction {
            val book = requireNotNull(database.bookDao().findBook(bookId))
            require(book.format == "TXT" && book.currentRevision == revision && book.availability == "READY")
            TxtTocRead.Ready(rows(bookId, revision, length))
        } }
    } catch (_: Exception) { currentCoroutineContext().ensureActive(); TxtTocRead.Error }

    override suspend fun save(session: ProgressSession, length: Long, offset: Long,
        expected: TxtTocOverride?, replacement: TxtTocOverride?): TxtTocWrite = try {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        require(offset >= 0 && offset < length)
        expected?.let { TxtToc.validate(it, length); require(it.offset == offset) }
        replacement?.let { TxtToc.validate(it, length); require(it.offset == offset && it.changeId != expected?.changeId) }
        database.useWriterConnection { connection -> connection.immediateTransaction {
            caller.ensureActive()
            val book = database.bookDao().findBook(session.bookId)
            if (book == null || book.format != "TXT" || book.availability != "READY" ||
                book.currentRevision != session.contentRevision || book.activeSessionEpoch != session.sessionEpoch)
                return@immediateTransaction TxtTocWrite.CONFLICT
            // Validate every current row: unknown future data must not be silently replaced.
            val edits = rows(session.bookId, session.contentRevision, length)
            if (edits.firstOrNull { it.offset == offset } != expected) return@immediateTransaction TxtTocWrite.CONFLICT
            if (replacement == null) database.txtTocDao().delete(session.bookId, session.contentRevision, offset)
            else {
                require(expected != null || edits.size < TxtToc.MAX_EDITS)
                database.txtTocDao().save(TxtTocOverrideEntity(session.bookId, session.contentRevision, offset, 1,
                    replacement.title, if (replacement.excluded) 1 else 0, replacement.changeId))
            }
            caller.ensureActive()
            TxtTocWrite.SAVED
        } }
    } catch (_: Exception) { currentCoroutineContext().ensureActive(); TxtTocWrite.ERROR }

    private suspend fun rows(bookId: String, revision: String, length: Long): List<TxtTocOverride> {
        val rows = database.txtTocDao().edits(bookId, revision)
        require(rows.size <= TxtToc.MAX_EDITS)
        return rows.map {
            require(it.schemaVersion == 1 && it.excluded in 0..1)
            TxtTocOverride(it.offset, it.title, it.excluded == 1, it.changeId).also { edit -> TxtToc.validate(edit, length) }
        }
    }
}
