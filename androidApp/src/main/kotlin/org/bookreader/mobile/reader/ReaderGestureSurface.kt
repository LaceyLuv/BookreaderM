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

/** Observe the final pass: child selection/links/scrolling and multi-touch always take priority. */
fun Modifier.readerGestures(enabled: Boolean, pageMode: Boolean, onTap: (Float) -> Unit,
    onDoubleTap: () -> Unit, onPage: (Int) -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val page by rememberUpdatedState(onPage)
    pointerInput(enabled, pageMode) {
        if (!enabled) return@pointerInput
        coroutineScope {
            var pendingTap: Job? = null
            var previousUp = Long.MIN_VALUE
            var previousPosition = Offset.Zero
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val edge = 24 * density
                val atEdge = down.position.x < edge || down.position.x > size.width - edge
                val second = pendingTap?.isActive == true && down.uptimeMillis - previousUp in
                    viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis &&
                    (down.position - previousPosition).getDistance() < 64 * density
                if (second) pendingTap?.cancel()
                var blocked = false
                var moved = false
                var last = down
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.size != 1 || event.changes.any { it.isConsumed }) blocked = true
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    last = change
                    if ((last.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (!change.pressed) break
                }
                if (last.pressed || blocked) return@awaitEachGesture
                val delta = last.position - down.position
                val elapsed = last.uptimeMillis - down.uptimeMillis
                if (pageMode && !atEdge && abs(delta.x) > maxOf(viewConfiguration.touchSlop * 3, size.width * .15f) &&
                    abs(delta.x) > abs(delta.y) * 2 && elapsed < viewConfiguration.longPressTimeoutMillis) {
                    pendingTap?.cancel()
                    page(if (delta.x < 0) 1 else -1)
                } else if (!moved && elapsed < viewConfiguration.longPressTimeoutMillis) {
                    if (second) doubleTap()
                    else {
                        previousUp = last.uptimeMillis
                        previousPosition = last.position
                        pendingTap?.cancel()
                        val fraction = last.position.x / size.width.coerceAtLeast(1)
                        pendingTap = launch { delay(viewConfiguration.doubleTapTimeoutMillis); tap(fraction) }
                    }
                }
            }
        }
    }
}
