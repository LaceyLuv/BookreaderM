package org.bookreader.mobile.epub

import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import org.bookreader.mobile.locator.EpubLocator
import org.bookreader.mobile.locator.EpubLocatorPayload
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.reader.*
import org.bookreader.mobile.repository.*
import org.bookreader.mobile.settings.*
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.getOrElse

interface ArchiveBookmarkAccess {
    suspend fun load(bookId: String, revision: String, cursor: BookmarkCursor?): BookmarkReadResult
    suspend fun add(id: String, session: ProgressSession, locator: org.bookreader.mobile.locator.ContentLocator, label: String?, now: Long): BookmarkWriteResult
    suspend fun delete(bookId: String, id: String): BookmarkWriteResult
}

data class EpubReaderUi(val opened: Boolean = false, val error: String? = null,
    val settings: ReaderSettings = ReaderSettings(), val settingsError: Boolean = false,
    val settingsSaving: Boolean = false, val hostVersion: Int = 0,
    val query: String = "", val results: List<Locator> = emptyList(), val searching: Boolean = false,
    val searchError: Boolean = false, val bookmarks: List<Bookmark> = emptyList(), val bookmarkError: Boolean = false,
    val bookmarkCursor: BookmarkCursor? = null, val canReturn: Boolean = false, val externalUrl: String? = null, val notice: String? = null)

