package org.bookreader.mobile.reader

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.repository.*
import org.bookreader.mobile.search.TxtLiteralSearch

interface ReaderBookmarkAccess {
    suspend fun load(bookId: String, revision: String, cursor: BookmarkCursor?): BookmarkReadResult
    suspend fun add(id: String, session: ProgressSession, locator: TxtLocator, label: String?, now: Long): BookmarkWriteResult
    suspend fun delete(bookId: String, id: String): BookmarkWriteResult
}

data class TxtSearchHit(val offset: Long, val snippet: String)
data class TxtToolsState(
    val query: String = "", val ignoreAsciiCase: Boolean = true,
    val searching: Boolean = false, val searchError: Boolean = false,
    val hits: List<TxtSearchHit> = emptyList(), val nextSearchOffset: Long? = null,
    val searchComplete: Boolean = false, val searchCapped: Boolean = false,
    val bookmarks: List<Bookmark> = emptyList(), val bookmarkCursor: BookmarkCursor? = null,
    val bookmarksLoading: Boolean = false, val bookmarkReadError: Boolean = false,
    val bookmarkWriting: Boolean = false, val bookmarkWriteError: Boolean = false,
    val returnOffset: Long? = null,
)

/** Owned by one immutable Reader/book lifetime; cancelled with that controller. */
class TxtReaderTools(
    private val book: Book,
    private val scope: CoroutineScope,
    private val reader: () -> TxtReaderState,
    private val session: () -> ProgressSession?,
    private val read: suspend (Long, Int) -> String,
    private val navigate: (Long) -> Unit,
    private val bookmarkAccess: ReaderBookmarkAccess?,
) {
    private val mutableState = MutableStateFlow(TxtToolsState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var bookmarkJob: Job? = null
    private var bookmarkGeneration = 0L

    fun search(query: String, ignoreAsciiCase: Boolean) {
        searchJob?.cancel()
        searchGeneration++
        mutableState.update { it.copy(query = query, ignoreAsciiCase = ignoreAsciiCase, hits = emptyList(),
            nextSearchOffset = 0, searchComplete = false, searchCapped = false, searchError = false, searching = false) }
        if (query.isNotEmpty()) moreSearch()
    }

    fun moreSearch() {
        val current = state.value
        val start = current.nextSearchOffset ?: return
        val readerState = reader()
        if (current.searching || current.hits.size >= 1000 || readerState.phase != TxtReaderPhase.READY || !readerState.cache.complete) return
        if (current.query.isEmpty() || current.query.length > TxtLiteralSearch.MAX_QUERY) {
            mutableState.update { it.copy(searchError = true) }; return
        }
        val generation = searchGeneration
        mutableState.update { it.copy(searching = true, searchError = false) }
        searchJob = scope.launch {
            try {
                val (batch, hits) = withContext(Dispatchers.Default) {
                    val batch = TxtLiteralSearch.find(current.query, current.ignoreAsciiCase,
                        readerState.cache.committedLength, start, read)
                    val hits = batch.offsets.map { offset ->
                        var begin = (offset - 24).coerceAtLeast(0)
                        val text = try { read(begin, 120) }
                        catch (_: IllegalArgumentException) { begin++; read(begin, 120) }
                        TxtSearchHit(offset, text.replace('\n', ' '))
                    }
                    batch to hits
                }
                if (generation != searchGeneration) return@launch
                mutableState.update { it.copy(searching = false, hits = (it.hits + hits).take(1000),
                    nextSearchOffset = batch.nextOffset, searchComplete = batch.nextOffset == null,
                    searchCapped = it.hits.size + hits.size >= 1000 && batch.nextOffset != null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == searchGeneration) mutableState.update { it.copy(searching = false, searchError = true) } }
        }
    }

    fun jumpFromSearch(offset: Long) {
        val current = reader()
        if (current.phase != TxtReaderPhase.READY || offset !in 0..current.cache.committedLength) return
        mutableState.update { it.copy(returnOffset = it.returnOffset ?: current.stableOffset ?: current.targetOffset) }
        navigate(offset)
    }

    fun returnFromSearch() {
        val offset = state.value.returnOffset ?: return
        if (reader().phase != TxtReaderPhase.READY) return
        mutableState.update { it.copy(returnOffset = null) }
        navigate(offset)
    }

    fun loadBookmarks(more: Boolean = false) {
        val access = bookmarkAccess ?: return
        if (reader().phase != TxtReaderPhase.READY || more && state.value.bookmarkCursor == null) return
        bookmarkJob?.cancel()
        val generation = ++bookmarkGeneration
        val cursor = if (more) state.value.bookmarkCursor else null
        if (more && state.value.bookmarks.size >= 1000) return
        mutableState.update { it.copy(bookmarksLoading = true, bookmarkReadError = false) }
        bookmarkJob = scope.launch {
            val loaded = try { access.load(book.id, requireNotNull(book.currentRevision), cursor) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { BookmarkReadResult.Error }
            if (generation != bookmarkGeneration) return@launch
            when (loaded) {
                is BookmarkReadResult.Ready -> mutableState.update { it.copy(bookmarksLoading = false,
                    bookmarks = (if (more) it.bookmarks + loaded.bookmarks else loaded.bookmarks).take(1000),
                    bookmarkCursor = loaded.next, bookmarkReadError = false) }
                BookmarkReadResult.Error -> mutableState.update { it.copy(bookmarksLoading = false, bookmarkReadError = true) }
            }
        }
    }

    fun addBookmark(label: String) {
        val access = bookmarkAccess ?: return
        val current = reader()
        val active = session() ?: return
        if (current.phase != TxtReaderPhase.READY || state.value.bookmarkWriting || label.length > 200) return
        val offset = current.stableOffset ?: current.targetOffset
        mutableState.update { it.copy(bookmarkWriting = true, bookmarkWriteError = false) }
        scope.launch {
            val result = try { access.add(UUID.randomUUID().toString(), active,
                TxtLocator(active.contentRevision, TxtLocatorPayload(offset)), label.takeIf { it.isNotEmpty() }, System.currentTimeMillis()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { BookmarkWriteResult.ERROR }
            mutableState.update { it.copy(bookmarkWriting = false, bookmarkWriteError = result != BookmarkWriteResult.COMMITTED) }
            if (result == BookmarkWriteResult.COMMITTED) loadBookmarks()
        }
    }

    fun deleteBookmark(id: String) {
        val access = bookmarkAccess ?: return
        if (state.value.bookmarkWriting || reader().phase != TxtReaderPhase.READY) return
        mutableState.update { it.copy(bookmarkWriting = true, bookmarkWriteError = false) }
        scope.launch {
            val result = try { access.delete(book.id, id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { BookmarkWriteResult.ERROR }
            mutableState.update { it.copy(bookmarkWriting = false, bookmarkWriteError = result != BookmarkWriteResult.COMMITTED) }
            if (result == BookmarkWriteResult.COMMITTED) loadBookmarks()
        }
    }
}
