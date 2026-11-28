package org.bookreader.mobile.reader

import org.bookreader.mobile.R
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import org.bookreader.mobile.settings.TxtReadingMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun TxtReaderScreen(controller: TxtReaderController, onClose: () -> Unit) {
    // Capture one immutable generation for item count, keys and deferred item content.
    // Reading the delegated State inside LazyListScope can switch providers mid-measure.
    val state = controller.state.collectAsStateWithLifecycle().value
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var hasDisplayed by remember(controller) { mutableStateOf(false) }
    LaunchedEffect(state.phase) { if (state.phase == TxtReaderPhase.READY) hasDisplayed = true }
    var bodyViewport by remember(controller) { mutableStateOf(IntSize.Zero) }
    var viewport by remember(controller) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val settings = state.settings
    val foreground = settings.theme.inkColor()
    val background = settings.theme.paperColor()
    val typography = MaterialTheme.typography.bodyLarge.copy(
        color = foreground, fontFamily = settings.composeFamily(), fontWeight = androidx.compose.ui.text.font.FontWeight(settings.fontWeight.value),
        fontSize = settings.fontSizeSp.sp, lineHeight = (settings.fontSizeSp * settings.lineHeight).sp,
        letterSpacing = settings.letterSpacingEm.em, localeList = LocaleList.current,
        platformStyle = PlatformTextStyle(includeFontPadding = false), lineBreak = LineBreak.Simple, hyphens = Hyphens.None)
    val pageLabel = txtPageLabel(controller, state, typography, with(density) {
        IntSize((bodyViewport.width - settings.marginDp.dp.roundToPx() * 2).coerceAtLeast(0),
            (bodyViewport.height - 24.dp.roundToPx()).coerceAtLeast(0))
    })
    val scrollMode = settings.readingMode == TxtReadingMode.SCROLL
    var showSettings by remember(controller) { mutableStateOf(false) }
    var showMenu by remember(controller) { mutableStateOf(false) }
    var requestedScroll by remember(controller, state.restoreGeneration) { mutableStateOf(false) }
    var pageStep by remember(controller) { mutableStateOf(PageStepRequest()) }
    val dragOffsetPx = remember(controller) { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var pageAvailability by remember(controller) { mutableStateOf(false to false) }
    val seek = remember(controller) { ReaderSeekPreview(controller::seekToFraction) }
    val touchExploration = readerWindowEffects(settings.window)
    var controlsVisible by rememberSaveable(controller.book.id) { mutableStateOf(false) }
    val showChrome = controlsVisible || touchExploration || state.phase == TxtReaderPhase.ERROR
    var showSearch by remember(controller) { mutableStateOf(false) }
    var showBookmarks by remember(controller) { mutableStateOf(false) }
    var showToc by remember(controller) { mutableStateOf(false) }
    var showAnnotations by remember(controller) { mutableStateOf(false) }
    val annotationState = controller.annotations.state.collectAsStateWithLifecycle().value
    val highlights = if (annotationState.ready) annotationState.entries else emptyList()
    LaunchedEffect(controller, state.phase, state.cache.complete) { controller.annotations.load() }
    val toolsState = controller.tools.state.collectAsStateWithLifecycle().value
    val layouts = remember(controller, state.restoreGeneration, typography, density, viewport) {
        mutableStateMapOf<Int, TextLayoutResult>()
    }
    val previousTypography = remember(controller) { arrayOf(Triple(density.density, density.fontScale, typography)) }
    LaunchedEffect(density.density, density.fontScale, typography) {
        val changed = Triple(density.density, density.fontScale, typography)
        if (previousTypography[0] != changed) controller.beginReflow()
        previousTypography[0] = changed
    }
    fun move(direction: Int) {
        val phase = controller.state.value.phase
        if (scrollMode) {
            if (phase != TxtReaderPhase.READY) return
            scope.launch(Dispatchers.Main.immediate) {
                requestedScroll = true
                val distance = (list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset) * .9f * direction
                if (settings.pageTransition == org.bookreader.mobile.settings.PageTransition.NONE) list.scrollBy(distance)
                else list.animateScrollBy(distance)
            }
        } else {
            if (phase != TxtReaderPhase.READY && !(phase == TxtReaderPhase.REFLOWING && hasDisplayed)) return
            pageStep = PageStepRequest(pageStep.id + 1, direction)
        }
    }
    BackHandler {
        if (showMenu || seek.fraction != null) { seek.cancel(); showMenu = false }
        else if (controlsVisible) controlsVisible = false
        else onClose()
    }
    DisposableEffect(controller, lifecycle) {
        // A retained controller returning to a newly measured screen restores its canonical anchor.
        if (controller.state.value.phase == TxtReaderPhase.READY) controller.beginReflow()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) scope.launch(Dispatchers.Main.immediate) { controller.flush() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(controller, state.restoreGeneration, state.phase, scrollMode, typography, density, viewport) {
        if (!scrollMode) return@LaunchedEffect
        if (state.phase != TxtReaderPhase.RESTORING && state.phase != TxtReaderPhase.REFLOWING)
            return@LaunchedEffect
        val generation = state.restoreGeneration
        val target = state.targetOffset
        val index = controller.cache.fragmentFor(target)
        withContext(Dispatchers.Main.immediate) { list.scrollToItem(index) }
        val layout = snapshotFlow { layouts[index] }.first { it != null } ?: return@LaunchedEffect
        val fragment = state.cache.fragments[index]
        val canonicalRelative = (target - fragment.start).toInt()
        // UTF-16 locations cannot point into the low half of a surrogate pair.
        if (canonicalRelative in 1 until fragment.length && layout.layoutInput.text[canonicalRelative].isLowSurrogate() &&
            layout.layoutInput.text[canonicalRelative - 1].isHighSurrogate()) {
            controller.restoreFailed()
            return@LaunchedEffect
        }
        var relative = canonicalRelative.coerceIn(0, fragment.length - 1)
        if (relative > 0 && layout.layoutInput.text[relative].isLowSurrogate()) relative--
        val line = layout.getLineForOffset(relative)
        // A snapshot continuation may resume from the IO publisher under the test interceptor.
        // Every call that forces Android layout must explicitly return to the UI dispatcher.
        withContext(Dispatchers.Main.immediate) {
            list.scrollToItem(index, layout.getLineTop(line).roundToInt() - list.layoutInfo.viewportStartOffset)
        }
        // Wait for the list to apply that measured line position before allowing the writer.
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
        controller.restored(generation)
    }

    LaunchedEffect(controller, state.restoreGeneration, scrollMode, typography, density, viewport) {
        if (!scrollMode) return@LaunchedEffect
        var userMovement = false
        snapshotFlow {
            val listLayout = list.layoutInfo
            // Read placement and text measurement in the same snapshot. A measured
            // line can arrive after its item has already entered the viewport.
            val anchor = listLayout.visibleItemsInfo.firstOrNull()?.let { item ->
                layouts[item.index]?.let { layout ->
                    val vertical = (listLayout.viewportStartOffset - item.offset).coerceAtLeast(0).toFloat()
                    val line = layout.getLineForVerticalPosition(vertical)
                    item.index to layout.getLineStart(line)
                }
            }
            Triple(anchor, list.isScrollInProgress || requestedScroll, controller.state.value.phase)
        }.collect { (anchor, moving, phase) ->
            if (phase != TxtReaderPhase.READY) { userMovement = false; return@collect }
            if (moving) userMovement = true
            val (index, lineStart) = anchor ?: return@collect
            val fragment = controller.state.value.cache.fragments.getOrNull(index) ?: return@collect
            controller.visibleAnchor(fragment.start + lineStart, userMovement)
        }
    }

    val darkChrome = settings.theme.isDarkBackground()
    Box(Modifier.fillMaxSize().background(background).testTag("txt_reader")) {
        if (state.phase == TxtReaderPhase.ERROR) {
            ReaderErrorStateCard(
                formatLabel = "TXT",
                message = state.error.orEmpty(),
                errorTag = "reader_error",
                retryTag = "reader_retry",
                onRetry = controller::retry,
                onClose = onClose,
                darkTheme = darkChrome,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val gesturesEnabled = !touchExploration &&
                (state.phase == TxtReaderPhase.READY || (!scrollMode && state.phase == TxtReaderPhase.REFLOWING && hasDisplayed))
            Box(Modifier.fillMaxSize().padding(bottom = ReaderPageStatusHeight).onSizeChanged { bodyViewport = it }.readerGestures(
                enabled = gesturesEnabled,
                pageMode = !scrollMode,
                onTap = { fraction -> when {
                    fraction < .25f -> move(-1)
                    fraction > .75f -> move(1)
                    else -> controlsVisible = !controlsVisible
                } },
                onDoubleTap = { showMenu = !showMenu },
                onPage = ::move,
                onDragOffset = { dragOffsetPx.floatValue = if (!scrollMode) it else 0f },
            ).testTag(
                if (state.phase == TxtReaderPhase.READY) "reader_ready" else "reader_content")) {
                if (!scrollMode) TxtPageContent(
                    controller,
                    state,
                    typography,
                    Modifier.fillMaxSize().background(background).padding(horizontal = settings.marginDp.dp, vertical = 12.dp),
                    step = pageStep,
                    highlights = highlights,
                    dragOffsetPx = { dragOffsetPx.floatValue },
                    onAvailability = { previous, next -> pageAvailability = previous to next },
                )
                else LazyColumn(state = list, userScrollEnabled = state.phase == TxtReaderPhase.READY,
                    contentPadding = PaddingValues(horizontal = settings.marginDp.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxSize().testTag("reader_text")
                        .background(background)
                        .onSizeChanged { size ->
                            if (viewport != IntSize.Zero && viewport != size) controller.beginReflow()
                            viewport = size
                        }) {
                    items(count = state.cache.fragments.size, key = { it }) { index ->
                        val text by produceState<String?>(null, controller, index, state.restoreGeneration) {
                            withContext(Dispatchers.Main.immediate) {
                                try {
                                    value = controller.fragment(index)
                                }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { controller.contentReadFailed() }
                            }
                        }
                        if (text == null) {
                            // A zero-height unloaded item lets LazyColumn collapse a far seek back
                            // toward the old viewport before its asynchronous text arrives. Keep
                            // the requested item measurable and visible until its real layout exists.
                            Spacer(Modifier.fillMaxWidth().height(with(density) { maxOf(1, viewport.height).toDp() }))
                        } else {
                            key(state.restoreGeneration) {
                                // LazyColumn can place a prefetched item without another text layout.
                                DisposableEffect(layouts, index) {
                                    onDispose { layouts.remove(index) }
                                }
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(
                                        highlightedTxt(state.cache.fragments[index].start, requireNotNull(text), highlights),
                                        style = typography,
                                        onTextLayout = { result -> layouts[index] = result },
                                        modifier = Modifier.fillMaxWidth().testTag("reader_fragment_$index"),
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.phase != TxtReaderPhase.READY && !hasDisplayed) {
                    ReaderLoadingStateCard(
                        title = if (state.targetOffset > state.cache.committedLength) {
                            "저장된 위치까지 본문을 준비하는 중"
                        } else {
                            "독서 위치를 복원하는 중"
                        },
                        detail = "마지막으로 읽은 문단 위치를 유지합니다.",
                        darkTheme = darkChrome,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
        ReaderPageStatus(pageLabel, "reader", background, foreground)
        if (!showChrome && !hasDisplayed && state.phase != TxtReaderPhase.READY) ReaderLoadingExit("reader", onClose, Modifier.align(Alignment.TopStart), darkTheme = darkChrome)
        ReaderChromeOverlay(visible = showChrome, title = controller.book.title, format = if (scrollMode) "TXT · 스크롤" else "TXT · 페이지", prefix = "reader",
            progress = pageLabel,
            onClose = onClose, onHide = { controlsVisible = false }, canHide = !touchExploration,
            previous = if (scrollMode) list.canScrollBackward && state.phase == TxtReaderPhase.READY else pageAvailability.first,
            next = if (scrollMode) list.canScrollForward && state.phase == TxtReaderPhase.READY else pageAvailability.second,
            onPrevious = { move(-1) }, onNext = { move(1) }, onSeek = { showMenu = true }, darkTheme = darkChrome) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ReaderToolButton("목차", R.drawable.ic_reader_toc, "reader_toc",
                    state.phase == TxtReaderPhase.READY && state.cache.complete) { showToc = true }
                ReaderToolButton("검색", R.drawable.ic_reader_search, "reader_search",
                    state.phase == TxtReaderPhase.READY) { showSearch = true }
                ReaderToolButton("북마크", R.drawable.ic_reader_bookmark, "reader_bookmarks",
                    state.phase == TxtReaderPhase.READY) { showBookmarks = true }
                ReaderToolButton("설정", R.drawable.ic_reader_text, "reader_settings") { showSettings = true }
                ReaderToolButton("형광펜", R.drawable.ic_reader_highlight, "reader_annotations",
                    state.phase == TxtReaderPhase.READY && state.cache.complete) { showAnnotations = true }
                ReaderToolButton(
                    if (settings.window.fullscreen) "집중 해제" else "집중 모드",
                    R.drawable.ic_reader_focus,
                    "reader_focus_mode",
                    state.phase == TxtReaderPhase.READY && state.settingsLoaded != null && !state.settingsSaving && !state.settingsError,
                ) {
                    if (!settings.window.fullscreen) controlsVisible = false
                    controller.toggleFullscreen()
                }
                if (toolsState.returnOffset != null) ReaderToolButton("검색 전 위치", R.drawable.ic_reader_return,
                    "reader_search_return", state.phase == TxtReaderPhase.READY, controller.tools::returnFromSearch)
            }
        }
        val inlineAlerts = buildList {
            if (state.saveError) {
                add(ReaderInlineAlertEntry("진행도를 저장하지 못했습니다. 다시 시도합니다.", "reader_save_error"))
            }
            if (state.settingsError) {
                add(
                    ReaderInlineAlertEntry(
                        "읽기 설정 오류: 기존 설정은 보존됩니다. 설정에서 다시 읽어 주세요.",
                        "reader_settings_error",
                    ),
                )
            }
        }
        if (inlineAlerts.isNotEmpty()) {
            ReaderInlineAlertBanner(
                entries = inlineAlerts,
                darkTheme = darkChrome,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
    if (showSettings) ReaderControlTheme(darkTheme = settings.theme.isDarkBackground()) { ReaderSettingsDialog(controller, state) { showSettings = false } }
    if (showSearch) ReaderSearchDialog(controller) { showSearch = false }
    if (showBookmarks) ReaderBookmarksDialog(controller) { showBookmarks = false }
    if (showToc) ReaderTocDialog(controller) { showToc = false }
    if (showAnnotations) ReaderAnnotationsDialog(controller) { showAnnotations = false }
    if (showMenu) ReaderMenuDialog(state, seek,
        previous = if (scrollMode) list.canScrollBackward && state.phase == TxtReaderPhase.READY else pageAvailability.first,
        next = if (scrollMode) list.canScrollForward && state.phase == TxtReaderPhase.READY else pageAvailability.second,
        onPrevious = { move(-1) }, onNext = { move(1) },
        onSearch = { showMenu = false; showSearch = true },
        onBookmarks = { showMenu = false; showBookmarks = true },
        onSettings = { showMenu = false; showSettings = true }, onExit = onClose,
        onDismiss = { seek.cancel(); showMenu = false })
}
