package org.bookreader.mobile.reader

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.repository.ProgressSession
import org.bookreader.mobile.toc.*

data class TxtTocState(
    val entries: List<TxtTocEntry> = emptyList(), val edits: List<TxtTocOverride> = emptyList(),
    val headings: List<TxtHeading> = emptyList(), val loading: Boolean = false,
    val ready: Boolean = false, val readError: Boolean = false, val truncated: Boolean = false,
    val writing: Boolean = false, val writeError: Boolean = false, val savedVersion: Long = 0,
)

/** Work and derived candidates belong to this Reader lifetime; scanning never writes progress. */
class TxtReaderToc(
    private val book: Book, private val scope: CoroutineScope,
    private val reader: () -> TxtReaderState, private val session: () -> ProgressSession?,
    private val read: suspend (Long, Int) -> String, private val navigate: (Long) -> Unit,
    private val access: TxtTocAccess?,
) {
    private val mutableState = MutableStateFlow(TxtTocState())
    val state = mutableState.asStateFlow()
    val editable get() = access != null
    private var scan: TxtTocScan? = null
    private var loadJob: Job? = null
    private var writeJob: Job? = null
    private var generation = 0L
    private var closed = false

    fun load() {
        val current = reader()
        if (closed || !scope.isActive || current.phase != TxtReaderPhase.READY || !current.cache.complete || state.value.loading || state.value.writing) return
        val version = ++generation
        mutableState.update { it.copy(loading = true, ready = false, readError = false) }
        loadJob = scope.launch {
            try {
                val result = scan ?: withContext(Dispatchers.Default) { TxtTocScanner.scan(current.cache.committedLength, read) }
                ensureActive()
                val edits = when (val loaded = access?.load(book.id, requireNotNull(book.currentRevision), current.cache.committedLength)
                    ?: TxtTocRead.Ready(emptyList())) {
                    is TxtTocRead.Ready -> loaded.edits
                    TxtTocRead.Error -> error("TOC read failed")
                }
                ensureActive()
                if (version != generation) return@launch
                scan = result
                val entries = withContext(Dispatchers.Default) { TxtToc.merge(result.headings, edits) }
                if (version == generation) mutableState.update { it.copy(entries = entries, edits = edits,
                    headings = result.headings, truncated = result.truncated, loading = false, ready = true, readError = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (version == generation) mutableState.update { it.copy(loading = false, ready = false, readError = true) } }
        }
    }

    fun cancelLoad() {
        generation++; loadJob?.cancel()
        mutableState.update { it.copy(loading = false) }
    }
    fun reset() { cancelLoad(); writeJob?.cancel(); scan = null; mutableState.value = TxtTocState() }
    fun close() { closed = true; cancelLoad(); writeJob?.cancel() }

    fun jump(offset: Long) {
        val current = reader()
        if (closed || current.phase != TxtReaderPhase.READY || !state.value.ready || state.value.writing ||
            state.value.entries.none { it.offset == offset } || offset !in 0 until current.cache.committedLength) return
        navigate(offset)
    }

    fun renameOrAdd(offset: Long, title: String) {
        val clean = title.trim()
        if (!TxtToc.validTitle(clean)) return
        change(offset, TxtTocOverride(offset, clean, false, UUID.randomUUID().toString()))
    }
    fun exclude(offset: Long) = change(offset, TxtTocOverride(offset, null, true, UUID.randomUUID().toString()))
    fun clearEdit(offset: Long) = change(offset, null)

    private fun change(offset: Long, replacement: TxtTocOverride?) {
        val store = access ?: return
        val active = session() ?: return
        val current = reader()
        val before = state.value
        if (closed || !scope.isActive || current.phase != TxtReaderPhase.READY || !current.cache.complete || !before.ready || before.writing ||
            active.bookId != book.id || active.contentRevision != book.currentRevision || offset !in 0 until current.cache.committedLength) return
        val expected = before.edits.firstOrNull { it.offset == offset }
        mutableState.update { it.copy(writing = true, writeError = false) }
        writeJob = scope.launch {
            try {
                // The canonical reader rejects a leading low surrogate. No guessed offset correction.
                val boundary = read(offset, minOf(2L, current.cache.committedLength - offset).toInt())
                require(boundary.isNotEmpty() && !boundary[0].isLowSurrogate())
                ensureActive()
                val result = store.save(active, current.cache.committedLength, offset, expected, replacement)
                ensureActive()
                mutableState.update { it.copy(writing = false, writeError = result != TxtTocWrite.SAVED,
                    ready = result == TxtTocWrite.SAVED && it.ready,
                    savedVersion = it.savedVersion + if (result == TxtTocWrite.SAVED) 1 else 0) }
                if (result == TxtTocWrite.SAVED) load()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(writing = false, writeError = true) } }
        }
    }
}
