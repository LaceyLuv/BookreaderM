package org.bookreader.mobile.reader

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bookreader.mobile.locator.ContentLocator
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.ProgressSession
import org.bookreader.mobile.repository.ProgressWriteEvent
import org.bookreader.mobile.repository.ProgressWriteResult
import org.bookreader.mobile.repository.ReadySessionResult
import org.bookreader.mobile.repository.DatabaseFailureDiagnostic
import org.bookreader.mobile.repository.failureTypes
import org.bookreader.mobile.settings.*

interface ReaderSettingsAccess {
    suspend fun load(bookId: String): ReaderSettingsRead
    suspend fun save(expected: LoadedReaderSettings, global: ReaderSettings, book: BookReaderPreferences): ReaderSettingsWrite
}

interface ReaderProgressAccess {
    /** Optional sanitized failure from the most recent operation; implementations clear it per call. */
    fun lastDatabaseFailure(): DatabaseFailureDiagnostic? = null
    suspend fun load(bookId: String, revision: String): ProgressReadResult
    suspend fun startReadySession(bookId: String, revision: String, restoredLocator: ContentLocator?): ReadySessionResult
    suspend fun save(event: ProgressWriteEvent): ProgressWriteResult
    suspend fun flush()
}

enum class TxtReaderPhase { OPENING, LOADING_PROGRESS, PREPARING, RESTORING, READY, REFLOWING, ERROR }
enum class ReaderFailureStage { LOAD_PROGRESS, CACHE_BUILD, RESTORE_POSITION, ACTIVATE, SAVE, FLUSH, READ_FRAGMENT }
data class ReaderFailureDiagnostic(
    val stage: ReaderFailureStage,
    val resultCode: String? = null,
    val exceptionTypes: List<String> = emptyList(),
    val database: DatabaseFailureDiagnostic? = null,
)
data class TxtReaderState(
    val phase: TxtReaderPhase = TxtReaderPhase.OPENING,
    val cache: TxtCacheSnapshot = TxtCacheSnapshot(),
    val targetOffset: Long = 0,
    val restoreGeneration: Int = 0,
    val stableOffset: Long? = null,
    val error: String? = null,
    val saveError: Boolean = false,
    val sourceAvailability: BookAvailability? = null,
    val failureDiagnostic: ReaderFailureDiagnostic? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val settingsLoaded: LoadedReaderSettings? = null,
    val settingsError: Boolean = false,
    val settingsSaving: Boolean = false,
)

