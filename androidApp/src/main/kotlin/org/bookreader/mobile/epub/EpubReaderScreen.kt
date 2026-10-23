package org.bookreader.mobile.epub

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.bookreader.mobile.reader.*
import org.readium.r2.shared.publication.Link

@Composable
fun EpubReaderScreen(controller: EpubReaderController, onClose: () -> Unit, onRetry: () -> Unit) {
    val state = controller.state.collectAsStateWithLifecycle().value
    val progress = controller.progress.state.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
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
    val pageLabel = if (state.currentPosition != null && state.totalPositions != null)
        "${state.currentPosition} / ${state.totalPositions} 쪽 · EPUB 위치 기준" else "쪽수 계산 중"
    BackHandler {
        when { settings -> settings = false; search -> { search = false; controller.cancelSearch() }
            bookmarks -> bookmarks = false; toc -> toc = false; menu -> menu = false; controlsVisible -> controlsVisible = false; else -> onClose() }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) scope.launch { controller.progress.flush() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.cancelSearch() }
    }
    @Composable fun Controls() {
        FlowRow {
            TextButton(onClick = { toc = true; menu = false }, enabled = ready, modifier = Modifier.testTag("epub_toc")) { Text("목차") }
            TextButton(onClick = { search = true; menu = false }, enabled = ready, modifier = Modifier.testTag("epub_search")) { Text("검색") }
            TextButton(onClick = { bookmarks = true; menu = false; controller.loadBookmarks() }, enabled = ready, modifier = Modifier.testTag("epub_bookmarks")) { Text("북마크") }
            TextButton(onClick = { settings = true; menu = false }, enabled = ready, modifier = Modifier.testTag("epub_settings")) { Text("설정") }
        }
    }
    Box(Modifier.fillMaxSize().background(state.settings.theme.paperColor()).testTag("epub_reader")) {
        if (state.error != null || progress.phase == ArchiveReaderPhase.ERROR) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
                Text(state.error ?: "저장된 독서 위치를 읽거나 복원하지 못했습니다. 기존 기록은 보존됩니다.", Modifier.testTag("epub_error"))
                Button(onClick = onRetry, modifier = Modifier.testTag("epub_retry")) { Text("다시 시도") }
                TextButton(onClick = onClose) { Text("서재로") }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(bottom = ReaderPageStatusHeight).onSizeChanged {
                if (viewport != IntSize.Zero && viewport != it) controller.reflowViewport()
                viewport = it
            }.testTag(if (ready) "epub_ready" else "epub_loading")) {
                if (state.opened) key(controller, state.hostVersion) { AndroidFragment<EpubHostFragment>(Modifier.fillMaxSize(), onUpdate = { fragment ->
                    fragment.onCenterTap = { controlsVisible = !controlsVisible }
                    fragment.touchExploration = exploration
                }) }
                if (!ready) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        ReaderPageStatus(pageLabel, "epub", state.settings.theme.paperColor(), state.settings.theme.inkColor())
        if (!chrome && !ready) ReaderLoadingExit("epub", onClose, Modifier.align(Alignment.TopStart))
        if (chrome) ReaderChrome(controller.book.title, "EPUB", "epub",
            pageLabel,
            onClose, { controlsVisible = false }, !exploration, ready, ready,
            { controller.next(-1) }, { controller.next(1) }) {
            Controls()
            FlowRow {
                TextButton(onClick = { menu = true }, modifier = Modifier.testTag("epub_menu")) { Text("위치 이동") }
                if (state.canReturn) TextButton(onClick = controller::returnFromJump, enabled = ready, modifier = Modifier.testTag("epub_return")) { Text("이전 위치로") }
            }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (progress.saveError) Text("독서 위치를 저장하지 못했습니다. 마지막 저장 기록은 유지됩니다.",
            Modifier.align(Alignment.Center).padding(16.dp).testTag("epub_save_error"), color = MaterialTheme.colorScheme.error)
    }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text("독서 메뉴") }, text = {
        Column {
            val current = (controller.initialLocator()?.locations?.totalProgression ?: 0.0).toFloat()
            Text("${if (seek.fraction == null) "현재" else "이동 미리보기"} ${((seek.fraction ?: current) * 100).toInt()}%")
            ReaderPositionSlider(current, ready, seek, "epub_seek")
            Controls()
        }
    }, confirmButton = { TextButton(onClick = { menu = false }, modifier = Modifier.testTag("epub_menu_close")) { Text("닫기 / 이동 취소") } })
    if (settings) ReaderSettingsDialog(TxtReaderState(phase = if (ready) TxtReaderPhase.READY else TxtReaderPhase.REFLOWING, settings = state.settings, settingsLoaded = controller.loadedSettings,
        settingsError = state.settingsError, settingsSaving = state.settingsSaving), controller::retrySettings, controller::saveSettings, epubMode = true) { settings = false }
    if (search) {
        var query by remember(controller) { mutableStateOf(state.query) }
        AlertDialog(onDismissRequest = { search = false; controller.cancelSearch() }, title = { Text("본문 검색") }, text = {
            Column {
                OutlinedTextField(query, { if (it.length <= 256) query = it }, label = { Text("찾을 문장 · 대소문자 구분") }, modifier = Modifier.testTag("epub_search_query"))
                TextButton(onClick = { controller.search(query) }, enabled = query.isNotBlank(), modifier = Modifier.testTag("epub_search_start")) { Text("찾기") }
                if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.searchError) Text("검색 한도에 도달했거나 일부 본문을 읽지 못했습니다. 표시된 결과만 확인할 수 있습니다.")
                LazyColumn(Modifier.heightIn(max = 360.dp)) { items(state.results) { result ->
                    TextButton(onClick = { controller.navigate(result); search = false; controller.cancelSearch() }, modifier = Modifier.testTag("epub_search_result")) {
                        Text(result.text.highlight.orEmpty() + " · " + result.text.after.orEmpty(), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                } }
            }
        }, confirmButton = { TextButton(onClick = { search = false; controller.cancelSearch() }) { Text("닫기") } })
    }
    if (toc) {
        val links = remember(controller) { flattenToc(controller.publication?.tableOfContents.orEmpty()) }
        AlertDialog(onDismissRequest = { toc = false }, title = { Text("목차") }, text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) { items(links.first) { (link, depth) ->
                TextButton(onClick = { controller.publication?.locatorFromLink(link)?.let { controller.navigate(it) }; toc = false }, modifier = Modifier.padding(start = (depth * 8).dp).testTag("epub_toc_item")) {
                    Text(link.title?.take(200) ?: "이름 없는 장", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
                if (links.second) item { Text("목차 표시 한도에 도달했습니다. 일부 항목은 본문 이동으로 확인할 수 있습니다.") }
            }
        }, confirmButton = { TextButton(onClick = { toc = false }) { Text("닫기") } })
    }
    if (bookmarks) AlertDialog(onDismissRequest = { bookmarks = false }, title = { Text("북마크") }, text = {
        Column {
            TextButton(onClick = controller::addBookmark, modifier = Modifier.testTag("epub_bookmark_add")) { Text("현재 위치 추가") }
            if (state.bookmarkError) Text("북마크를 읽거나 변경하지 못했습니다. 기존 기록은 유지됩니다.")
            LazyColumn(Modifier.heightIn(max = 300.dp)) { items(state.bookmarks, key = { it.id }) { bookmark ->
                Row {
                    TextButton(onClick = { controller.goBookmark(bookmark); bookmarks = false }, modifier = Modifier.weight(1f).testTag("epub_bookmark_item")) { Text(bookmark.label ?: "저장한 위치") }
                    TextButton(onClick = { controller.deleteBookmark(bookmark.id) }) { Text("삭제") }
                }
            } }
            if (state.bookmarkCursor != null) TextButton(onClick = { controller.loadBookmarks(true) }) { Text("다음 50개") }
            TextButton(onClick = { controller.loadBookmarks() }) { Text("처음 / 다시 읽기") }
        }
    }, confirmButton = { TextButton(onClick = { bookmarks = false }) { Text("닫기") } })
    state.externalUrl?.let { url -> AlertDialog(onDismissRequest = controller::dismissExternal,
        title = { Text("외부 링크 열기") }, text = { Text(url, maxLines = 4, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: android.content.ActivityNotFoundException) { controller.externalOpenFailed() }
            controller.dismissExternal()
        }) { Text("브라우저로 열기") } }, dismissButton = { TextButton(onClick = controller::dismissExternal) { Text("취소") } }) }
}

private fun flattenToc(links: List<Link>): Pair<List<Pair<Link, Int>>, Boolean> {
    val pending = ArrayDeque<Pair<Link, Int>>()
    links.asReversed().forEach { pending.addLast(it to 0) }
    val result = mutableListOf<Pair<Link, Int>>()
    var truncated = false
    while (pending.isNotEmpty() && result.size < 1000) {
        val entry = pending.removeLast(); result += entry
        if (entry.second < 32) entry.first.children.asReversed().forEach { pending.addLast(it to entry.second + 1) }
        else if (entry.first.children.isNotEmpty()) truncated = true
    }
    return result to (truncated || pending.isNotEmpty())
}
