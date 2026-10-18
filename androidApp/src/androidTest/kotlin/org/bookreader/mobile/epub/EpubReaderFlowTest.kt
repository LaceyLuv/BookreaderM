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
    private var observationFileName = "epub-restore-observations.json"
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
        File(target.filesDir, "epub-restore-observations.json").writeText("[]")
        send("epub-product"); waitFor("epub_ready")
        val active = reader(); val book = active.book
        assertEquals(BookFormat.EPUB, book.format)
        val source = target.assets.open("epub-fixtures/epub3.epub").use { it.readBytes() }
        assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
        compose.onNodeWithTag("epub_menu").performClick()
        val beforeSeek = saved(book)
        compose.onNodeWithTag("epub_seek").performTouchInput {
            down(center); moveTo(androidx.compose.ui.geometry.Offset(width * .9f, centerY)); cancel()
        }
        compose.waitForIdle(); android.os.SystemClock.sleep(400)
        assertEquals(beforeSeek, saved(book))
        compose.onNodeWithTag("epub_seek").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.9f) }
        compose.waitUntil(20_000) { saved(book)?.payload?.locator?.get("href")?.toString()?.contains("two.xhtml") == true }
        compose.onNodeWithTag("epub_menu_close").performClick()
        compose.onNodeWithTag("epub_return").performClick()
        compose.waitUntil(20_000) { saved(book)?.payload?.locator?.get("href")?.toString()?.contains("one.xhtml") == true }
        compose.onNodeWithTag("epub_toc").performClick()
        compose.onAllNodesWithTag("epub_toc_item")[1].performClick()
        compose.waitUntil(20_000) { saved(book)?.payload?.locator?.get("href")?.toString()?.contains("two.xhtml") == true }
        waitFor("epub_ready")
        val searchViewport = compose.onNodeWithTag("epub_ready").fetchSemanticsNode().boundsInRoot.size
        compose.onNodeWithTag("epub_search").performClick()
        compose.onNodeWithTag("epub_search_query").performTextInput("two 문단 0179")
        compose.onNodeWithTag("epub_search_start").performClick()
        waitFor("epub_search_result")
        // Numeric progression describes the viewport start. Compare recreation/font reflow
        // from the full viewport, not a keyboard-shortened one (234 vs 516 CSS px on API 26).
        // The forced replacements below and in the next test still exercise in-flight jumps.
        var imeVisible = false
        compose.activityRule.scenario.onActivity {
            imeVisible = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        if (imeVisible) instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(10_000) {
            active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY &&
                compose.onAllNodesWithTag("epub_ready").fetchSemanticsNodes()
                    .singleOrNull()?.boundsInRoot?.size == searchViewport
        }
        val requested = active.state.value.results.single()
        compose.onNodeWithTag("epub_search_result").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { click ->
            val accepted = active.progress.state.value.accepted
            active.reflowViewport()
            assertEquals(org.bookreader.mobile.reader.ArchiveReaderPhase.REFLOWING, active.progress.state.value.phase)
            assertTrue(click())
            assertEquals("A request must not save before the replacement host renders it", accepted, active.progress.state.value.accepted)
            assertEquals(requested, active.initialLocator())
        }
        waitForSearchCommit(book, requested)
        assertParagraphVisible("two", "p179")
        val original = requireNotNull(saved(book))
        recordRestoreObservation("search", original)
        compose.onNodeWithTag("epub_bookmarks").performClick()
        compose.onNodeWithTag("epub_bookmark_add").performClick()
        waitFor("epub_bookmark_item")
        compose.onNodeWithText("닫기").performClick()
        compose.activityRule.scenario.recreate()
        waitFor("epub_ready")
        assertEquals(original, saved(book))
        assertRenderedNear(original)
        val beforeFontChange = recordRestoreObservation("recreation", original)
        assertParagraphVisible("two", "p179")
        val beforeSettings = reader().state.value
        compose.onNodeWithTag("epub_settings").performClick()
        compose.onNodeWithTag("font_size_increase").performScrollTo().performClick()
        compose.onNodeWithTag("reader_settings_apply").performClick()
        waitForSettingsApplied(beforeSettings.hostVersion,
            beforeSettings.settings.copy(fontSizeSp = beforeSettings.settings.fontSizeSp + 1))
        assertEquals(original, saved(book))
        assertRenderedNear(original)
        val afterFontChange = recordRestoreObservation("font-size", original)
        fun fontPixels(observation: org.json.JSONObject) = observation.getJSONObject("viewport")
            .getString("fontSize").removeSuffix("px").toDouble()
        assertTrue("The displayed paragraph must use the increased font size",
            fontPixels(afterFontChange) > fontPixels(beforeFontChange))
        assertParagraphVisible("two", "p179")
        compose.onNodeWithTag("epub_back").performClick()
        waitFor("continue_reading")
        compose.onNodeWithTag("continue_reading").performClick()
        waitFor("epub_ready")
        assertEquals(original, saved(book))
        assertRenderedNear(original)
        recordRestoreObservation("reopen", original)
        assertParagraphVisible("two", "p179")
        compose.onNodeWithTag("epub_bookmarks").performClick()
        waitFor("epub_bookmark_item")
        compose.onNodeWithTag("epub_bookmark_item").performClick()
        waitFor("epub_ready")
        assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
    }
    @Test fun searchJumpSurvivesHostReplacementAfterRequestWithoutPrematureProgressWrite() {
        observationFileName = "epub-search-reflow-observations.json"
        File(target.filesDir, observationFileName).writeText("[]")
        send("epub-product"); waitFor("epub_ready")
        val active = reader(); val book = active.book
        compose.activityRule.scenario.onActivity { active.search("two 문단 0179") }
        compose.waitUntil(20_000) { !active.state.value.searching && active.state.value.results.size == 1 }
        val requested = active.state.value.results.single()
        compose.activityRule.scenario.onActivity {
            assertEquals(org.bookreader.mobile.reader.ArchiveReaderPhase.READY, active.progress.state.value.phase)
            val accepted = active.progress.state.value.accepted
            active.navigate(requested)
            active.reflowViewport()
            assertEquals(org.bookreader.mobile.reader.ArchiveReaderPhase.REFLOWING, active.progress.state.value.phase)
            assertEquals(accepted, active.progress.state.value.accepted)
            assertEquals(requested, active.initialLocator())
        }
        waitForSearchCommit(book, requested)
        assertParagraphVisible("two", "p179")
        recordRestoreObservation("search-after-reflow", requireNotNull(saved(book)))
        val locator = org.json.JSONObject(requireNotNull(saved(book)).payload.locator.toString())
        assertEquals(requested.text.highlight, locator.getJSONObject("text").getString("highlight"))
    }

    private fun waitForSearchCommit(book: Book, requested: org.readium.r2.shared.publication.Locator) {
        val owner = reader()
        try {
            compose.waitUntil(20_000) {
                val locator = saved(book)?.payload?.locator ?: return@waitUntil false
                val json = org.json.JSONObject(locator.toString())
                json.getString("href").endsWith("two.xhtml") && json.getJSONObject("locations").optDouble("progression", 0.0) > .5 &&
                    json.optJSONObject("text")?.optString("highlight") == requested.text.highlight &&
                    owner.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY
            }
        } catch (failure: Throwable) {
            // Preserve the original timeout and capture fixture state even before search succeeds.
            runCatching { recordRestoreObservation("search-timeout", saved(book)) }
                .onFailure { failure.addSuppressed(it) }
            throw failure
        }
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
        assertEquals(true, reader().preferences().publisherStyles)
        assertEquals(raw, runBlocking { database.readerSettingsDao().global() })
        val beforeSettings = reader().state.value
        compose.onNodeWithTag("epub_settings").performClick()
        compose.onNodeWithTag("epub_publisher_styles").performScrollTo().performClick()
        compose.onNodeWithTag("reader_settings_apply").performClick()
        waitForSettingsApplied(beforeSettings.hostVersion, value.copy(epubPublisherStyles = false))
        assertEquals(false, reader().preferences().publisherStyles)
        assertEquals(-.02f, reader().state.value.settings.letterSpacingEm)
        val storedSettings = requireNotNull(runBlocking { database.readerSettingsDao().global() })
        assertEquals(value.copy(epubPublisherStyles = false), org.bookreader.mobile.settings.ReaderSettingsCodec.readGlobal(storedSettings.schemaVersion, storedSettings.settingsJson))
        compose.onNodeWithTag("epub_back").performClick(); waitFor("continue_reading")
        assertEquals(storedSettings, runBlocking { database.readerSettingsDao().global() })
    }
    /** Fixture-only observations survive cleanup even when the following assertion fails. */
    private fun recordRestoreObservation(stage: String, expected: EpubLocator?): org.json.JSONObject {
        val completed = java.util.concurrent.CountDownLatch(1)
        val observation = org.json.JSONObject().put("stage", stage)
            .put("expected", expected?.let { org.json.JSONObject(it.payload.locator.toString()) } ?: org.json.JSONObject.NULL)
        compose.activityRule.scenario.onActivity { activity ->
            val owner = ViewModelProvider(activity)[AppViewModel::class.java].state.value.epubReader!!
            observation.put("phase", owner.progress.state.value.phase.name)
                .put("hostVersion", owner.state.value.hostVersion)
                .put("generation", owner.progress.state.value.generation)
                .put("initialLocator", owner.initialLocator()?.toJSON())
                .put("error", owner.state.value.error)
            val wrapper = activity.supportFragmentManager.fragments.filterIsInstance<EpubHostFragment>().single()
            val navigator = wrapper.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single()
            observation.put("actual", navigator.currentLocator.value.toJSON())
            fun webViews(view: android.view.View): List<android.webkit.WebView> = when (view) {
                is android.webkit.WebView -> listOf(view)
                is android.view.ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
                else -> emptyList()
            }
            val web = webViews(activity.window.decorView).firstOrNull { it.isShown && it.url?.contains("/two.xhtml") == true }
            if (web == null) {
                observation.put("viewportUnavailable", true)
                completed.countDown()
                return@onActivity
            }
            web.evaluateJavascript("""
                (() => {
                    const p = document.getElementById('p179');
                    const rect = r => ({left:r.left, top:r.top, right:r.right, bottom:r.bottom});
                    const visible = r => r.bottom > 0 && r.top < innerHeight && r.right > 0 && r.left < innerWidth;
                    return JSON.stringify({width:innerWidth, height:innerHeight, scrollX, scrollY,
                        scrollHeight:document.documentElement.scrollHeight,
                        fontSize:getComputedStyle(p).fontSize, lineHeight:getComputedStyle(p).lineHeight,
                        target:rect(p.getBoundingClientRect()), fragments:Array.from(p.getClientRects(), rect),
                        visibleParagraphs:Array.from(document.querySelectorAll('p[id]'))
                            .filter(e => Array.from(e.getClientRects()).some(visible)).map(e => e.id)});
                })()
            """.trimIndent()) { result ->
                try {
                    val json = org.json.JSONTokener(result).nextValue() as String
                    observation.put("viewport", org.json.JSONObject(json))
                } finally { completed.countDown() }
            }
        }
        assertTrue("Fixture viewport observation must complete", completed.await(10, java.util.concurrent.TimeUnit.SECONDS))
        val report = File(target.filesDir, observationFileName)
        val observations = if (stage == "search" || !report.exists()) org.json.JSONArray()
            else org.json.JSONArray(report.readText())
        observations.put(observation)
        report.writeText(observations.toString(2))
        return observation
    }

    private fun waitForSettingsApplied(previousHostVersion: Int, expected: org.bookreader.mobile.settings.ReaderSettings) {
        val owner = reader()
        // The old READY node remains visible while the asynchronous settings write runs.
        // Require the new settings/host and its completed restoration, not just that node.
        compose.waitUntil(40_000) {
            val state = owner.state.value
            !state.settingsSaving && !state.settingsError && state.settings == expected &&
                state.hostVersion > previousHostVersion &&
                owner.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY
        }
        waitFor("epub_ready")
    }

    private fun assertParagraphVisible(chapter: String, id: String) {
        val visible = evaluateLoadedChapter(compose.activityRule.scenario, chapter,
            "(() => { const r = document.getElementById('$id').getBoundingClientRect(); return r.bottom > 0 && r.top < innerHeight && r.right > 0 && r.left < innerWidth; })()") { activity ->
            ViewModelProvider(activity)[AppViewModel::class.java].state.value.epubReader
                ?.progress?.state?.value?.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY
        }
        assertEquals("The searched paragraph must be in the visible viewport", "true", visible)
    }
    private fun assertRenderedNear(locator: EpubLocator) {
        val expected = org.json.JSONObject(locator.payload.locator.toString())
        // DOM scrolling and the SDK's debounced currentLocator notification finish separately.
        // Keep the exact same assertion, but wait for that asynchronous notification first.
        compose.waitUntil(10_000) {
            var settled = false
            compose.activityRule.scenario.onActivity { activity ->
                val wrapper = activity.supportFragmentManager.fragments.filterIsInstance<EpubHostFragment>().singleOrNull()
                val navigator = wrapper?.childFragmentManager?.fragments
                    ?.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>()?.singleOrNull()
                navigator?.currentLocator?.value?.toJSON()?.let { actual ->
                    settled = actual.getString("href") == expected.getString("href") &&
                        kotlin.math.abs(actual.getJSONObject("locations").optDouble("progression", 0.0) -
                            expected.getJSONObject("locations").optDouble("progression", 0.0)) <= .03
                }
            }
            settled
        }
        compose.activityRule.scenario.onActivity { activity ->
            val wrapper = activity.supportFragmentManager.fragments.filterIsInstance<EpubHostFragment>().single()
            val navigator = wrapper.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single()
            val actual = navigator.currentLocator.value.toJSON()
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