/** Retained by the app ViewModel, never saved as a navigation destination across process death. */
class TxtReaderController(
    val book: Book,
    private val managedFile: File,
    cacheDirectory: File,
    private val progress: ReaderProgressAccess,
    parentScope: CoroutineScope,
    private val settingsAccess: ReaderSettingsAccess? = null,
    bookmarkAccess: ReaderBookmarkAccess? = null,
) {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private val directory = cacheDirectory
    var cache = TxtCanonicalCache(book, cacheDirectory)
        private set
    private val mutableState = MutableStateFlow(TxtReaderState())
    val state: StateFlow<TxtReaderState> = mutableState.asStateFlow()
    private var openJob: Job? = null
    private var settleJob: Job? = null
    private var sampler: Job? = null
    private var restoringJob: Job? = null
    private var displayedGeneration: Int? = null
    private var originalLocator: ContentLocator? = null
    private var session: ProgressSession? = null
    private var sequence = 0L
    private var lastCommittedOffset: Long? = null
    private var closing = false
    private val writerMutex = Mutex()
    private var settingsJob: Job? = null
    private var navigationGeneration: Int? = null
    private var seekJob: Job? = null
    val tools = TxtReaderTools(book, scope, { state.value }, { session }, ::range, ::navigateTo, bookmarkAccess)

    init { retry() }

    fun retry() {
        if (closing) return
        seekJob?.cancel()
        navigationGeneration = null
        val previousOpen = openJob
        previousOpen?.cancel()
        settleJob?.cancel()
        sampler?.cancel()
        restoringJob?.cancel()
        session = null
        originalLocator = null
        lastCommittedOffset = null
        sequence = 0
        openJob = scope.launch {
            previousOpen?.cancelAndJoin()
            restoringJob?.cancelAndJoin()
            displayedGeneration = null
            cache = TxtCanonicalCache(book, directory)
            mutableState.value = TxtReaderState(phase = TxtReaderPhase.LOADING_PROGRESS,
                restoreGeneration = mutableState.value.restoreGeneration + 1)
            var failureStage = ReaderFailureStage.LOAD_PROGRESS
            var resultCode: String? = null
            try {
                loadSettings()
                val revision = requireNotNull(book.currentRevision)
                when (val loaded = progress.load(book.id, revision)) {
                    is ProgressReadResult.Error -> {
                        resultCode = loaded.code.name
                        error("독서 기록을 읽을 수 없습니다. 기존 기록은 보존됩니다.")
                    }
                    is ProgressReadResult.Found -> {
                        val locator = loaded.progress.locator as? TxtLocator
                            ?: error("지원하지 않는 독서 위치입니다. 기존 기록은 보존됩니다.")
                        require(locator.contentRevision == revision) { "본문 버전이 독서 기록과 다릅니다." }
                        originalLocator = locator
                    }
                    ProgressReadResult.Missing -> Unit
                }
                val target = (originalLocator as? TxtLocator)?.payload?.utf16Offset ?: 0
                failureStage = ReaderFailureStage.CACHE_BUILD
                mutableState.update { it.copy(phase = TxtReaderPhase.PREPARING, targetOffset = target) }
                withContext(Dispatchers.IO) {
                    val buildContext = coroutineContext
                    cache.openOrBuild(managedFile, { buildContext.ensureActive() }) { snapshot ->
                        mutableState.update { old ->
                                val available = snapshot.committedLength > old.targetOffset ||
                                    (snapshot.complete && snapshot.committedLength == old.targetOffset)
                                old.copy(cache = snapshot, phase =
                                    if (old.phase == TxtReaderPhase.PREPARING && available)
                                        TxtReaderPhase.RESTORING else old.phase)
                        }
                    }
                }
                val snapshot = cache.snapshot()
                failureStage = ReaderFailureStage.RESTORE_POSITION
                require(target <= snapshot.committedLength) { "저장된 위치가 본문 범위를 벗어납니다." }
                mutableState.update { it.copy(cache = snapshot,
                    phase = if (it.phase == TxtReaderPhase.PREPARING) TxtReaderPhase.RESTORING else it.phase) }
                displayedGeneration?.let { restored(it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutableState.update { it.copy(phase = TxtReaderPhase.ERROR,
                    failureDiagnostic = diagnostic(failureStage, resultCode, failure),
                    sourceAvailability = (failure as? TxtSourceException)?.availability,
                    error = if (failure.message == "EMPTY_TXT") "내용이 없는 텍스트 파일입니다."
                        else "본문 또는 독서 기록을 열 수 없습니다. 원본과 기존 기록은 보존됩니다.") }
            }
        }
    }

    suspend fun fragment(index: Int): String = withContext(Dispatchers.IO) {
        try { cache.readFragment(index) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            mutableState.update { it.copy(failureDiagnostic = diagnostic(ReaderFailureStage.READ_FRAGMENT, failure = failure)) }
            throw failure
        }
    }

    suspend fun range(start: Long, maxUnits: Int): String = withContext(Dispatchers.IO) {
        cache.readRange(start, maxUnits)
    }

    private suspend fun loadSettings() {
        val access = settingsAccess ?: return
        when (val loaded = access.load(book.id)) {
            is ReaderSettingsRead.Ready -> mutableState.update {
                val reflow = it.phase in setOf(TxtReaderPhase.READY, TxtReaderPhase.REFLOWING)
                it.copy(settings = loaded.value.effective, settingsLoaded = loaded.value, settingsError = false,
                    phase = if (reflow) TxtReaderPhase.REFLOWING else it.phase,
                    targetOffset = if (reflow) it.stableOffset ?: it.targetOffset else it.targetOffset,
                    restoreGeneration = it.restoreGeneration + if (reflow) 1 else 0)
            }
            ReaderSettingsRead.Error -> mutableState.update { it.copy(settingsError = true) }
        }
    }

    fun retrySettings() {
        if (closing || settingsJob?.isActive == true) return
        settingsJob = scope.launch {
            loadSettings()
        }
    }

    fun saveSettings(global: ReaderSettings, preferences: BookReaderPreferences) {
        val access = settingsAccess ?: return
        val before = state.value
        val expected = before.settingsLoaded ?: return
        if (closing || before.phase != TxtReaderPhase.READY || before.settingsSaving || before.settingsError) return
        mutableState.update { it.copy(settingsSaving = true) }
        settingsJob = scope.launch {
            when (access.save(expected, global, preferences)) {
                ReaderSettingsWrite.SAVED -> {
                    loadSettings()
                }
                ReaderSettingsWrite.CONFLICT, ReaderSettingsWrite.ERROR ->
                    mutableState.update { it.copy(settingsError = true) }
            }
            mutableState.update { it.copy(settingsSaving = false) }
        }
    }

    /** Navigation freezes the old anchor until the target's actual measured layout is visible. */
    fun navigateTo(offset: Long) {
        val current = state.value
        if (closing || session == null || current.phase !in setOf(TxtReaderPhase.READY, TxtReaderPhase.REFLOWING) || !current.cache.complete ||
            offset !in 0..current.cache.committedLength) return
        settleJob?.cancel()
        val generation = current.restoreGeneration + 1
        navigationGeneration = generation
        mutableState.update { it.copy(phase = TxtReaderPhase.REFLOWING, targetOffset = offset,
            restoreGeneration = generation) }
    }

    fun seekToFraction(fraction: Float) {
        if (!fraction.isFinite() || closing || session == null || !state.value.cache.complete ||
            state.value.phase !in setOf(TxtReaderPhase.READY, TxtReaderPhase.REFLOWING)) return
        seekJob?.cancel()
        val cacheForRequest = cache
        val generation = state.value.restoreGeneration
        val requested = (state.value.cache.committedLength * fraction.coerceIn(0f, 1f).toDouble()).toLong()
        seekJob = scope.launch {
            try {
                val anchor = withContext(Dispatchers.IO) { cacheForRequest.anchorAtOrBefore(requested) }
                if (cache === cacheForRequest && state.value.restoreGeneration == generation) navigateTo(anchor)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { contentReadFailed() }
        }
    }

    fun restoreFailed() {
        mutableState.update { it.copy(phase = TxtReaderPhase.ERROR,
            failureDiagnostic = diagnostic(ReaderFailureStage.RESTORE_POSITION),
            error = "저장된 위치가 유효한 문자 경계가 아닙니다. 기존 기록은 보존됩니다.") }
    }

    fun contentReadFailed() {
        mutableState.update { it.copy(phase = TxtReaderPhase.ERROR,
            error = "본문을 읽을 수 없습니다. 다시 시도하면 원본에서 복원합니다.") }
    }

    /** The screen calls this only after actual TextLayout measurement and canonical-anchor scroll. */
    @Synchronized
    fun restored(generation: Int) {
        val before = mutableState.value
        if (before.restoreGeneration != generation || before.phase !in
            setOf(TxtReaderPhase.RESTORING, TxtReaderPhase.REFLOWING)) return
        displayedGeneration = generation
        // Early prefix display is allowed; integrity/strict decoding must finish before durable writes.
        if (!before.cache.complete) return
        // Reflow already has an activated session. Its callback must not be lost
        // while the preceding activation coroutine is finishing after publishing READY.
        if (session != null) { finishRestoration(generation); return }
        if (restoringJob?.isActive == true) return
        // Claim activation before its body can run or another completion callback can enter.
        // Lazy start also prevents an immediate/reentrant callback from observing a missing job.
        restoringJob = scope.launch(start = CoroutineStart.LAZY) {
            if (session == null) {
                val started = try {
                    progress.startReadySession(book.id, requireNotNull(book.currentRevision), originalLocator)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    mutableState.update { it.copy(phase = TxtReaderPhase.ERROR,
                        failureDiagnostic = diagnostic(ReaderFailureStage.ACTIVATE, failure = failure),
                        error = "독서 기록 저장을 준비할 수 없습니다. 기존 기록은 보존됩니다.") }
                    return@launch
                }
                if (started !is ReadySessionResult.Ready) {
                    mutableState.update { it.copy(phase = TxtReaderPhase.ERROR,
                        failureDiagnostic = diagnostic(ReaderFailureStage.ACTIVATE,
                            (started as? ReadySessionResult.Error)?.code?.name),
                        error = "독서 기록 저장을 준비할 수 없습니다. 기존 기록은 보존됩니다.") }
                    return@launch
                }
                session = started.session
                sampler = scope.launch {
                    while (true) { delay(1_000); persistStable() }
                }
            }
            finishRestoration(generation)
        }
        restoringJob?.start()
    }

    @Synchronized
    private fun finishRestoration(generation: Int) {
        val current = mutableState.value
        if (closing || current.restoreGeneration != generation || current.phase !in
            setOf(TxtReaderPhase.RESTORING, TxtReaderPhase.REFLOWING)) return
        val navigated = navigationGeneration == generation
        if (navigated) navigationGeneration = null
        // Publish READY and the newly measured navigation anchor as one state change.
        mutableState.update { it.copy(phase = TxtReaderPhase.READY,
            stableOffset = if (navigated) it.targetOffset else it.stableOffset) }
        if (navigated) scope.launch { persistStable() }
    }

    fun beginReflow() {
        val state = mutableState.value
        if (state.phase != TxtReaderPhase.READY) return
        settleJob?.cancel()
        mutableState.update { it.copy(phase = TxtReaderPhase.REFLOWING,
            targetOffset = it.stableOffset ?: it.targetOffset,
            restoreGeneration = it.restoreGeneration + 1) }
    }

    /** Only user scrolling after Ready can change the durable locator. Layout callbacks alone cannot. */
    fun visibleAnchor(offset: Long, userMovement: Boolean) {
        val state = mutableState.value
        if (!userMovement || closing || state.phase != TxtReaderPhase.READY ||
            offset !in 0..state.cache.committedLength) return
        mutableState.update { it.copy(stableOffset = offset) }
        settleJob?.cancel()
        settleJob = scope.launch { delay(250); persistStable() }
    }

    private suspend fun persistStable() = writerMutex.withLock {
        val state = mutableState.value
        val capturedSession = session ?: return@withLock
        val offset = state.stableOffset ?: return@withLock
        // Reflow freezes the last user anchor accepted in READY. Flush/sampling may
        // commit it while layout is pending; reflow callbacks cannot replace it.
        if (state.phase !in setOf(TxtReaderPhase.READY, TxtReaderPhase.REFLOWING) ||
            offset == lastCommittedOffset) return@withLock
        val event = ProgressWriteEvent(capturedSession, ++sequence,
            TxtLocator(capturedSession.contentRevision, TxtLocatorPayload(offset)),
            if (state.cache.complete) offset * 100.0 / state.cache.committedLength else null)
        val result = try { progress.save(event) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            mutableState.update { it.copy(saveError = true,
                failureDiagnostic = diagnostic(ReaderFailureStage.SAVE, failure = failure)) }
            return@withLock
        }
        when (result) {
            ProgressWriteResult.Committed -> {
                lastCommittedOffset = offset
                mutableState.update { it.copy(saveError = false, failureDiagnostic = null) }
            }
            ProgressWriteResult.RejectedStale -> mutableState.update { it.copy(saveError = true,
                failureDiagnostic = diagnostic(ReaderFailureStage.SAVE, "REJECTED_STALE")) }
            is ProgressWriteResult.Error -> mutableState.update { it.copy(saveError = true,
                failureDiagnostic = diagnostic(ReaderFailureStage.SAVE, result.code.name)) }
        }
    }

    suspend fun flush() {
        persistStable()
        try { progress.flush() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { mutableState.update { it.copy(saveError = true,
            failureDiagnostic = diagnostic(ReaderFailureStage.FLUSH, failure = failure)) } }
    }

    private fun diagnostic(stage: ReaderFailureStage, resultCode: String? = null, failure: Exception? = null) =
        ReaderFailureDiagnostic(stage, resultCode, failure?.let(::failureTypes).orEmpty(),
            if (stage in setOf(ReaderFailureStage.LOAD_PROGRESS, ReaderFailureStage.ACTIVATE, ReaderFailureStage.SAVE))
                progress.lastDatabaseFailure() else null)

    suspend fun close() {
        if (closing) return
        closing = true
        settleJob?.cancel()
        sampler?.cancel()
        // A pending activation can hold progress access's mutex while waiting for Room.
        // Cancel opening/restoration before flush waits for that same mutex. Already
        // accepted READY/REFLOWING anchors and in-flight saves still use the flush barrier.
        openJob?.cancel()
        restoringJob?.cancel()
        settingsJob?.cancel()
        seekJob?.cancel()
        try {
            openJob?.join()
            restoringJob?.join()
            settingsJob?.join()
            flush()
        } finally { lifetime.cancel() }
    }
}