/** ViewModel owner for one verified private EPUB and its progress session. UI callbacks run on Main. */
@OptIn(ExperimentalReadiumApi::class)
class EpubReaderController(val book: Book, private val file: File, private val verifySource: suspend () -> Unit,
    access: ReaderProgressAccess, parent: CoroutineScope, private val settingsAccess: ReaderSettingsAccess,
    private val bookmarkAccess: ArchiveBookmarkAccess) {
    private val lifetime = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + lifetime)
    val progress = ArchiveProgressController(book, access, scope)
    private val mutableState = MutableStateFlow(EpubReaderUi())
    val state = mutableState.asStateFlow()
    private val ownedPublication = java.util.concurrent.atomic.AtomicReference<Publication?>(null)
    var publication: Publication?
        get() = ownedPublication.get()
        private set(value) { ownedPublication.set(value) }
    init { lifetime.invokeOnCompletion { ownedPublication.getAndSet(null)?.close() } }
    private var settingsLoaded: LoadedReaderSettings? = null
    private var host: EpubNavigatorFragment? = null
    private var detachHost: (() -> Unit)? = null
    private var generation = 0
    private var userMovement = false
    private var jumpTarget: Locator? = null
    // A requested jump is not an accepted reading position until a live host renders it.
    // Keep it across the host replacement caused by dismissing a search dialog/IME.
    private var pendingJump: Locator? = null
    private var captureJob: Job? = null
    private var seekJob: Job? = null
    private var returnTo: Locator? = null
    private var closing = false
    private var searchJob: Job? = null
    private var settingsJob: Job? = null
    private var restoreTimeout: Job? = null
    private var queryGeneration = 0
    private val openJob = scope.launch {
        try {
            val initial = progress.state.first { it.phase != ArchiveReaderPhase.LOADING_PROGRESS }
            check(initial.phase == ArchiveReaderPhase.RESTORING)
            when (val read = settingsAccess.load(book.id)) {
                is ReaderSettingsRead.Ready -> { settingsLoaded = read.value; mutableState.update { it.copy(settings = read.value.effective) } }
                ReaderSettingsRead.Error -> mutableState.update { it.copy(settingsError = true) }
            }
            verifySource()
            val opened = SafeEpub.open(file)
            publication = opened
            currentCoroutineContext().ensureActive()
            (initial.original as? EpubLocator)?.let { validate(sdk(it)) }
            mutableState.update { it.copy(opened = true) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { fail("EPUB 또는 저장된 위치를 열지 못했습니다. 기존 기록은 보존됩니다.") }
        finally { if (!state.value.opened) { ownedPublication.getAndSet(null)?.close() } }
    }

    val loadedSettings: LoadedReaderSettings? get() = settingsLoaded
    fun reflowViewport() {
        if (progress.state.value.phase == ArchiveReaderPhase.READY) {
            progress.beginReflow(); mutableState.update { it.copy(hostVersion = it.hostVersion + 1) }
        }
    }
    fun retrySettings() { scope.launch {
        when (val read = settingsAccess.load(book.id)) {
            is ReaderSettingsRead.Ready -> {
                settingsLoaded = read.value; progress.beginReflow()
                mutableState.update { it.copy(settings = read.value.effective, settingsError = false, hostVersion = it.hostVersion + 1) }
            }
            ReaderSettingsRead.Error -> mutableState.update { it.copy(settingsError = true) }
        }
    } }
    fun initialLocator(): Locator? = pendingJump ?: (progress.state.value.accepted as? EpubLocator)?.let(::sdk)
    fun preferences(): EpubPreferences = state.value.settings.let {
        EpubPreferences(scroll = it.readingMode == TxtReadingMode.SCROLL, fontSize = it.fontSizeSp / 18.0,
            lineHeight = it.lineHeight.toDouble(), publisherStyles = it.epubPublisherStyles,
            letterSpacing = it.letterSpacingEm.coerceAtLeast(0f).toDouble() * 2, pageMargins = it.marginDp / 16.0,
            fontFamily = if (it.font == ReaderFont.SANS) org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF else org.readium.r2.navigator.preferences.FontFamily.SERIF,
            backgroundColor = if (it.theme == ReaderTheme.OLED) org.readium.r2.navigator.preferences.Color(0xff000000.toInt()) else null,
            theme = when (it.theme) {
                ReaderTheme.LIGHT -> Theme.LIGHT; ReaderTheme.SEPIA -> Theme.SEPIA; else -> Theme.DARK
            })
    }
    fun attach(navigator: EpubNavigatorFragment, detach: () -> Unit): Int {
        check(!closing)
        captureJob?.cancel()
        host = navigator; detachHost = detach; userMovement = false; jumpTarget = null
        generation = progress.beginReflow()
        restoreTimeout?.cancel()
        val expectedGeneration = generation
        restoreTimeout = scope.launch {
            delay(30_000)
            if (!closing && host === navigator && generation == expectedGeneration &&
                progress.state.value.phase in setOf(ArchiveReaderPhase.RESTORING, ArchiveReaderPhase.REFLOWING))
                fail("독서 위치 복원이 지연되고 있습니다. 기존 기록을 보존한 채 다시 시도할 수 있습니다.")
        }
        return generation
    }
    fun detach(navigator: EpubNavigatorFragment) {
        if (host === navigator) { captureJob?.cancel(); restoreTimeout?.cancel(); host = null; detachHost = null; userMovement = false }
    }
    fun pageChanged(navigator: EpubNavigatorFragment, token: Int, locator: Locator) {
        if (closing || host !== navigator || token != generation || token != progress.state.value.generation) return
        try {
            validate(locator)
            val before = progress.state.value
            if (before.phase in setOf(ArchiveReaderPhase.RESTORING, ArchiveReaderPhase.REFLOWING)) {
                val target = initialLocator()
                if (target != null && target.href.removeFragment().removeQuery() != locator.href.removeFragment().removeQuery()) return
                restoreTimeout?.cancel()
                progress.restored(token)
            }
            if ((userMovement || pendingJump != null) && progress.state.value.let {
                    it.phase == ArchiveReaderPhase.READY && it.generation == token
                }) {
                captureJob?.cancel()
                val requested = pendingJump
                // Ignore callbacks from the old chapter while a jump is still in flight.
                if (requested != null && requested.href.removeFragment().removeQuery() != locator.href.removeFragment().removeQuery()) return
                val target = (requested ?: jumpTarget)?.takeIf { it.href.removeFragment().removeQuery() == locator.href.removeFragment().removeQuery() }
                captureJob = scope.launch {
                    try {
                        // The page callback contains progression only. Preserve the user's search
                        // text/fragment, or ask the SDK for the actual first visible DOM block.
                        val precise = target ?: navigator.firstVisibleElementLocator() ?: locator
                        ensureActive()
                        if (closing || host !== navigator || token != generation || progress.state.value.phase != ArchiveReaderPhase.READY) return@launch
                        if (precise.href.removeFragment().removeQuery() != locator.href.removeFragment().removeQuery()) return@launch
                        val anchored = precise.copy(title = locator.title, locations = precise.locations.copy(
                            progression = locator.locations.progression, position = locator.locations.position,
                            totalProgression = locator.locations.totalProgression),
                            // A very large HTML block still has its exact SDK CSS selector.
                            text = if ((precise.text.highlight?.length ?: 0) > 4096) Locator.Text() else precise.text)
                        progress.userAnchor(token, canonical(anchored), locator.locations.totalProgression?.times(100))
                        if (pendingJump === requested) pendingJump = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { fail("표시 위치를 읽지 못했습니다. 기존 독서 기록은 보존됩니다.") }
                }
            }
        } catch (_: Exception) { fail("독서 위치를 복원하지 못했습니다. 기존 기록은 보존됩니다.") }
    }
    fun armMovement() { if (progress.state.value.phase == ArchiveReaderPhase.READY) { userMovement = true; jumpTarget = null; pendingJump = null } }
    fun next(direction: Int) { armMovement(); if (direction > 0) host?.goForward(animated = false) else host?.goBackward(animated = false) }
    fun seek(fraction: Float) {
        if (!fraction.isFinite() || progress.state.value.phase != ArchiveReaderPhase.READY) return
        val opened = publication ?: return
        seekJob?.cancel()
        seekJob = scope.launch {
            try {
                val locator = withContext(Dispatchers.IO) { opened.locateProgression(fraction.coerceIn(0f, 1f).toDouble()) }
                ensureActive()
                if (!closing) navigate(requireNotNull(locator))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(notice = "이 위치를 계산하지 못했습니다. 현재 기록은 유지됩니다.") } }
        }
    }
    fun navigate(locator: Locator, rememberOrigin: Boolean = true) {
        if (closing || progress.state.value.phase !in setOf(ArchiveReaderPhase.READY, ArchiveReaderPhase.REFLOWING)) return
        try {
            validate(locator)
            if (rememberOrigin && returnTo == null) returnTo = initialLocator() ?: host?.currentLocator?.value
            captureJob?.cancel()
            armMovement()
            jumpTarget = locator
            pendingJump = locator
            if (progress.state.value.phase == ArchiveReaderPhase.REFLOWING) {
                // The old host is being removed. Let its replacement start at this request.
                mutableState.update { it.copy(hostVersion = it.hostVersion + 1, canReturn = returnTo != null) }
            } else {
                check(host?.go(locator, animated = false) == true)
                mutableState.update { it.copy(canReturn = returnTo != null) }
            }
        } catch (_: Exception) {
            pendingJump = null
            mutableState.update { it.copy(error = "이 위치로 이동할 수 없습니다. 현재 기록은 유지됩니다.") }
        }
    }
    fun returnFromJump() { val target = returnTo ?: return; returnTo = null; navigate(target, false); mutableState.update { it.copy(canReturn = false) } }
    fun beforeInternalLink() { if (returnTo == null) returnTo = initialLocator() ?: host?.currentLocator?.value; armMovement(); mutableState.update { it.copy(canReturn = returnTo != null) } }
    fun externalLink(url: AbsoluteUrl) {
        if (!url.isHttp) return
        if (url.toString().length <= 8192) mutableState.update { it.copy(externalUrl = url.toString()) }
        else mutableState.update { it.copy(notice = "링크가 너무 길어 열 수 없습니다.") }
    }
    fun externalOpenFailed() { mutableState.update { it.copy(notice = "브라우저를 열지 못했습니다. 독서는 계속할 수 있습니다.") } }
    fun dismissExternal() { mutableState.update { it.copy(externalUrl = null) } }
    fun fail(message: String) { progress.restorationFailed(); mutableState.update { it.copy(error = message) } }

    fun search(query: String) {
        if (query.isBlank() || query.length > 256 || closing) return
        val opened = publication ?: return
        val request = ++queryGeneration
        searchJob?.cancel()
        mutableState.update { it.copy(query = query, results = emptyList(), searching = true, searchError = false) }
        searchJob = scope.launch {
            try {
                val cursor = requireNotNull(opened.search(query))
                try {
                    while (true) {
                        val page = cursor.next().getOrElse { error("Search limit or unreadable resource") } ?: break
                        currentCoroutineContext().ensureActive()
                        if (request != queryGeneration) return@launch
                        mutableState.update { it.copy(results = (it.results + page.locators).take(1000)) }
                    }
                } finally { cursor.close() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (request == queryGeneration) mutableState.update { it.copy(searchError = true) } }
            finally { if (request == queryGeneration) mutableState.update { it.copy(searching = false) } }
        }
    }
    fun cancelSearch() { queryGeneration++; searchJob?.cancel(); mutableState.update { it.copy(searching = false) } }
    fun loadBookmarks(more: Boolean = false) {
        val cursor = if (more) state.value.bookmarkCursor ?: return else null
        scope.launch {
            when (val read = bookmarkAccess.load(book.id, requireNotNull(book.currentRevision), cursor)) {
                is BookmarkReadResult.Ready -> mutableState.update { it.copy(bookmarks = read.bookmarks,
                    bookmarkCursor = read.next, bookmarkError = false) }
                BookmarkReadResult.Error -> mutableState.update { it.copy(bookmarkError = true) }
            }
        }
    }
    fun addBookmark() {
        val session = progress.readySession() ?: return
        val locator = initialLocator() ?: host?.currentLocator?.value ?: return
        scope.launch {
            try {
                val result = bookmarkAccess.add(java.util.UUID.randomUUID().toString(), session, canonical(locator), null, System.currentTimeMillis())
                if (result == BookmarkWriteResult.COMMITTED) loadBookmarks() else mutableState.update { it.copy(bookmarkError = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(bookmarkError = true) } }
        }
    }
    fun deleteBookmark(id: String) { scope.launch {
        if (bookmarkAccess.delete(book.id, id) == BookmarkWriteResult.COMMITTED) loadBookmarks()
        else mutableState.update { it.copy(bookmarkError = true) }
    } }
    fun goBookmark(bookmark: Bookmark) {
        try { navigate(sdk(requireNotNull(bookmark.locator as? EpubLocator))) }
        catch (_: Exception) { mutableState.update { it.copy(bookmarkError = true) } }
    }
    fun saveSettings(value: ReaderSettings, bookPreferences: BookReaderPreferences) {
        val expected = settingsLoaded ?: return
        if (settingsJob?.isActive == true || closing || progress.state.value.phase != ArchiveReaderPhase.READY) return
        mutableState.update { it.copy(settingsSaving = true) }
        settingsJob = scope.launch {
            try {
                check(settingsAccess.save(expected, value, bookPreferences) == ReaderSettingsWrite.SAVED)
                val read = settingsAccess.load(book.id) as? ReaderSettingsRead.Ready ?: error("Settings unavailable")
                settingsLoaded = read.value
                progress.beginReflow()
                mutableState.update { it.copy(settings = read.value.effective, hostVersion = it.hostVersion + 1, settingsError = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(settingsError = true) } }
            finally { mutableState.update { it.copy(settingsSaving = false) } }
        }
    }
    private fun sdk(locator: EpubLocator): Locator = requireNotNull(Locator.fromJSON(JSONObject(locator.payload.locator.toString())))
    private fun canonical(locator: Locator): EpubLocator {
        validate(locator)
        return EpubLocator(requireNotNull(book.currentRevision), EpubLocatorPayload(locator = Json.parseToJsonElement(locator.toJSON().toString()).jsonObject))
    }
    private fun validate(locator: Locator) {
        require(locator.toJSON().toString().length <= 64 * 1024 && locator.href !is AbsoluteUrl)
        val href = locator.href.removeQuery().removeFragment()
        require(publication?.readingOrder?.any { it.url().removeQuery().removeFragment() == href } == true)
        require(locator.locations.progression?.let { it.isFinite() && it in 0.0..1.0 } != false)
        require(locator.locations.totalProgression?.let { it.isFinite() && it in 0.0..1.0 } != false)
    }
    suspend fun close() {
        if (closing) return
        closing = true
        seekJob?.cancel(); captureJob?.cancel(); restoreTimeout?.cancel()
        openJob.cancel(); searchJob?.cancel(); settingsJob?.cancel()
        detachHost?.invoke(); detachHost = null; host = null
        try { openJob.join(); searchJob?.join(); settingsJob?.join(); progress.close() }
        finally { ownedPublication.getAndSet(null)?.close(); lifetime.cancel() }
    }
}
