package org.bookreader.mobile.comic

import org.bookreader.mobile.R

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.bookreader.mobile.reader.*
import org.bookreader.mobile.settings.*
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ComicReaderScreen(controller: ComicReaderController, onClose: () -> Unit, onRetry: () -> Unit) {
    val state = controller.state.collectAsStateWithLifecycle().value
    val progress = controller.progress.state.collectAsStateWithLifecycle().value
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var zoomRequest by remember(controller) { mutableIntStateOf(0) }
    val seek = remember(controller) { ReaderSeekPreview(controller::seek) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val exploration = readerWindowEffects(state.settings.window)
    var controlsVisible by rememberSaveable(controller.book.id) { mutableStateOf(false) }
    val chrome = controlsVisible || exploration
    val ready = progress.phase == ArchiveReaderPhase.READY
    val pageLabel = state.manifest?.let { "${state.index + 1} / ${it.entryKeys.size} 쪽" } ?: "쪽수 계산 중"
    BackHandler { when { settings -> settings = false; bookmarks -> bookmarks = false; menu -> menu = false; controlsVisible -> controlsVisible = false; else -> onClose() } }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) scope.launch { controller.progress.flush() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    @Composable fun Controls() {
        ReaderToolButton("북마크", R.drawable.ic_reader_bookmark, "comic_bookmarks", ready) { bookmarks = true; menu = false; controller.loadBookmarks() }
        ReaderToolButton("읽기 설정", R.drawable.ic_reader_settings, "comic_settings", ready) { settings = true; menu = false }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101720)).testTag("comic_reader")) {
        if (state.fatalError || progress.phase == ArchiveReaderPhase.ERROR) {
            ReaderErrorStateCard(
                formatLabel = "만화",
                message = "압축파일 또는 저장 위치를 열지 못했습니다. 기존 기록은 보존됩니다.",
                errorTag = "comic_error",
                onRetry = onRetry,
                onClose = onClose,
                darkTheme = true,
                modifier = Modifier.fillMaxSize(),
            )
        } else Box(Modifier.fillMaxSize().padding(bottom = ReaderPageStatusHeight).onSizeChanged {
            if (viewport != IntSize.Zero && viewport != it) controller.reflow()
            viewport = it
        }.testTag(if (ready) "comic_ready" else "comic_loading")) {
            val manifest = state.manifest
            if (manifest != null && viewport.width > 0 && viewport.height > 0) {
                if (state.settings.readingMode == TxtReadingMode.SCROLL) key(controller, state.hostVersion) {
                    ComicContinuous(controller, manifest, viewport, exploration, zoomRequest, { zoomRequest = 0 }) { controlsVisible = !controlsVisible }
                } else ComicSinglePage(controller, viewport, exploration) { controlsVisible = !controlsVisible }
            } else {
                ReaderLoadingStateCard(
                    title = "만화 페이지를 준비하는 중",
                    detail = "압축 목록과 현재 페이지를 불러오고 있습니다.",
                    darkTheme = true,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        ReaderPageStatus(pageLabel, "comic", Color(0xFF101720), Color(0xFFE8E8E8))
        if (!chrome && !ready) ReaderLoadingExit("comic", onClose, Modifier.align(Alignment.TopStart), darkTheme = true)
        ReaderChromeOverlay(visible = chrome, title = controller.book.title, format = if (state.settings.readingMode == TxtReadingMode.SCROLL) "COMIC · 연속" else "COMIC · 한 장", prefix = "comic",
            progress = pageLabel, onClose = onClose, onHide = { controlsVisible = false }, canHide = !exploration,
            previous = ready && state.index > 0, next = ready && state.index + 1 < (state.manifest?.entryKeys?.size ?: 0),
            onPrevious = { controller.move(-1) }, onNext = { controller.move(1) },
            onSeek = { menu = true }, darkTheme = true) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Controls()
                if (state.settings.readingMode == TxtReadingMode.SCROLL) ReaderToolButton("확대", R.drawable.ic_reader_zoom,
                    "comic_enlarge_${state.manifest?.entryKeys?.getOrNull(state.index)}", ready) { zoomRequest++ }
                if (state.canReturn) ReaderToolButton("이전 위치", R.drawable.ic_reader_return,
                    "comic_return", ready, controller::returnFromJump)
            }
            if (state.manifest?.legacyNames == true) Text("레거시 ZIP 이름을 고정 규칙으로 표시합니다.", style = MaterialTheme.typography.bodySmall)
        }
        if (progress.saveError) {
            ReaderInlineAlertBanner(
                entries = listOf(ReaderInlineAlertEntry("위치 저장에 실패했습니다. 마지막 저장 기록을 유지합니다.")),
                darkTheme = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
    if (menu) {
        ComicMenuDialog(
            state = state,
            ready = ready,
            seek = seek,
            onSettings = { settings = true; menu = false },
            onBookmarks = { bookmarks = true; menu = false; controller.loadBookmarks() },
            onDismiss = { menu = false },
        )
    }
    if (settings) ComicSettingsDialog(controller) { settings = false }
    if (bookmarks) {
        ComicBookmarksDialog(controller = controller, state = state) { bookmarks = false }
    }
}

@Composable
private fun ComicContinuous(controller: ComicReaderController, manifest: ComicManifest, viewport: IntSize,
    exploration: Boolean, zoomRequest: Int, onZoomConsumed: () -> Unit, onMenu: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = controller.state.collectAsStateWithLifecycle().value
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index)
    var requestedScroll by remember { mutableStateOf(false) }
    val loaded = remember { mutableStateMapOf<Int, Int>() }
    val density = LocalDensity.current
    val targetIndex = remember { state.index }
    val anchor = remember { controller.anchor() }
    val generation = remember { controller.progress.state.value.generation }
    LaunchedEffect(controller) {
        val height = snapshotFlow { loaded[targetIndex] }.first { it != null } ?: return@LaunchedEffect
        withContext(Dispatchers.Main.immediate) { list.scrollToItem(targetIndex, ((anchor?.normalizedY ?: 0.0) * height).roundToInt()) }
        withFrameNanos { }
        controller.renderReady(manifest.entryKeys[targetIndex], generation)
    }
    LaunchedEffect(controller, list) {
        var moving = false
        snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress || requestedScroll) }.collect { (index, offset, scrolling) ->
            if (scrolling) moving = true
            val height = loaded[index] ?: return@collect
            if (moving) controller.visible(manifest.entryKeys[index], .5, offset.toDouble() / height.coerceAtLeast(1), true)
        }
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("comic_continuous"),
        verticalArrangement = Arrangement.spacedBy(state.settings.comicGapDp.dp)) {
        itemsIndexed(manifest.entryKeys, key = { _, key -> key }) { index, entry ->
            ComicPage(controller, entry, viewport, true, exploration, onReady = {}, onMenu = onMenu,
                zoomRequest = if (index == state.index) zoomRequest else 0, onZoomConsumed = onZoomConsumed, onPageTap = { fraction ->
                    if (fraction < .25f || fraction > .75f) scope.launch {
                        requestedScroll = true
                        val distance = viewport.height * .9f * if (fraction < .25f) -1 else 1
                        if (state.settings.pageTransition == PageTransition.NONE) list.scrollBy(distance) else list.animateScrollBy(distance)
                    } else onMenu()
                },
                onHeight = { height -> loaded[index] = height }, onRelease = { loaded.remove(index) })
        }
    }
}

