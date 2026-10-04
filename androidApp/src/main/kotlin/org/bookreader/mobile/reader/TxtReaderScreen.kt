package org.bookreader.mobile.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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
import kotlin.math.roundToInt

@Composable
fun TxtReaderScreen(controller: TxtReaderController, onClose: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val layouts = remember(controller, state.restoreGeneration) { mutableStateMapOf<Int, TextLayoutResult>() }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val viewport = remember(controller) { arrayOf(IntSize.Zero) }
    val density = LocalDensity.current
    val typography = MaterialTheme.typography.bodyLarge
    val previousTypography = remember(controller) { arrayOf(Triple(density.density, density.fontScale, typography)) }
    LaunchedEffect(density.density, density.fontScale, typography) {
        val changed = Triple(density.density, density.fontScale, typography)
        if (previousTypography[0] != changed) controller.beginReflow()
        previousTypography[0] = changed
    }
    BackHandler(onBack = onClose)
    DisposableEffect(controller, lifecycle) {
        // A retained controller returning to a newly measured screen restores its canonical anchor.
        if (controller.state.value.phase == TxtReaderPhase.READY) controller.beginReflow()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) scope.launch { controller.flush() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(controller, state.restoreGeneration, state.phase) {
        if (state.phase != TxtReaderPhase.RESTORING && state.phase != TxtReaderPhase.REFLOWING)
            return@LaunchedEffect
        val generation = state.restoreGeneration
        val target = state.targetOffset
        val index = controller.cache.fragmentFor(target)
        list.scrollToItem(index)
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
        list.scrollToItem(index, layout.getLineTop(line).roundToInt() - list.layoutInfo.viewportStartOffset)
        // Wait for the list to apply that measured line position before allowing the writer.
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == index } }.first { it }
        controller.restored(generation)
    }

    LaunchedEffect(controller, state.restoreGeneration) {
        var userMovement = false
        snapshotFlow {
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull()
            Triple(first, list.isScrollInProgress, controller.state.value.phase)
        }.collect { (item, moving, phase) ->
            if (phase != TxtReaderPhase.READY) { userMovement = false; return@collect }
            if (moving) userMovement = true
            if (item == null) return@collect
            val layout = layouts[item.index] ?: return@collect
            val fragment = controller.state.value.cache.fragments.getOrNull(item.index) ?: return@collect
            val vertical = (list.layoutInfo.viewportStartOffset - item.offset).coerceAtLeast(0).toFloat()
            val line = layout.getLineForVerticalPosition(vertical)
            controller.visibleAnchor(fragment.start + layout.getLineStart(line), userMovement)
            // Measured layouts are bounded to the current window; there is no whole-book layout cache.
            val keep = list.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
            layouts.keys.toList().filter { it !in keep }.forEach { layouts.remove(it) }
        }
    }

    Column(Modifier.fillMaxSize().testTag("txt_reader")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("reader_back")) { Text("서재") }
            Text(controller.book.title, Modifier.weight(1f), maxLines = 1,
                style = MaterialTheme.typography.titleMedium)
        }
        if (state.phase == TxtReaderPhase.ERROR) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(state.error.orEmpty(), modifier = Modifier.testTag("reader_error"))
                Button(onClick = controller::retry, modifier = Modifier.testTag("reader_retry")) { Text("다시 시도") }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().testTag(
                if (state.phase == TxtReaderPhase.READY) "reader_ready" else "reader_content")) {
                LazyColumn(state = list, userScrollEnabled = state.phase == TxtReaderPhase.READY,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxSize().testTag("reader_text")
                        .onSizeChanged { size ->
                            if (viewport[0] != IntSize.Zero && viewport[0] != size) controller.beginReflow()
                            viewport[0] = size
                        }) {
                    items(count = state.cache.fragments.size, key = { it }) { index ->
                        val text by produceState<String?>(null, controller, index, state.restoreGeneration) {
                            try { value = controller.fragment(index) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { controller.contentReadFailed() }
                        }
                        text?.let {
                            key(state.restoreGeneration) { Text(it, style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp, lineHeight = 29.sp),
                                onTextLayout = { result -> layouts[index] = result },
                                modifier = Modifier.fillMaxWidth().testTag("reader_fragment_$index")) }
                        }
                    }
                }
                if (state.phase != TxtReaderPhase.READY) {
                    Column(Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(if (state.targetOffset > state.cache.committedLength)
                            "저장된 위치까지 본문을 준비하는 중" else "독서 위치를 복원하는 중")
                    }
                }
            }
        }
        val offset = state.stableOffset ?: state.targetOffset
        Text(if (state.cache.complete) "${(offset * 100.0 / state.cache.committedLength).toInt()}%"
            else "본문 준비 중",
            Modifier.padding(12.dp).testTag("reader_progress"))
        if (state.saveError) Text("진행도를 저장하지 못했습니다. 다시 시도합니다.",
            Modifier.padding(8.dp).testTag("reader_save_error"))
    }
}
