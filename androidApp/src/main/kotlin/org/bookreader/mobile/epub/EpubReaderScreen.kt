package org.bookreader.mobile.epub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bookreader.mobile.R
import org.bookreader.mobile.reader.*

@Composable
fun EpubReaderScreen(controller: EpubReaderController, onClose: () -> Unit, onRetry: () -> Unit) {
    val state = controller.state.collectAsStateWithLifecycle().value
    val progress = controller.progress.state.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val exploration = readerWindowEffects(state.settings.window)
    var controlsVisible by rememberSaveable(controller.book.id) { mutableStateOf(false) }
    val chrome = controlsVisible || exploration
    var menu by remember(controller) { mutableStateOf(false) }
    val seek = remember(controller) { ReaderSeekPreview(controller::seek) }
    var settings by remember(controller) { mutableStateOf(false) }
    var search by remember(controller) { mutableStateOf(false) }
    var bookmarks by remember(controller) { mutableStateOf(false) }
    var toc by remember(controller) { mutableStateOf(false) }
    var viewport by remember(controller) { mutableStateOf(IntSize.Zero) }
    val ready = progress.phase == ArchiveReaderPhase.READY
    LaunchedEffect(controller, ready, state.hostVersion, state.currentPosition) {
        if (ready) {
            controller.annotations.load()
            controller.annotations.render()
        }
    }
    val pageLabel = if (state.currentPosition != null && state.totalPositions != null)
        "${state.currentPosition} / ${state.totalPositions} 쪽 · EPUB 위치 기준" else "쪽수 계산 중"
    BackHandler {
        when { settings -> settings = false; search -> { search = false; controller.cancelSearch() }
            bookmarks -> bookmarks = false; toc -> toc = false; menu -> menu = false; controlsVisible -> controlsVisible = false; else -> onClose() }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) scope.launch(Dispatchers.Main.immediate) { controller.progress.flush() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.cancelSearch() }
    }
    @Composable fun Controls() {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ReaderToolButton("목차", R.drawable.ic_reader_toc, "epub_toc", ready) { toc = true; menu = false }
            ReaderToolButton("검색", R.drawable.ic_reader_search, "epub_search", ready) { search = true; menu = false }
            ReaderToolButton("북마크", R.drawable.ic_reader_bookmark, "epub_bookmarks", ready) { bookmarks = true; menu = false; controller.loadBookmarks() }
            ReaderToolButton("설정", R.drawable.ic_reader_text, "epub_settings", ready) { settings = true; menu = false }
            ReaderToolButton("형광펜", R.drawable.ic_reader_highlight, "epub_annotations", ready) { controller.annotations.open(); menu = false }
        }
    }
    val darkChrome = state.settings.theme.isDarkBackground()
    Box(Modifier.fillMaxSize().background(state.settings.theme.paperColor()).testTag("epub_reader")) {
        if (state.error != null || progress.phase == ArchiveReaderPhase.ERROR) {
            ReaderErrorStateCard(
                formatLabel = "EPUB",
                message = state.error ?: "저장된 독서 위치를 읽거나 복원하지 못했습니다. 기존 기록은 보존됩니다.",
                errorTag = "epub_error",
                retryTag = "epub_retry",
                onRetry = onRetry,
                onClose = onClose,
                darkTheme = darkChrome,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().padding(bottom = ReaderPageStatusHeight).onSizeChanged {
                if (viewport != IntSize.Zero && viewport != it) controller.reflowViewport()
                viewport = it
            }.testTag(if (ready) "epub_ready" else "epub_loading")) {
                if (state.opened) key(controller, state.hostVersion) { AndroidFragment<EpubHostFragment>(Modifier.fillMaxSize(), onUpdate = { fragment ->
                    fragment.onCenterTap = { controlsVisible = !controlsVisible }
                    fragment.touchExploration = exploration
                }) }
                if (!ready) {
                    ReaderLoadingStateCard(
                        title = "EPUB 본문과 독서 위치를 준비하는 중",
                        detail = "기존 독서 기록과 서식 설정을 적용하고 있습니다.",
                        darkTheme = darkChrome,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
        ReaderPageStatus(pageLabel, "epub", state.settings.theme.paperColor(), state.settings.theme.inkColor())
        if (!chrome && !ready) ReaderLoadingExit("epub", onClose, Modifier.align(Alignment.TopStart), darkTheme = darkChrome)
        ReaderChromeOverlay(visible = chrome, title = controller.book.title, format = "EPUB", prefix = "epub",
            progress = pageLabel,
            onClose = onClose, onHide = { controlsVisible = false }, canHide = !exploration, previous = ready, next = ready,
            onPrevious = { controller.next(-1) }, onNext = { controller.next(1) },
            onSeek = { menu = true }, darkTheme = darkChrome) {
            Controls()
            if (state.canReturn) ReaderToolButton("이전 위치", R.drawable.ic_reader_return,
                "epub_return", ready, controller::returnFromJump)
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (progress.saveError) {
            ReaderInlineAlertBanner(
                entries = listOf(
                    ReaderInlineAlertEntry(
                        "독서 위치를 저장하지 못했습니다. 마지막 저장 기록은 유지됩니다.",
                        "epub_save_error",
                    ),
                ),
                darkTheme = darkChrome,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
    ReaderControlTheme(darkTheme = darkChrome) { EpubAnnotationsDialog(controller) }
    if (menu) {
        val current = (controller.initialLocator()?.locations?.totalProgression ?: 0.0).toFloat()
        EpubMenuDialog(
            state = state,
            currentFraction = current,
            ready = ready,
            seek = seek,
            onToc = { toc = true; menu = false },
            onSearch = { search = true; menu = false },
            onBookmarks = { bookmarks = true; menu = false; controller.loadBookmarks() },
            onSettings = { settings = true; menu = false },
            onAnnotations = { controller.annotations.open(); menu = false },
            onDismiss = { menu = false },
        )
    }
    if (settings) ReaderControlTheme(darkTheme = state.settings.theme.isDarkBackground()) { ReaderSettingsDialog(TxtReaderState(phase = if (ready) TxtReaderPhase.READY else TxtReaderPhase.REFLOWING, settings = state.settings, settingsLoaded = controller.loadedSettings,
        settingsError = state.settingsError, settingsSaving = state.settingsSaving), controller::retrySettings, controller::saveSettings, epubMode = true) { settings = false } }
    if (search) {
        EpubSearchDialog(controller = controller, state = state) {
            search = false
            controller.cancelSearch()
        }
    }
    if (toc) {
        EpubTocDialog(controller = controller, theme = state.settings.theme) { toc = false }
    }
    if (bookmarks) {
        EpubBookmarksDialog(controller = controller, state = state) { bookmarks = false }
    }
    state.externalUrl?.let { url ->
        EpubExternalLinkDialog(
            url = url,
            theme = state.settings.theme,
            onOpenFailed = controller::externalOpenFailed,
            onDismiss = controller::dismissExternal,
        )
    }
}
