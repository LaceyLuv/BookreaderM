package org.bookreader.mobile.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Only Stop or a completed non-drag action may commit; cancellation never moves the Reader. */
class ReaderSeekPreview(private val commit: (Float) -> Unit) {
    var fraction: Float? by mutableStateOf(null)
        private set
    var version: Long = 0
        private set
    private var dragging = false
    private var dragCycle = false

    fun change(value: Float) {
        require(value.isFinite())
        if (fraction == null) dragCycle = dragging
        fraction = value.coerceIn(0f, 1f)
        version++
    }
    fun dragStarted() { dragging = true; dragCycle = true }
    fun dragStopped() { dragging = false; commitPreview() }
    fun cancel() { dragging = false; dragCycle = true; fraction = null; version++ }
    fun finishNonDrag(expectedVersion: Long) {
        if (version == expectedVersion && !dragging && !dragCycle) commitPreview()
    }
    private fun commitPreview() {
        val value = fraction ?: return
        fraction = null
        version++
        commit(value)
    }
}
