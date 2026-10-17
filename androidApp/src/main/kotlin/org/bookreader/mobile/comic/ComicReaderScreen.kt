package org.bookreader.mobile.comic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val seek = remember(controller) { ReaderSeekPreview(controller::seek) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val exploration = readerWindowEffects(state.settings.window)
    val chrome = !state.settings.window.fullscreen || exploration
    val ready = progress.phase == ArchiveReaderPhase.READY
    BackHandler { when { settings -> settings = false; bookmarks -> bookmarks = false; menu -> menu = false; else -> onClose() } }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) scope.launch { controller.progress.flush() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    @Composable fun Controls() { FlowRow {
        TextButton(onClick = onClose, modifier = Modifier.testTag("comic_back")) { Text("서재") }
        TextButton(onClick = { settings = true; menu = false }, enabled = ready, modifier = Modifier.testTag("comic_settings")) { Text("설정") }
        TextButton(onClick = { bookmarks = true; menu = false; controller.loadBookmarks() }, enabled = ready, modifier = Modifier.testTag("comic_bookmarks")) { Text("북마크") }
    } }
    Column(Modifier.fillMaxSize().testTag("comic_reader")) {
        if (chrome) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(controller.book.title, Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { menu = true }, modifier = Modifier.testTag("comic_menu")) { Text("메뉴") }
            }
            Controls()
        }
        if (state.fatalError || progress.phase == ArchiveReaderPhase.ERROR) {
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text("압축파일 또는 저장 위치를 열지 못했습니다. 기존 기록은 보존됩니다.", Modifier.testTag("comic_error"))
                Button(onClick = onRetry) { Text("다시 시도") }
                TextButton(onClick = onClose) { Text("서재로") }
            }
        } else Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged {
            if (viewport != IntSize.Zero && viewport != it) controller.reflow()
            viewport = it
        }.testTag(if (ready) "comic_ready" else "comic_loading")) {
            val manifest = state.manifest
            if (manifest != null && viewport.width > 0 && viewport.height > 0) key(controller, state.hostVersion) {
                if (state.settings.readingMode == TxtReadingMode.SCROLL)
                    ComicContinuous(controller, manifest, viewport, exploration)
                else ComicPage(controller, manifest.entryKeys[state.index], viewport, false, exploration,
                    onReady = { controller.renderReady(it, controller.progress.state.value.generation) }, onMenu = { menu = true })
            } else CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (!chrome) TextButton(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopEnd)) { Text("메뉴") }
        }
        if (state.manifest?.legacyNames == true) Text("레거시 ZIP 이름을 고정 규칙으로 표시합니다. 다른 인코딩으로 이름을 추정하지 않습니다.")
        if (progress.saveError) Text("위치 저장에 실패했습니다. 마지막 저장 기록을 유지합니다.")
        if (chrome) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { controller.move(-1) }, enabled = state.index > 0, modifier = Modifier.testTag("comic_previous")) { Text("이전") }
            Text("${state.index + 1} / ${state.manifest?.entryKeys?.size ?: 0}")
            if (state.canReturn) TextButton(onClick = controller::returnFromJump, enabled = ready, modifier = Modifier.testTag("comic_return")) { Text("이전 위치로") }
            TextButton(onClick = { controller.move(1) }, enabled = state.index + 1 < (state.manifest?.entryKeys?.size ?: 0), modifier = Modifier.testTag("comic_next")) { Text("다음") }
        }
    }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text("독서 메뉴") }, text = {
        Column {
            val count = state.manifest?.entryKeys?.size ?: 0
            val current = if (count > 1) state.index.toFloat() / (count - 1) else 0f
            Text("${if (seek.fraction == null) "현재" else "이동 미리보기"} ${((seek.fraction ?: current) * (count - 1).coerceAtLeast(0)).toInt() + 1} / $count")
            ReaderPositionSlider(current, ready && count > 1, seek, "comic_seek")
            Controls()
        }
    }, confirmButton = { TextButton(onClick = { menu = false }, modifier = Modifier.testTag("comic_menu_close")) { Text("닫기 / 이동 취소") } })
    if (settings) ComicSettingsDialog(controller) { settings = false }
    if (bookmarks) AlertDialog(onDismissRequest = { bookmarks = false }, title = { Text("북마크") }, text = {
        Column {
            TextButton(onClick = controller::addBookmark, modifier = Modifier.testTag("comic_bookmark_add")) { Text("현재 위치 추가") }
            if (state.bookmarkError) Text("북마크 오류입니다. 기존 기록은 유지됩니다.")
            LazyColumn(Modifier.heightIn(max = 300.dp)) { itemsIndexed(state.bookmarks, key = { _, it -> it.id }) { _, bookmark ->
                Row {
                    TextButton(onClick = { controller.goBookmark(bookmark); bookmarks = false }, modifier = Modifier.weight(1f).testTag("comic_bookmark_item")) { Text(bookmark.label ?: "저장한 위치") }
                    TextButton(onClick = { controller.deleteBookmark(bookmark.id) }) { Text("삭제") }
                }
            } }
            if (state.bookmarkCursor != null) TextButton(onClick = { controller.loadBookmarks(true) }) { Text("다음 50개") }
            TextButton(onClick = { controller.loadBookmarks() }) { Text("처음 / 다시 읽기") }
        }
    }, confirmButton = { TextButton(onClick = { bookmarks = false }) { Text("닫기") } })
}

