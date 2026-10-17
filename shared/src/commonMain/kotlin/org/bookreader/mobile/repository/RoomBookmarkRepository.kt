package org.bookreader.mobile.repository

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.BookmarkEntity
import org.bookreader.mobile.locator.*

data class Bookmark(val id: String, val bookId: String, val locator: ContentLocator,
    val label: String?, val createdAt: Long, val updatedAt: Long)
data class BookmarkCursor(val createdAt: Long, val id: String)
sealed interface BookmarkReadResult {
    data class Ready(val bookmarks: List<Bookmark>, val next: BookmarkCursor?) : BookmarkReadResult
    data object Error : BookmarkReadResult
}
enum class BookmarkWriteResult { COMMITTED, REJECTED_STALE, ERROR }

class RoomBookmarkRepository(private val database: BookReaderDatabase) {
    private val codec = LocatorCodec()
    suspend fun load(bookId: String, revision: String, cursor: BookmarkCursor? = null): BookmarkReadResult {
        currentCoroutineContext().ensureActive()
        return try {
            val rows = database.bookmarkDao().page(bookId, revision, cursor?.createdAt ?: Long.MAX_VALUE, cursor?.id ?: "\uFFFF")
            val values = rows.take(50).map { row ->
                val locator = (codec.decode(row.locatorJson) as? LocatorDecodeResult.Success)?.locator
                    ?: error("Invalid bookmark")
                require(locator.contentRevision == row.contentRevision && locator.version == row.locatorVersion &&
                    type(locator) == row.locatorType && (row.label?.length ?: 0) <= 200)
                Bookmark(row.id, row.bookId, locator, row.label, row.createdAt, row.updatedAt)
            }
            BookmarkReadResult.Ready(values, if (rows.size > 50) values.last().let { BookmarkCursor(it.createdAt, it.id) } else null)
        } catch (_: Exception) { currentCoroutineContext().ensureActive(); BookmarkReadResult.Error }
    }

    suspend fun add(id: String, session: ProgressSession, locator: ContentLocator, label: String?, now: Long): BookmarkWriteResult {
        currentCoroutineContext().ensureActive()
        return try {
            require(id.isNotBlank() && id.length <= 128 && (label?.length ?: 0) <= 200 && now >= 0)
            require(locator.contentRevision == session.contentRevision)
            if (database.bookmarkDao().insertForSession(BookmarkEntity(id, session.bookId,
                    session.contentRevision, type(locator), locator.version, codec.encode(locator), label, now, now), session.sessionEpoch))
                BookmarkWriteResult.COMMITTED else BookmarkWriteResult.REJECTED_STALE
        } catch (_: Exception) { currentCoroutineContext().ensureActive(); BookmarkWriteResult.ERROR }
    }

    suspend fun delete(bookId: String, id: String): BookmarkWriteResult = try {
        currentCoroutineContext().ensureActive()
        database.bookmarkDao().delete(bookId, id)
        BookmarkWriteResult.COMMITTED
    } catch (_: Exception) { currentCoroutineContext().ensureActive(); BookmarkWriteResult.ERROR }

    private fun type(locator: ContentLocator) = when (locator) {
        is TxtLocator -> "txt"; is EpubLocator -> "epub"; is ComicLocator -> "comic"
    }
}
