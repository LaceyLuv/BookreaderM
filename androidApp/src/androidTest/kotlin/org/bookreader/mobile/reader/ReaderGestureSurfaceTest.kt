package org.bookreader.mobile.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderGestureSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun repeatedSideAndCenterTapsUseCurrentCallbacksIncludingOuterEdge() {
        var page by mutableIntStateOf(5)
        var visible by mutableStateOf(false)
        compose.setContent {
            val renderedPage = page
            val renderedVisible = visible
            Box(Modifier.fillMaxSize().testTag("surface").readerGestures(true, true,
                { if (it < .25f) page = renderedPage - 1 else if (it > .75f) page = renderedPage + 1 else visible = !renderedVisible },
                {}, { page = renderedPage + it })) {
                SelectionContainer { Text("선택 가능한 본문 ".repeat(500), Modifier.fillMaxSize()) }
            }
        }
        fun tap(x: Float) {
            compose.onNodeWithTag("surface").performTouchInput { click(Offset(width * x, height * .4f)) }
            compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        }
        tap(.98f); tap(.85f); tap(.85f)
        compose.runOnIdle { assertEquals(8, page) }
        tap(.02f)
        compose.runOnIdle { assertEquals(7, page) }
        tap(.5f)
        compose.runOnIdle { assertTrue(visible); assertEquals(7, page) }
        tap(.5f)
        compose.runOnIdle { assertFalse(visible); assertEquals(7, page) }
    }

    @Test fun longPressSelectionCancelledTouchesAndSystemEdgeSwipesNeverTurnPages() {
        var actions = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("surface").readerGestures(true, true,
                { actions++ }, { actions++ }, { actions++ })) {
                SelectionContainer { Text("길게 눌러 선택할 수 있는 본문 ".repeat(500), Modifier.fillMaxSize()) }
            }
        }
        compose.onNodeWithTag("surface").performTouchInput { longClick(Offset(width * .8f, height * .4f)) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(0, actions) }
        compose.onNodeWithTag("surface").performTouchInput { down(center); cancel() }
        compose.onNodeWithTag("surface").performTouchInput { swipe(Offset(1f, centerY), Offset(width * .8f, centerY), 200) }
        compose.onNodeWithTag("surface").performTouchInput {
            down(0, Offset(width * .4f, centerY)); down(1, Offset(width * .6f, centerY))
            moveTo(0, Offset(width * .2f, centerY)); moveTo(1, Offset(width * .8f, centerY)); up(0); up(1)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(0, actions) }
    }
}
