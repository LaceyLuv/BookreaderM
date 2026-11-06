package org.bookreader.mobile.reader

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bookreader.mobile.annotation.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.repository.ProgressSession

data class TxtSelectionWindow(val token: String, val base: Long, val text: String, val session: ProgressSession)
data class TxtAnnotationsState(
    val entries: List<TxtAnnotation> = emptyList(), val ready: Boolean = false, val loading: Boolean = false,
    val readError: Boolean = false, val writing: Boolean = false, val writeError: Boolean = false,
    val window: TxtSelectionWindow? = null, val selecting: Boolean = false, val selectionError: Boolean = false,
    val savedVersion: Long = 0,
)

/** Every source read and write belongs to the Reader lifetime, with no progress side effects. */
class TxtReaderAnnotations(
    private val book: Book, private val scope: CoroutineScope, private val reader: () -> TxtReaderState,
    private val session: () -> ProgressSession?, private val read: suspend (Long, Int) -> String,
    private val navigate: (Long) -> Unit, private val access: TxtAnnotationsAccess?,
) {
    private val mutableState = MutableStateFlow(TxtAnnotationsState())
    val state = mutableState.asStateFlow()
    val editable get() = access != null
    private var loadJob: Job? = null
    private var writeJob: Job? = null
    private var selectionJob: Job? = null
    private var generation = 0L
    private var selectionVersion = 0L
    private var closed = false

    fun load(force: Boolean = false) {
        val current = reader()
        if (closed || !scope.isActive || current.phase != TxtReaderPhase.READY || !current.cache.complete ||
            state.value.loading || state.value.writing || state.value.ready && !force) return
        val version = ++generation
        mutableState.update { it.copy(loading = true, ready = false, entries = emptyList(), readError = false) }
        loadJob = scope.launch {
            try {
                val result = access?.load(book.id, requireNotNull(book.currentRevision), current.cache.committedLength)
                    ?: TxtAnnotationsRead.Ready(emptyList())
                val entries = (result as? TxtAnnotationsRead.Ready)?.entries ?: error("Annotations unavailable")
                require(entries.size <= TxtAnnotations.MAX_ENTRIES)
                for (entry in entries) {
                    ensureActive()
                    TxtAnnotations.validate(entry, current.cache.committedLength)
                    require(read(entry.quote.start, entry.quote.text.length) == entry.quote.text)
                }
                ensureActive()
                if (version == generation) mutableState.update { it.copy(entries = entries, ready = true, loading = false, readError = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (version == generation) mutableState.update { it.copy(ready = false, entries = emptyList(), loading = false, readError = true) } }
        }
    }

    fun beginSelection() {
        val current = reader(); val active = session() ?: return
        val at = current.stableOffset ?: current.targetOffset
        if (!canWrite() || at !in 0 until current.cache.committedLength || state.value.selecting) return
        cancelSelection()
        val version = selectionVersion
        mutableState.update { it.copy(selecting = true, selectionError = false) }
        selectionJob = scope.launch {
            try {
                val text = read(at, minOf(TxtAnnotations.MAX_QUOTE.toLong(), current.cache.committedLength - at).toInt())
                require(text.isNotEmpty() && text.length <= TxtAnnotations.MAX_QUOTE && TxtAnnotations.validUnicode(text))
                ensureActive()
                if (version == selectionVersion && active == session()) mutableState.update {
                    it.copy(window = TxtSelectionWindow(UUID.randomUUID().toString(), at, text, active), selecting = false)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (version == selectionVersion) mutableState.update { it.copy(selecting = false, selectionError = true) } }
        }
    }
    fun cancelSelection() {
        selectionVersion++; selectionJob?.cancel()
        mutableState.update { it.copy(window = null, selecting = false) }
    }
    fun reset() {
        generation++; loadJob?.cancel(); writeJob?.cancel(); cancelSelection()
        mutableState.value = TxtAnnotationsState()
    }
    fun close() { closed = true; reset() }

    fun add(window: TxtSelectionWindow, first: Int, last: Int, color: HighlightColor, note: String) {
        if (window != state.value.window || window.session != session()) return
        val quote = TxtAnnotations.select(window.base, window.text, first, last, reader().cache.committedLength) ?: return
        val id = UUID.randomUUID().toString()
        change(id, null, TxtAnnotation(id, quote, color, note, UUID.randomUUID().toString()))
    }
    fun edit(expected: TxtAnnotation, color: HighlightColor, note: String) =
        change(expected.id, expected, expected.copy(color = color, note = note, changeId = UUID.randomUUID().toString()))
    fun delete(expected: TxtAnnotation) = change(expected.id, expected, null)

    fun jump(entry: TxtAnnotation) {
        if (canWrite() && state.value.entries.any { it == entry }) navigate(entry.quote.start)
    }

    private fun canWrite(): Boolean = !closed && scope.isActive && access != null && state.value.ready && !state.value.writing &&
        reader().phase == TxtReaderPhase.READY && reader().cache.complete

    private fun change(id: String, expected: TxtAnnotation?, replacement: TxtAnnotation?) {
        val store = access ?: return; val active = session() ?: return; val current = reader()
        if (!canWrite() || active.bookId != book.id || active.contentRevision != book.currentRevision ||
            state.value.entries.firstOrNull { it.id == id } != expected) return
        try { replacement?.let { TxtAnnotations.validate(it, current.cache.committedLength) } }
        catch (_: IllegalArgumentException) { return }
        mutableState.update { it.copy(writing = true, writeError = false) }
        writeJob = scope.launch {
            try {
                replacement?.quote?.let { require(read(it.start, it.text.length) == it.text) }
                ensureActive()
                val result = store.save(active, current.cache.committedLength, id, expected, replacement)
                ensureActive()
                mutableState.update { it.copy(writing = false, writeError = result != AnnotationWrite.SAVED,
                    ready = false, entries = emptyList(), savedVersion = it.savedVersion + if (result == AnnotationWrite.SAVED) 1 else 0) }
                if (result == AnnotationWrite.SAVED) { cancelSelection(); load(force = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(writing = false, writeError = true, ready = false, entries = emptyList()) } }
        }
    }
}
