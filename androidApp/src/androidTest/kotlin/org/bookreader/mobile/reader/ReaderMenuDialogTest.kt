package org.bookreader.mobile.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderMenuDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun realMaterialSliderCancelThenAccessibilityCommitsExactlyOnce() {
        val commits = mutableListOf<Float>()
        val seek = ReaderSeekPreview(commits::add)
        compose.setContent { MaterialTheme {
            ReaderMenuDialog(TxtReaderState(phase = TxtReaderPhase.READY,
                cache = TxtCacheSnapshot(committedLength = 10_000, complete = true), stableOffset = 8500),
                seek, previous = true, next = true, onPrevious = {}, onNext = {}, onSearch = {},
                onBookmarks = {}, onSettings = {}, onExit = {}, onDismiss = {})
        } }
        compose.onNodeWithTag("reader_seek").performTouchInput { down(center); moveTo(Offset(width * .8f, centerY), 100) }
        compose.waitForIdle()
        assertTrue(commits.isEmpty())
        compose.onNodeWithTag("reader_seek").performTouchInput { cancel() }
        compose.waitForIdle()
        assertTrue(commits.isEmpty())
        compose.onNodeWithTag("reader_seek").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.7f)) }
        compose.waitForIdle()
        assertEquals("Preview version=${seek.version} fraction=${seek.fraction}", listOf(.7f), commits)
    }
}
