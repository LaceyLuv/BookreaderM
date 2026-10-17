package org.bookreader.mobile.reader

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun ReaderMenuDialog(state: TxtReaderState, seek: ReaderSeekPreview, previous: Boolean, next: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onSearch: () -> Unit, onBookmarks: () -> Unit,
    onSettings: () -> Unit, onExit: () -> Unit, onDismiss: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(source) {
        source.interactions.collect { interaction -> when (interaction) {
            is DragInteraction.Start -> seek.dragStarted()
            is DragInteraction.Stop -> seek.dragStopped()
            is DragInteraction.Cancel -> seek.cancel()
        } }
    }
    DisposableEffect(seek) { onDispose { seek.cancel() } }
    val offset = state.stableOffset ?: state.targetOffset
    val current = if (state.cache.complete && state.cache.committedLength > 0)
        (offset.toDouble() / state.cache.committedLength).toFloat().coerceIn(0f, 1f) else 0f
    AlertDialog(onDismissRequest = onDismiss, title = { Text("독서 메뉴") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(if (!state.cache.complete) "위치 계산 중" else seek.fraction?.let { "이동 미리보기 ${(it * 100).toInt()}%" }
                ?: "현재 ${(current * 100).toInt()}%", modifier = Modifier.testTag("reader_seek_preview"))
            Slider(value = seek.fraction ?: current, onValueChange = seek::change,
                enabled = state.cache.complete && state.phase in setOf(TxtReaderPhase.READY, TxtReaderPhase.REFLOWING),
                interactionSource = source, modifier = Modifier.fillMaxWidth().testTag("reader_seek")
                    .semantics { contentDescription = "독서 위치" },
                onValueChangeFinished = {
                    val version = seek.version
                    // Foundation 1.9.3 emits Stop/Cancel before onDragStopped; that callback also
                    // runs on cancellation. Let the interaction collector settle before the tap/
                    // accessibility fallback. A cancelled drag never commits its preview.
                    scope.launch { yield(); seek.finishNonDrag(version) }
                })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onPrevious, enabled = previous, modifier = Modifier.testTag("reader_menu_previous")) { Text("이전") }
                TextButton(onClick = onNext, enabled = next, modifier = Modifier.testTag("reader_menu_next")) { Text("다음") }
            }
            TextButton(onClick = onSearch, enabled = state.phase == TxtReaderPhase.READY) { Text("본문 검색") }
            TextButton(onClick = onBookmarks, enabled = state.phase == TxtReaderPhase.READY) { Text("북마크") }
            TextButton(onClick = onSettings) { Text("읽기 설정") }
            TextButton(onClick = onExit, modifier = Modifier.testTag("reader_menu_exit")) { Text("서재로 나가기") }
            Text("좌우 탭: 이전·다음 · 가운데 탭: 메뉴", Modifier.padding(top = 8.dp))
        }
    }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("reader_menu_close")) { Text("닫기 / 이동 취소") } })
}