private data class ComicFrame(val image: ComicImage, val index: Int, val generation: Int)

/** Keep the last decoded image alive until the replacement has been decoded and drawn. */
@Composable
private fun ComicSinglePage(controller: ComicReaderController, viewport: IntSize, exploration: Boolean, onMenu: () -> Unit) {
    val state = controller.state.collectAsStateWithLifecycle().value
    val progress = controller.progress.state.collectAsStateWithLifecycle().value
    val entry = state.manifest?.entryKeys?.getOrNull(state.index) ?: return
    var displayed by remember(controller) { mutableStateOf<ComicFrame?>(null) }
    var failed by remember(entry) { mutableStateOf(false) }
    var attempt by remember(entry) { mutableIntStateOf(0) }
    var direction by remember(controller) { mutableIntStateOf(1) }
    val leases = remember(controller) { mutableSetOf<ComicImage>() }
    DisposableEffect(controller) { onDispose { leases.forEach { it.close() }; leases.clear() } }
    LaunchedEffect(entry, viewport, state.hostVersion, attempt) {
        val generation = controller.progress.state.value.generation
        var decoded: ComicImage? = null
        try {
            failed = false
            decoded = controller.decode(entry, viewport.width, viewport.height)
            ensureActive()
            val old = displayed
            direction = (if (old != null && old.index > state.index) -1 else 1) *
                (if (state.settings.comicDirection == ComicDirection.RTL) -1 else 1)
            leases.add(decoded)
            displayed = ComicFrame(decoded, state.index, generation)
            decoded = null // The composition/transition now owns this lease.
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { decoded?.close() }
    }
    Box(Modifier.fillMaxSize().testTag("comic_page")) {
        displayed?.let { target ->
            ReaderPageTransition(target, state.settings.pageTransition, direction, Modifier.fillMaxSize(),
                onSettled = { controller.renderReady(it.image.entryKey, it.generation) }) { frame ->
                DisposableEffect(frame.image) { onDispose { leases.remove(frame.image); frame.image.close() } }
                Box(Modifier.fillMaxSize().background(Color(0xFF101720))) {
                    ComicCanvas(controller, frame.image, viewport, false,
                        exploration || frame !== target || progress.phase != ArchiveReaderPhase.READY, onMenu)
                    if (frame.image.limitedResolution) {
                        ReaderResolutionHintBanner(
                            message = "메모리 보호를 위해 해상도를 낮춰 표시합니다.",
                            tag = "comic_resolution_limit",
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        if (failed) {
            ComicImageErrorCard(
                canSkip = state.index + 1 < (state.manifest?.entryKeys?.size ?: 0),
                onRetry = { attempt++ },
                onSkip = { controller.move(1) },
                entryLabel = entry.substringAfterLast('/'),
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (displayed == null) {
            ReaderLoadingStateCard(
                title = "이미지를 불러오는 중",
                darkTheme = true,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ComicPage(controller: ComicReaderController, entry: String, viewport: IntSize, continuous: Boolean,
    exploration: Boolean, onReady: (String) -> Unit, onMenu: () -> Unit,
    zoomRequest: Int = 0, onZoomConsumed: () -> Unit = {}, onPageTap: (Float) -> Unit = {},
    onHeight: (Int) -> Unit = {}, onRelease: () -> Unit = {}) {
    val state = controller.state.collectAsStateWithLifecycle().value
    var image by remember(entry) { mutableStateOf<ComicImage?>(null) }
    var failed by remember(entry) { mutableStateOf(false) }
    var attempt by remember(entry) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val leases = remember(entry) { mutableSetOf<ComicImage>() }
    DisposableEffect(leases) { onDispose { leases.forEach { it.close() }; leases.clear() } }
    DisposableEffect(entry) { onDispose { onRelease() } }
    LaunchedEffect(controller, entry, viewport, attempt) {
        var owned: ComicImage? = null
        try {
            failed = false
            owned = controller.decode(entry, viewport.width, viewport.height)
            leases.add(owned)
            image = owned
            awaitCancellation()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { image = null }
    }
    val current = image
    var enlarged by remember(entry) { mutableStateOf(false) }
    LaunchedEffect(zoomRequest) { if (zoomRequest > 0) { enlarged = true; onZoomConsumed() } }
    DisposableEffect(current) { onDispose { current?.let { leases.remove(it); it.close() } } }
    val pageHeight = (if (!continuous || current == null) viewport.height.toFloat() else when (state.settings.comicFit) {
        ComicFit.WIDTH -> viewport.width.toFloat() * current.bitmap.height / current.bitmap.width
        ComicFit.HEIGHT -> viewport.height.toFloat()
        ComicFit.CONTAIN -> minOf(viewport.height.toFloat(), viewport.width.toFloat() * current.bitmap.height / current.bitmap.width)
    }).coerceIn(1f, 100000f)
    Box(Modifier.fillMaxWidth().height(with(density) { pageHeight.coerceIn(1f, 100000f).toDp() })
        .onSizeChanged { if (current != null) onHeight(it.height) }.testTag("comic_page")) {
        if (failed) {
            ComicImageErrorCard(
                canSkip = state.index + 1 < (state.manifest?.entryKeys?.size ?: 0),
                onRetry = { attempt++ },
                onSkip = { controller.move(1) },
                entryLabel = entry.substringAfterLast('/'),
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (current == null) {
            ReaderLoadingStateCard(
                title = "이미지를 불러오는 중",
                darkTheme = true,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LaunchedEffect(current, pageHeight) { withFrameNanos { }; onHeight(pageHeight.roundToInt()); onReady(entry) }
            ComicCanvas(controller, current, IntSize(viewport.width, pageHeight.roundToInt().coerceAtLeast(1)), continuous, exploration, onMenu,
                onContinuousTap = onPageTap, onEnlarge = { enlarged = true })
            if (enlarged) {
                ComicZoomOverlayDialog(
                    entryLabel = entry.substringAfterLast('/'),
                    onDismiss = { enlarged = false; controller.reflow() },
                ) {
                    ComicCanvas(controller, current, viewport, false, exploration, {}, navigation = false)
                }
            }
            if (current.limitedResolution) {
                ReaderResolutionHintBanner(
                    message = "메모리 보호를 위해 해상도를 낮춰 표시합니다.",
                    tag = "comic_resolution_limit",
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun ComicCanvas(controller: ComicReaderController, image: ComicImage, viewport: IntSize,
    continuous: Boolean, exploration: Boolean, onMenu: () -> Unit, navigation: Boolean = true,
    onContinuousTap: (Float) -> Unit = {}, onEnlarge: () -> Unit = {}) {
    val readerState = controller.state.collectAsStateWithLifecycle().value
    val settings = readerState.settings
    var canvasViewport by remember(viewport) { mutableStateOf(viewport) }
    val bitmap = remember(image) { image.bitmap.asImageBitmap() }
    val requestedBase = when (settings.comicFit) {
        ComicFit.WIDTH -> canvasViewport.width.toFloat() / bitmap.width
        ComicFit.HEIGHT -> canvasViewport.height.toFloat() / bitmap.height
        ComicFit.CONTAIN -> minOf(canvasViewport.width.toFloat() / bitmap.width, canvasViewport.height.toFloat() / bitmap.height)
    }
    // Very tall continuous pages use a complete scaled image within the layout height cap.
    // Never clip away the tail merely to keep LazyColumn dimensions bounded.
    val base = if (continuous) minOf(requestedBase, canvasViewport.height.toFloat() / bitmap.height) else requestedBase
    var zoom by remember(image, canvasViewport) { mutableFloatStateOf(1f) }
    fun clamp(position: Offset, scale: Float): Offset {
        val w = bitmap.width * base * scale; val h = bitmap.height * base * scale
        return Offset(if (w <= canvasViewport.width) (canvasViewport.width - w) / 2 else position.x.coerceIn(canvasViewport.width - w, 0f),
            if (h <= canvasViewport.height) (canvasViewport.height - h) / 2 else position.y.coerceIn(canvasViewport.height - h, 0f))
    }
    var origin by remember(image, canvasViewport) { mutableStateOf(clamp(Offset(
        -((controller.anchor()?.normalizedX ?: 0.0) * bitmap.width * base).toFloat(),
        -((controller.anchor()?.normalizedY ?: 0.0) * bitmap.height * base).toFloat()), 1f)) }
    fun save() { controller.visible(image.entryKey, (-origin.x / (bitmap.width * base * zoom)).toDouble(),
        (-origin.y / (bitmap.height * base * zoom)).toDouble(), true) }
    val direction by rememberUpdatedState(if (settings.comicDirection == ComicDirection.LTR) 1 else -1)
    val menuTap by rememberUpdatedState(onMenu)
    val continuousTap by rememberUpdatedState(onContinuousTap)
    val enlarge by rememberUpdatedState(onEnlarge)
    var modifier = Modifier.fillMaxSize().onSizeChanged { canvasViewport = it }.clipToBounds().semantics { contentDescription = "만화 이미지" }.testTag("comic_image")
    if (!continuous && !exploration) modifier = modifier.pointerInput(image) {
        detectTapGestures(onDoubleTap = { point ->
            val next = if (zoom > 1f) 1f else 2.5f
            origin = clamp(point - (point - origin) * (next / zoom), next); zoom = next; save()
        }, onTap = { point -> if (navigation) when {
            zoom > 1f -> menuTap()
            point.x < size.width * .25f -> controller.move(-direction)
            point.x > size.width * .75f -> controller.move(direction)
            else -> menuTap()
        } })
    }.pointerInput(image) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var multiple = false; var total = Offset.Zero; var transformed = false
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } > 1) multiple = true
                val pan = event.calculatePan(); val scale = event.calculateZoom()
                total += pan
                if (multiple || zoom > 1f || abs(total.y) > viewConfiguration.touchSlop && abs(total.y) > abs(total.x)) {
                    if (pan != Offset.Zero || scale != 1f) {
                        val next = (zoom * scale).coerceIn(1f, 6f)
                        val center = event.calculateCentroid(useCurrent = false)
                        origin = clamp(center - (center - origin) * (next / zoom) + pan, next)
                        zoom = next; transformed = true
                        event.changes.forEach { it.consume() }
                        save()
                    }
                }
            } while (event.changes.any { it.pressed })
            if (navigation && !multiple && !transformed && zoom == 1f && abs(total.x) > 64.dp.toPx() && abs(total.x) > abs(total.y))
                controller.move(if (total.x < 0) direction else -direction)
        }
    }
    if (continuous && !exploration) modifier = modifier.pointerInput(image) {
        detectTapGestures(onTap = { continuousTap(it.x / size.width.coerceAtLeast(1)) }, onDoubleTap = { enlarge() })
    }
    Canvas(modifier) {
        drawImage(bitmap, dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
            dstSize = IntSize((bitmap.width * base * zoom).roundToInt().coerceAtLeast(1), (bitmap.height * base * zoom).roundToInt().coerceAtLeast(1)))
    }
}
