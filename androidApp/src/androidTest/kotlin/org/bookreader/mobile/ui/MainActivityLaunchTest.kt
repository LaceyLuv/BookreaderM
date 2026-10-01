package org.bookreader.mobile.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.bookreader.mobile.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivityLaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launcherStartsAtLibraryAndActivityRecreationKeepsSelectedTab() {
        compose.onNodeWithTag("tab_LIBRARY").assertIsSelected()
        compose.onNodeWithTag("tab_SETTINGS").performClick().assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("tab_SETTINGS").assertIsSelected()
    }
}
