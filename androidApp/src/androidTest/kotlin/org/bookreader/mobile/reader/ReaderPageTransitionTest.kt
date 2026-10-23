package org.bookreader.mobile.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.bookreader.mobile.settings.PageTransition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderPageTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bothDirectionsAndDisabledEffectKeepEveryFrameCovered() {
        val page = mutableIntStateOf(0)
        val direction = mutableIntStateOf(1)
        val effect = mutableStateOf(PageTransition.SLIDE)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.size(160.dp, 120.dp).background(Color.Green).testTag("deck")) {
                ReaderPageTransition(page.intValue, effect.value, direction.intValue, Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(if (it % 2 == 0) Color.Red else Color.Blue))
                }
            }
        }
        fun assertCovered() {
            val pixels = compose.onNodeWithTag("deck").captureToImage().toPixelMap()
            for (x in 1 until pixels.width - 1 step 3) for (y in 1 until pixels.height - 1 step 3) {
                val pixel = pixels[x, y]
                assertTrue("A turn exposed the empty backing surface at $x,$y", pixel.green < .05f && pixel.alpha > .99f)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertCovered()
        for (sign in listOf(1, -1)) {
            compose.runOnIdle { direction.intValue = sign; page.intValue++ }
            repeat(14) { compose.mainClock.advanceTimeByFrame(); assertCovered() }
        }
        compose.runOnIdle { effect.value = PageTransition.NONE; page.intValue++ }
        compose.mainClock.advanceTimeByFrame()
        assertCovered()
    }
}
