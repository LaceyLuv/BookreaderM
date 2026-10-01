package org.bookreader.mobile.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses real Android SharedPreferences in an isolated test namespace. */
class AndroidThemeStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("theme_store_test", Context.MODE_PRIVATE)

    @After fun cleanFixture() { check(preferences.edit().clear().commit()) }

    @Test fun selectedThemePersistsAcrossStoreInstances() {
        assertTrue(AndroidThemeStore(preferences).write(ThemeMode.DARK).isSuccess)
        assertEquals(ThemeMode.DARK, AndroidThemeStore(preferences).read().getOrThrow())
    }

    @Test fun invalidStoredThemeReportsErrorAndPreservesOriginal() {
        check(preferences.edit().putString("app_theme", "future-theme").commit())
        assertTrue(AndroidThemeStore(preferences).read().isFailure)
        assertEquals("future-theme", preferences.getString("app_theme", null))
    }
}
