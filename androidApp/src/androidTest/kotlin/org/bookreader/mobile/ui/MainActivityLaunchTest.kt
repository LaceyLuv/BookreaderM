package org.bookreader.mobile.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.bookreader.mobile.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivityLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launcherStartsAtLibraryAndActivityRecreationKeepsSelectedTab() {
        compose.onNodeWithTag("tab_LIBRARY").assertIsSelected()
        // A fresh installation must complete the real Android Room open/query successfully.
        // Loading or database error must not pass merely because navigation is available.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("library_empty").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("library_empty").assertIsDisplayed()
        compose.onNodeWithTag("tab_SETTINGS").performClick().assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab_SETTINGS").assertIsSelected()
    }
}
