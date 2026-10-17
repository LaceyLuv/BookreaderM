package org.bookreader.mobile.epub

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.useWriterConnection
import androidx.room.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.importing.DeleteResult
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.EpubLocator
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.repository.*
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.ui.AppViewModel
import org.junit.*
import org.junit.Assert.*

/** Real managed import, Room records, stable SDK Fragment and product UI; generated books only. */
class EpubReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val database by lazy { createAndroidDatabase(target) }
    private var existing: Set<String>? = null
    private var originalGlobal: org.bookreader.mobile.database.ReaderGlobalSettingsEntity? = null
    @Before fun preserveExistingBooks() { existing = books().map { it.id }.toSet(); originalGlobal = runBlocking { database.readerSettingsDao().global() } }
    @After fun cleanupOwnedFixtures() {
        try {
            compose.activityRule.scenario.close()
            val preserved = existing ?: return
            books().filter { it.id !in preserved && it.originalDisplayName.startsWith("../../epub-") }.forEach {
                runBlocking { assertEquals(DeleteResult.Deleted, AndroidBookManagement(target).delete(it.id)) }
            }
        } finally {
            runBlocking {
                val original = originalGlobal
                if (original == null) database.useWriterConnection { it.execSQL("DELETE FROM reader_global_settings WHERE id = 'global'") }
                else database.readerSettingsDao().putGlobal(original)
            }
            database.close()
        }
    }
    @Test fun managedEpubTocSearchBookmarksAndRecreationPreserveFullLocator() {
        send("epub-product"); waitFor("epub_ready")
        val active = reader(); val book = active.book
        assertEquals(BookFormat.EPUB, book.format)
        val source = target.assets.open("epub-fixtures/epub3.epub").use { it.readBytes() }
        assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
        compose.onNodeWithTag("epub_toc").performClick()
        compose.onAllNodesWithTag("epub_toc_item")[1].performClick()
        compose.waitUntil(20_000) { saved(book)?.payload?.locator?.get("href")?.toString()?.contains("two.xhtml") == true }
        compose.onNodeWithTag("epub_search").performClick()
        compose.onNodeWithTag("epub_search_query").performTextInput("two 문단 0179")
        compose.onNodeWithTag("epub_search_start").performClick()
        waitFor("epub_search_result")
        compose.onNodeWithTag("epub_search_result").performClick()
        compose.waitUntil(20_000) {
            val locator = saved(book)?.payload?.locator ?: return@waitUntil false
            val json = org.json.JSONObject(locator.toString())
            json.getString("href").endsWith("two.xhtml") && json.getJSONObject("locations").optDouble("progression", 0.0) > .5
        }
        val original = requireNotNull(saved(book))
        compose.onNodeWithTag("epub_bookmarks").performClick()
        compose.onNodeWithTag("epub_bookmark_add").performClick()
        waitFor("epub_bookmark_item")
        compose.onNodeWithText("닫기").performClick()
        compose.activityRule.scenario.recreate()
        waitFor("epub_ready")
        assertEquals(original, saved(book))
        assertRenderedNear(original)
        compose.onNodeWithTag("epub_settings").performClick()
        compose.onNodeWithTag("font_size_increase").performScrollTo().performClick()
        compose.onNodeWithTag("reader_settings_apply").performClick()
        waitFor("epub_ready")
        assertEquals(original, saved(book))
        assertRenderedNear(original)
        compose.onNodeWithTag("epub_back").performClick()
        waitFor("continue_reading")
        compose.onNodeWithTag("continue_reading").performClick()
        waitFor("epub_ready")
        assertEquals(original, saved(book))
        assertRenderedNear(original)
        compose.onNodeWithTag("epub_bookmarks").performClick()
        waitFor("epub_bookmark_item")
        compose.onNodeWithTag("epub_bookmark_item").performClick()
        waitFor("epub_ready")
        assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
    }
    @Test fun unsafeEpubImportCannotReplaceExistingProgressOrManagedCopy() {
        send("epub-product"); waitFor("epub_ready")
        val book = reader().book
        compose.onNodeWithTag("epub_next").performClick()
        compose.waitUntil(20_000) { saved(book) != null }
        compose.onNodeWithTag("epub_back").performClick(); waitFor("continue_reading")
        val record = saved(book)
        val source = File(target.filesDir, "managed/${book.managedRelativePath}").readBytes()
        val ids = books().map { it.id }.toSet()
        for (name in listOf("epub-unsafe", "epub-disguised")) {
            send(name); waitFor("import_error")
            assertEquals(ids, books().map { it.id }.toSet())
            assertEquals(record, saved(book))
            assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
        }
    }
    @Test fun negativeTxtLetterSpacingOpensEpubWithoutRewritingStoredDefaults() {
        val value = org.bookreader.mobile.settings.ReaderSettings(letterSpacingEm = -.02f)
        val raw = org.bookreader.mobile.database.ReaderGlobalSettingsEntity(schemaVersion = 1,
            settingsJson = org.bookreader.mobile.settings.ReaderSettingsCodec.write(value))
        runBlocking { database.readerSettingsDao().putGlobal(raw) }
        send("epub-product"); waitFor("epub_ready")
        assertEquals(-.02f, reader().state.value.settings.letterSpacingEm)
        assertEquals(0.0, requireNotNull(reader().preferences().letterSpacing), 0.0)
        assertEquals(false, reader().preferences().publisherStyles)
        assertEquals(raw, runBlocking { database.readerSettingsDao().global() })
        compose.onNodeWithTag("epub_back").performClick(); waitFor("continue_reading")
        assertEquals(raw, runBlocking { database.readerSettingsDao().global() })
    }
    private fun assertRenderedNear(locator: EpubLocator) {
        compose.activityRule.scenario.onActivity { activity ->
            val wrapper = activity.supportFragmentManager.fragments.filterIsInstance<EpubHostFragment>().single()
            val navigator = wrapper.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single()
            val actual = navigator.currentLocator.value.toJSON()
            val expected = org.json.JSONObject(locator.payload.locator.toString())
            assertEquals(expected.getString("href"), actual.getString("href"))
            assertEquals(expected.getJSONObject("locations").optDouble("progression", 0.0),
                actual.getJSONObject("locations").optDouble("progression", 0.0), .03)
        }
    }
    private fun reader(): EpubReaderController {
        var owner: EpubReaderController? = null
        compose.activityRule.scenario.onActivity { owner = ViewModelProvider(it)[AppViewModel::class.java].state.value.epubReader }
        return requireNotNull(owner)
    }
    private fun books(): List<Book> = runBlocking { withContext(Dispatchers.IO) {
        when (val read = RoomBookRepository(database).loadBooks()) {
            is LibraryState.Content -> read.books
            LibraryState.Empty -> emptyList()
            else -> error("Fixture library read failed")
        }
    } }
    private fun saved(book: Book): EpubLocator? = runBlocking { withContext(Dispatchers.IO) {
        when (val read = RoomProgressRepository(database).loadProgress(book.id, requireNotNull(book.currentRevision))) {
            is ProgressReadResult.Found -> read.progress.locator as EpubLocator
            ProgressReadResult.Missing -> null
            is ProgressReadResult.Error -> error("Fixture progress read failed")
        }
    } }
    private fun send(name: String) { instrumentation.context.startActivity(Intent().apply {
        component = ComponentName(instrumentation.context, TestSenderActivity::class.java)
        putExtra("fixture", name); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }) }
    private fun waitFor(tag: String) {
        compose.waitUntil(40_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}
