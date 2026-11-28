package org.bookreader.mobile.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal var lastReaderGestureTrace: String = "none"
    private set

/** Observe the final pass: child selection/links/scrolling and multi-touch always take priority. */
fun Modifier.readerGestures(
    enabled: Boolean,
    pageMode: Boolean,
    onTap: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    onPage: (Int) -> Unit,
    onDragOffset: (Float) -> Unit = {},
): Modifier = composed {
    val enabledState by rememberUpdatedState(enabled)
    val pageModeState by rememberUpdatedState(pageMode)
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val page by rememberUpdatedState(onPage)
    val dragOffset by rememberUpdatedState(onDragOffset)
    pointerInput(pageMode) {
        coroutineScope {
            var pendingTap: Job? = null
            var previousUp = Long.MIN_VALUE
            var previousPosition = Offset.Zero
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!enabledState) {
                    lastReaderGestureTrace = "disabled(pageMode=$pageModeState,w=${size.width})"
                    return@awaitEachGesture
                }
                val edge = 20 * density
                val atEdge = down.position.x < edge || down.position.x > size.width - edge
                val second = pendingTap?.isActive == true && down.uptimeMillis - previousUp in
                    viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                    (down.position - previousPosition).getDistance() < 64 * density
                if (second) pendingTap?.cancel()
                var blocked = false
                var moved = false
                var draggingHorizontal = false
                var last = down
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.changes.size != 1 || event.changes.any { it.isConsumed }) {
                            blocked = true
                            if (draggingHorizontal) {
                                draggingHorizontal = false
                                dragOffset(0f)
                            }
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        last = change
                        val delta = last.position - down.position
                        if (delta.getDistance() > viewConfiguration.touchSlop) moved = true
                        if (!blocked && pageModeState && !atEdge) {
                            if (!draggingHorizontal && abs(delta.x) > viewConfiguration.touchSlop &&
                                abs(delta.x) > abs(delta.y) * 1.2f
                            ) {
                                draggingHorizontal = true
                                pendingTap?.cancel()
                            }
                            if (draggingHorizontal) {
                                val maxDrag = size.width * 0.35f
                                dragOffset(delta.x.coerceIn(-maxDrag, maxDrag))
                            }
                        }
                        if (!change.pressed) break
                    }
                } finally {
                    if (draggingHorizontal) dragOffset(0f)
                }
                val delta = last.position - down.position
                val elapsed = last.uptimeMillis - down.uptimeMillis
                val swipeThreshold = maxOf(viewConfiguration.touchSlop * 2f, size.width * .08f)
                if (last.pressed || blocked || !enabledState) {
                    lastReaderGestureTrace = "ignored(pressed=${last.pressed},blocked=$blocked,enabled=$enabledState," +
                        "pageMode=$pageModeState,atEdge=$atEdge,dx=${delta.x.toInt()},dy=${delta.y.toInt()},w=${size.width})"
                    return@awaitEachGesture
                }
                if (pageModeState && !atEdge && abs(delta.x) > swipeThreshold &&
                    abs(delta.x) > abs(delta.y) * 1.2f
                ) {
                    val direction = if (delta.x < 0) 1 else -1
                    lastReaderGestureTrace = "swipe(dir=$direction,dx=${delta.x.toInt()},dy=${delta.y.toInt()},w=${size.width})"
                    pendingTap?.cancel()
                    page(direction)
                } else if (!moved && elapsed < viewConfiguration.longPressTimeoutMillis) {
                    if (second) {
                        lastReaderGestureTrace = "doubleTap"
                        doubleTap()
                    } else {
                        previousUp = last.uptimeMillis
                        previousPosition = last.position
                        pendingTap?.cancel()
                        val fraction = last.position.x / size.width.coerceAtLeast(1)
                        lastReaderGestureTrace = "tap(frac=$fraction)"
                        val tapDelayMs = minOf(210L, viewConfiguration.doubleTapTimeoutMillis)
                        pendingTap = launch {
                            delay(tapDelayMs)
                            if (enabledState) tap(fraction)
                        }
                    }
                } else {
                    lastReaderGestureTrace = "noMatch(pageMode=$pageModeState,atEdge=$atEdge,moved=$moved," +
                        "dx=${delta.x.toInt()},dy=${delta.y.toInt()},thr=${swipeThreshold.toInt()},w=${size.width})"
                }
            }
        }
    }
}
