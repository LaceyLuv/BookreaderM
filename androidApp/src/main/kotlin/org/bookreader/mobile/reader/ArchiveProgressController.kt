package org.bookreader.mobile.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bookreader.mobile.locator.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.repository.*

enum class ArchiveReaderPhase { LOADING_PROGRESS, RESTORING, READY, REFLOWING, ERROR, CLOSED }
data class ArchiveProgressState(val phase: ArchiveReaderPhase = ArchiveReaderPhase.LOADING_PROGRESS,
    val generation: Int = 0, val original: ContentLocator? = null, val accepted: ContentLocator? = null,
    val saveError: Boolean = false)

/** Shared persistence owner for SDK/image hosts. Only an actual restored host may activate a session. */
class ArchiveProgressController(val book: Book, private val progress: ReaderProgressAccess, parent: CoroutineScope) {
    init { require(book.format in setOf(BookFormat.EPUB, BookFormat.COMIC)) }
    private val lifetime = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + lifetime)
    private val mutableState = MutableStateFlow(ArchiveProgressState())
    val state = mutableState.asStateFlow()
    private var session: ProgressSession? = null
    private var sequence = 0L
    private data class Anchor(val locator: ContentLocator, val percent: Double?)
    private var pending: Anchor? = null
    private var committed: Anchor? = null
    private var activation: Job? = null
    private var settled: Job? = null
    private var sampler: Job? = null
    private val writes = Mutex()
    private val closingMutex = Mutex()
    private var closing = false
    private val load = scope.launch {
        try {
            val revision = requireNotNull(book.currentRevision)
            val original = when (val loaded = progress.load(book.id, revision)) {
                is ProgressReadResult.Found -> {
                    require(loaded.progress.bookId == book.id)
                    loaded.progress.locator.also(::validate)
                }
                ProgressReadResult.Missing -> null
                is ProgressReadResult.Error -> error("Cannot read previous progress")
            }
            currentCoroutineContext().ensureActive()
            mutableState.value = ArchiveProgressState(ArchiveReaderPhase.RESTORING, original = original, accepted = original)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(phase = ArchiveReaderPhase.ERROR) } }
    }

    fun readySession(): ProgressSession? = session.takeIf { !closing && state.value.phase == ArchiveReaderPhase.READY }

    fun beginReflow(): Int {
        val before = state.value
        if (!closing && before.phase == ArchiveReaderPhase.READY)
            mutableState.update { it.copy(phase = ArchiveReaderPhase.REFLOWING, generation = it.generation + 1) }
        return state.value.generation
    }

    fun restored(generation: Int) {
        val before = state.value
        if (closing || before.generation != generation || before.phase !in setOf(ArchiveReaderPhase.RESTORING, ArchiveReaderPhase.REFLOWING)) return
        if (session != null) { mutableState.update { it.copy(phase = ArchiveReaderPhase.READY) }; return }
        if (activation?.isActive == true) return
        activation = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = progress.startReadySession(book.id, requireNotNull(book.currentRevision), before.original)
                currentCoroutineContext().ensureActive()
                if (closing || state.value.generation != generation) return@launch
                if (result !is ReadySessionResult.Ready) error("Activation failed")
                require(result.session.bookId == book.id && result.session.contentRevision == book.currentRevision && result.session.sessionEpoch > 0)
                session = result.session
                mutableState.update { it.copy(phase = ArchiveReaderPhase.READY) }
                sampler = scope.launch { while (true) { delay(1000); persist() } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(phase = ArchiveReaderPhase.ERROR) } }
        }
        activation?.start()
    }

    /** A renderer failure does not authorize a chapter-zero fallback or a progress write. */
    fun restorationFailed() { if (!closing) mutableState.update { it.copy(phase = ArchiveReaderPhase.ERROR) } }

    fun userAnchor(generation: Int, locator: ContentLocator, percent: Double?) {
        if (closing || state.value.phase != ArchiveReaderPhase.READY || state.value.generation != generation) return
        validate(locator)
        require(percent == null || percent.isFinite() && percent in 0.0..100.0)
        pending = Anchor(locator, percent)
        mutableState.update { it.copy(accepted = locator) }
        settled?.cancel()
        settled = scope.launch { delay(250); persist() }
    }

    private fun validate(locator: ContentLocator) {
        require(locator.contentRevision == book.currentRevision && locator.version == 1)
        require(when (locator) {
            is EpubLocator -> book.format == BookFormat.EPUB && locator.payload.schemaVersion == 1 && locator.payload.sdk == "readium-kotlin"
            is ComicLocator -> book.format == BookFormat.COMIC && locator.payload.sortVersion == 1
            is TxtLocator -> false
        })
        require(LocatorCodec().encode(locator).length <= 64 * 1024)
    }

    private suspend fun persist() = writes.withLock {
        val captured = session ?: return@withLock
        val anchor = pending ?: return@withLock
        // Only READY user input can populate pending. Preserve it through renderer errors/close.
        if (anchor == committed) return@withLock
        val event = ProgressWriteEvent(captured, ++sequence, anchor.locator, anchor.percent)
        // Do not abandon a successful DB acknowledgement when a newer settling job cancels us.
        withContext(NonCancellable) {
        val result = try { progress.save(event) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE) }
        if (result == ProgressWriteResult.Committed) committed = anchor
        mutableState.update { it.copy(saveError = result != ProgressWriteResult.Committed) }
        }
    }

    suspend fun flush() {
        persist()
        try { progress.flush() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(saveError = true) } }
    }

    suspend fun close() = closingMutex.withLock {
        if (state.value.phase == ArchiveReaderPhase.CLOSED) return@withLock
        closing = true
        load.cancel(); activation?.cancel(); settled?.cancel(); sampler?.cancel()
        try { load.join(); activation?.join(); flush() }
        finally { lifetime.cancel(); mutableState.update { it.copy(phase = ArchiveReaderPhase.CLOSED) } }
    }
}