@Composable
private fun ComicContinuous(controller: ComicReaderController, manifest: ComicManifest, viewport: IntSize, exploration: Boolean) {
    val state = controller.state.collectAsStateWithLifecycle().value
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index)
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
        snapshotFlow { Triple(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.isScrollInProgress) }.collect { (index, offset, scrolling) ->
            if (scrolling) moving = true
            val height = loaded[index] ?: return@collect
            if (moving) controller.visible(manifest.entryKeys[index], .5, offset.toDouble() / height.coerceAtLeast(1), true)
        }
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("comic_continuous"),
        verticalArrangement = Arrangement.spacedBy(state.settings.comicGapDp.dp)) {
        itemsIndexed(manifest.entryKeys, key = { _, key -> key }) { index, entry ->
            ComicPage(controller, entry, viewport, true, exploration, onReady = {}, onMenu = {},
                onHeight = { height -> loaded[index] = height }, onRelease = { loaded.remove(index) })
        }
    }
}

@Composable
private fun ComicPage(controller: ComicReaderController, entry: String, viewport: IntSize, continuous: Boolean,
    exploration: Boolean, onReady: (String) -> Unit, onMenu: () -> Unit,
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
    DisposableEffect(current) { onDispose { current?.let { leases.remove(it); it.close() } } }
    val pageHeight = (if (!continuous || current == null) viewport.height.toFloat() else when (state.settings.comicFit) {
        ComicFit.WIDTH -> viewport.width.toFloat() * current.bitmap.height / current.bitmap.width
        ComicFit.HEIGHT -> viewport.height.toFloat()
        ComicFit.CONTAIN -> minOf(viewport.height.toFloat(), viewport.width.toFloat() * current.bitmap.height / current.bitmap.width)
    }).coerceIn(1f, 100000f)
    Box(Modifier.fillMaxWidth().height(with(density) { pageHeight.coerceIn(1f, 100000f).toDp() })
        .onSizeChanged { if (current != null) onHeight(it.height) }.testTag("comic_page")) {
        if (failed) Column(Modifier.align(Alignment.Center).padding(16.dp)) {
            Text("이미지를 표시하지 못했습니다. 원본과 다른 페이지는 유지됩니다.", Modifier.testTag("comic_image_error"))
            TextButton(onClick = { attempt++ }) { Text("다시 시도") }
            TextButton(onClick = { controller.move(1) }, enabled = state.index + 1 < (state.manifest?.entryKeys?.size ?: 0), modifier = Modifier.testTag("comic_skip")) { Text("다음 이미지로 건너뛰기") }
        } else if (current == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
        else {
            LaunchedEffect(current, pageHeight) { withFrameNanos { }; onHeight(pageHeight.roundToInt()); onReady(entry) }
            ComicCanvas(controller, current, IntSize(viewport.width, pageHeight.roundToInt().coerceAtLeast(1)), continuous, exploration, onMenu)
            if (continuous) TextButton(onClick = { enlarged = true }, modifier = Modifier.align(Alignment.TopEnd).testTag("comic_enlarge_$entry")) { Text("확대") }
            if (enlarged) Dialog(onDismissRequest = { enlarged = false; controller.reflow() },
                properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxSize().testTag("comic_zoom_dialog")) {
                    Column {
                        TextButton(onClick = { enlarged = false; controller.reflow() }, modifier = Modifier.testTag("comic_zoom_close")) { Text("확대 보기 닫기") }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            ComicCanvas(controller, current, viewport, false, exploration, {}, navigation = false)
                        }
                    }
                }
            }
            if (current.limitedResolution) Text("메모리 보호를 위해 해상도를 낮춰 표시합니다.", Modifier.align(Alignment.BottomCenter).testTag("comic_resolution_limit"))
        }
    }
}

