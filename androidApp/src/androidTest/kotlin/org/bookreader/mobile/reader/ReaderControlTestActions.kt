package org.bookreader.mobile.reader

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/** Open hidden tools through the same physical tap used by readers, including Readium's WebView. */
fun AndroidComposeTestRule<*, *>.readerControl(tag: String): SemanticsNodeInteraction {
    val prefix = tag.substringBefore('_')
    if (onAllNodesWithTag("${prefix}_chrome").fetchSemanticsNodes().isEmpty()) {
        // Loading/error states expose an immediate exit without requiring a ready publication.
        if (onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return onNodeWithTag(tag)
        tapReader(prefix, .5f)
        waitUntil(5_000) { onAllNodesWithTag("${prefix}_chrome").fetchSemanticsNodes().isNotEmpty() }
    }
    return onNodeWithTag(tag).performScrollTo()
}

fun AndroidComposeTestRule<*, *>.tapReader(prefix: String, fraction: Float, verticalFraction: Float = .45f) {
    val node = onAllNodesWithTag("${prefix}_ready").fetchSemanticsNodes().single()
    val point = runOnIdle {
        val view = (node.root as ViewRootForTest).view
        val location = IntArray(2); view.getLocationOnScreen(location)
        val bounds = node.boundsInRoot
        Offset(location[0] + bounds.left + bounds.width * fraction,
            location[1] + bounds.top + bounds.height * verticalFraction)
    }
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val start = SystemClock.uptimeMillis()
    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
        val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue("Android must accept the reader tap", automation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }
    waitForIdle()
}
