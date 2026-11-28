package org.bookreader.mobile.annotation

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.EpubAnnotationEntity
import org.bookreader.mobile.repository.ProgressSession

class RoomEpubAnnotationsRepository(private val database: BookReaderDatabase) : EpubAnnotationsAccess {
    override suspend fun load(bookId: String, revision: String): EpubAnnotationsRead = try {
        currentCoroutineContext().ensureActive()
        database.useReaderConnection { connection -> connection.deferredTransaction {
            val book = requireNotNull(database.bookDao().findBook(bookId))
            require(book.format == "EPUB" && book.currentRevision == revision && book.availability == "READY")
            EpubAnnotationsRead.Ready(rows(bookId, revision))
        } }
    } catch (_: Exception) { currentCoroutineContext().ensureActive(); EpubAnnotationsRead.Error }

    override suspend fun save(session: ProgressSession, id: String,
        expected: EpubAnnotation?, replacement: EpubAnnotation?): AnnotationWrite = try {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        expected?.let { EpubAnnotations.validate(it); require(it.id == id) }
        replacement?.let { EpubAnnotations.validate(it); require(it.id == id && it.changeId != expected?.changeId) }
        database.useWriterConnection { connection -> connection.immediateTransaction {
            caller.ensureActive()
            val book = database.bookDao().findBook(session.bookId)
            if (book == null || book.format != "EPUB" || book.availability != "READY" ||
                book.currentRevision != session.contentRevision || book.activeSessionEpoch != session.sessionEpoch)
                return@immediateTransaction AnnotationWrite.CONFLICT
            val entries = rows(session.bookId, session.contentRevision)
            if (entries.firstOrNull { it.id == id } != expected) return@immediateTransaction AnnotationWrite.CONFLICT
            if (replacement == null) database.epubAnnotationDao().delete(session.bookId, session.contentRevision, id)
            else {
                require(expected != null || entries.size < TxtAnnotations.MAX_ENTRIES)
                database.epubAnnotationDao().save(EpubAnnotationEntity(session.bookId, session.contentRevision, id, EpubAnnotations.VERSION,
                    EpubAnnotations.json.encodeToString(EpubAnchor.serializer(), replacement.anchor), replacement.locatorJson, replacement.color.name, replacement.note, replacement.changeId))
            }
            caller.ensureActive()
            AnnotationWrite.SAVED
        } }
    } catch (_: Exception) { currentCoroutineContext().ensureActive(); AnnotationWrite.ERROR }

    private suspend fun rows(bookId: String, revision: String): List<EpubAnnotation> {
        require(!database.epubAnnotationDao().hasOversizedFields(bookId, revision))
        val rows = database.epubAnnotationDao().entries(bookId, revision)
        require(rows.size <= TxtAnnotations.MAX_ENTRIES)
        return rows.map {
            require(it.schemaVersion == EpubAnnotations.VERSION)
            EpubAnnotation(it.id, EpubAnnotations.json.decodeFromString<EpubAnchor>(it.anchorJson.also { require(it.length <= EpubAnnotations.MAX_ANCHOR) }), it.locatorJson, HighlightColor.valueOf(it.color), it.note, it.changeId)
                .also(EpubAnnotations::validate)
        }
    }
}