@Composable
private fun ComicCanvas(controller: ComicReaderController, image: ComicImage, viewport: IntSize,
    continuous: Boolean, exploration: Boolean, onMenu: () -> Unit, navigation: Boolean = true) {
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
    val direction = if (settings.comicDirection == ComicDirection.LTR) 1 else -1
    var modifier = Modifier.fillMaxSize().onSizeChanged { canvasViewport = it }.clipToBounds().semantics { contentDescription = "만화 이미지" }.testTag("comic_image")
    if (!continuous && !exploration) modifier = modifier.pointerInput(image) {
        detectTapGestures(onDoubleTap = { point ->
            val next = if (zoom > 1f) 1f else 2.5f
            origin = clamp(point - (point - origin) * (next / zoom), next); zoom = next; save()
        }, onTap = { point -> if (navigation) when {
            point.x < size.width * .25f -> controller.move(-direction)
            point.x > size.width * .75f -> controller.move(direction)
            else -> onMenu()
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
    Canvas(modifier) {
        drawImage(bitmap, dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
            dstSize = IntSize((bitmap.width * base * zoom).roundToInt().coerceAtLeast(1), (bitmap.height * base * zoom).roundToInt().coerceAtLeast(1)))
    }
}

@Composable
private fun ComicSettingsDialog(controller: ComicReaderController, dismiss: () -> Unit) {
    val state = controller.state.collectAsStateWithLifecycle().value
    val loaded = controller.loadedSettings
    var global by remember(loaded) { mutableStateOf(loaded?.global ?: ReaderSettings()) }
    var book by remember(loaded) { mutableStateOf(loaded?.book ?: BookReaderPreferences()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("만화 설정") }, text = { Column {
        if (state.settingsError) { Text("설정 오류입니다. 기존 값을 유지합니다."); TextButton(onClick = controller::retrySettings) { Text("다시 읽기") } }
        FlowRow { TxtReadingMode.entries.forEach { mode -> FilterChip(selected = (book.readingMode ?: global.readingMode) == mode,
            onClick = { book = book.copy(readingMode = mode) }, label = { Text(if (mode == TxtReadingMode.PAGES) "한 장 보기" else "연속 보기") }, modifier = Modifier.testTag("comic_mode_${mode.name}")) } }
        TextButton(onClick = { book = book.copy(readingMode = TxtReadingMode.SCROLL, comicFit = ComicFit.WIDTH); global = global.copy(comicGapDp = 0) }, modifier = Modifier.testTag("comic_webtoon")) { Text("웹툰 · 연속 / 너비 / 간격 0") }
        FlowRow { ComicDirection.entries.forEach { value -> FilterChip(selected = (book.comicDirection ?: global.comicDirection) == value,
            onClick = { book = book.copy(comicDirection = value) }, label = { Text(value.name) }, modifier = Modifier.testTag("comic_direction_$value")) } }
        FlowRow { ComicFit.entries.forEach { value -> FilterChip(selected = (book.comicFit ?: global.comicFit) == value,
            onClick = { book = book.copy(comicFit = value) }, label = { Text(when (value) { ComicFit.WIDTH -> "너비"; ComicFit.HEIGHT -> "높이"; ComicFit.CONTAIN -> "전체" }) }, modifier = Modifier.testTag("comic_fit_$value")) } }
        TextButton(onClick = { book = book.copy(readingMode = null, comicFit = null, comicDirection = null) }) { Text("기본값 상속") }
    } }, confirmButton = { TextButton(enabled = loaded != null && !state.settingsError && !state.settingsSaving,
        onClick = { controller.saveSettings(global, book); dismiss() }, modifier = Modifier.testTag("comic_settings_apply")) { Text("적용") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("닫기") } })
}
