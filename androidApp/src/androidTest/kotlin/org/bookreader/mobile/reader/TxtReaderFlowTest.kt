package org.bookreader.mobile.reader

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.AndroidDatabaseOwner
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.ReadingProgress
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.RoomBookRepository
import org.bookreader.mobile.repository.DatabaseFailureStage
import org.bookreader.mobile.repository.RoomProgressRepository
import org.bookreader.mobile.ui.AppViewModel
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.importing.DeleteResult
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual import → measured scroll → Room commit → library continue, including cache rebuild. */
class TxtReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private var initialIds: Set<String>? = null
    private lateinit var observerDatabase: BookReaderDatabase

    @Before fun preserveExistingBooks() {
        observerDatabase = createAndroidDatabase(target)
        // Open the fixture observer before reader actions, rather than opening a Room pool per poll.
        initialIds = books().map { it.id }.toSet()
    }
    @After fun cleanOnlyNewFixtureBooks() {
        try {
            compose.activityRule.scenario.close()
            val preservedIds = initialIds ?: return
            books().filter { it.id !in preservedIds && it.originalDisplayName.startsWith("../../reader-") }.forEach { book ->
                runBlocking { withContext(Dispatchers.IO) {
                    assertEquals(DeleteResult.Deleted, AndroidBookManagement(target).delete(book.id))
                } }
                val key = MessageDigest.getInstance("SHA-256").digest(requireNotNull(book.currentRevision).toByteArray())
                    .joinToString("") { "%02x".format(it) }
                File(target.cacheDir, "txt").listFiles()?.filter { it.name.startsWith(key) }?.forEach { it.delete() }
            }
        } finally { if (::observerDatabase.isInitialized) observerDatabase.close() }
    }

    @Test fun txtTocEditsExclusionsManualEntryAndJumpSurviveReopeningWithoutMovingRecordsDuringEdits() {
        sendFixture("reader-toc"); waitFor("reader_ready")
        val active = reader(); val book = active.book
        compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val origin = measuredVisibleLine(active)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == origin } == true }
        val before = requireNotNull(saved(book))
        val beforeBook = books().single { it.id == book.id }
        val file = File(File(target.filesDir,"managed"),book.managedRelativePath)
        val hash = digest(file)
        val body = runBlocking { active.range(0,active.state.value.cache.committedLength.toInt()) }
        val first = 0L; val second = body.indexOf("제1화 시작").toLong(); val third = body.indexOf("Chapter 2 Next").toLong()
        compose.readerControl("reader_toc").performClick()
        compose.waitUntil(10_000) { active.toc.state.value.ready }
        assertEquals(listOf(first,second,third),active.toc.state.value.entries.map { it.offset })
        assertEquals(before,saved(book))
        compose.onNodeWithTag("toc_edit_$first").performClick()
        compose.onNodeWithTag("toc_title").performTextReplacement("수정한 프롤로그")
        compose.onNodeWithTag("toc_save").performClick()
        compose.waitUntil(10_000) { active.toc.state.value.let { it.ready && it.savedVersion==1L } }
        compose.onNodeWithTag("toc_edit_$second").performClick()
        compose.onNodeWithTag("toc_exclude").performClick()
        compose.waitUntil(10_000) { active.toc.state.value.let { it.ready && it.savedVersion==2L } }
        assertEquals(listOf(first,third),active.toc.state.value.entries.map { it.offset })
        compose.onNodeWithTag("toc_excluded").performClick()
        compose.onNodeWithTag("toc_restore_$second").performClick()
        compose.waitUntil(10_000) { active.toc.state.value.let { it.ready && it.savedVersion==3L } }
        compose.onNodeWithTag("toc_add_current").performClick()
        compose.onNodeWithTag("toc_title").performTextReplacement("내 위치")
        compose.onNodeWithTag("toc_save").performClick()
        compose.waitUntil(10_000) { active.toc.state.value.let { it.ready && it.savedVersion==4L } }
        assertEquals(listOf(first,origin,second,third).sorted(),active.toc.state.value.entries.map { it.offset })
        assertEquals(before,saved(book));assertEquals(beforeBook,books().single { it.id==book.id })
        ReaderFixtureScreenshot.capture("phase3-txt-toc.png")
        compose.onNodeWithTag("toc_entries").performScrollToNode(hasTestTag("toc_jump_$third"))
        compose.onNodeWithTag("toc_jump_$third").performClick()
        compose.waitUntil(10_000) { active.state.value.let { it.phase==TxtReaderPhase.READY && it.targetOffset==third } }
        assertAnchorInFirstMeasuredLine(active,third)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it)==third }==true }
        val jumped=requireNotNull(saved(book))
        compose.readerControl("reader_back").performClick();waitFor("library_content")
        compose.activityRule.scenario.recreate();waitFor("library_content")
        compose.onNodeWithTag("read_${book.id}").performScrollTo().performClick();waitFor("reader_ready")
        assertAnchorInFirstMeasuredLine(reader(),third)
        assertEquals(jumped,saved(book))
        compose.readerControl("reader_toc").performClick()
        compose.waitUntil(10_000) { reader().toc.state.value.ready }
        assertEquals("수정한 프롤로그",reader().toc.state.value.entries.first().title)
        assertEquals("내 위치",reader().toc.state.value.entries.single { it.offset==origin }.title)
        assertEquals(hash,digest(file));assertEquals(jumped,saved(book))
    }

    @Test fun quickSettingsCancelAndApplyPreserveExactProgressAndUnrelatedBookPreferences() {
        val originalSettings = runBlocking { observerDatabase.readerSettingsDao().global() }
        try {
            sendFixture("reader-pages"); waitFor("reader_ready")
            val active = reader()
            val access = org.bookreader.mobile.settings.ReaderSettingsRepository(observerDatabase)
            val defaults = org.bookreader.mobile.settings.ReaderSettings()
            val preferences = org.bookreader.mobile.settings.BookReaderPreferences(
                readingMode = org.bookreader.mobile.settings.TxtReadingMode.SCROLL,
                comicDirection = org.bookreader.mobile.settings.ComicDirection.RTL,
                comicFit = org.bookreader.mobile.settings.ComicFit.WIDTH)
            val expectedLoaded = requireNotNull(active.state.value.settingsLoaded)
            assertEquals(org.bookreader.mobile.settings.ReaderSettingsWrite.SAVED,
                runBlocking { access.save(expectedLoaded, defaults, preferences) })
            compose.runOnIdle { active.retrySettings() }
            compose.waitUntil(15_000) { active.state.value.let {
                it.phase == TxtReaderPhase.READY && it.settingsLoaded?.book == preferences && it.settings == defaults.copy(
                    comicDirection = preferences.comicDirection!!, comicFit = preferences.comicFit!!) }
            }
            compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
            compose.waitForIdle()
            val anchor = measuredVisibleLine(active)
            compose.waitUntil(10_000) { saved(active.book)?.let { offset(it) == anchor && anchor > 0 } == true }
            val before = requireNotNull(saved(active.book))
            val rows = runBlocking { observerDatabase.readerSettingsDao().read(active.book.id) }
            val bounds = compose.onNodeWithTag("reader_ready").fetchSemanticsNode().boundsInRoot
            val generation = active.state.value.restoreGeneration
            compose.readerControl("reader_settings").performClick()
            compose.onNodeWithTag("reader_font_import").assertDoesNotExist()
            compose.onNodeWithTag("reader_mode_PAGES").performClick()
            compose.onNodeWithTag("font_size_increase").performScrollTo().performClick()
            compose.onNodeWithTag("reader_weight_BOLD").performScrollTo().performClick()
            compose.onNodeWithTag("reader_theme_SEPIA").performScrollTo().performClick()
            compose.onNodeWithTag("window_brightness_increase").performScrollTo().performClick()
            assertEquals(rows, runBlocking { observerDatabase.readerSettingsDao().read(active.book.id) })
            assertEquals(before, saved(active.book))
            compose.onNodeWithTag("reader_settings_close").performClick()
            compose.waitForIdle()
            assertEquals(generation, active.state.value.restoreGeneration)
            assertEquals(bounds, compose.onNodeWithTag("reader_ready").fetchSemanticsNode().boundsInRoot)
            assertEquals(rows, runBlocking { observerDatabase.readerSettingsDao().read(active.book.id) })
            assertEquals(before, saved(active.book))
            ReaderFixtureScreenshot.capture("phase3-reader-tools.png")

            compose.readerControl("reader_settings").performClick()
            compose.onNodeWithTag("reader_mode_inherit").performScrollTo().performClick()
            for (tag in listOf("font_size_increase", "line_height_increase", "margin_increase",
                "reader_font_SERIF", "reader_weight_BOLD", "reader_theme_SEPIA", "reader_transition_SLIDE", "window_brightness_increase")) {
                compose.onNodeWithTag(tag).performScrollTo().performClick()
            }
            compose.onNodeWithTag("font_size_increase").performScrollTo().assertIsDisplayed()
            ReaderFixtureScreenshot.capture("phase3-reader-quick-settings.png")
            compose.onNodeWithTag("reader_settings_apply").performClick()
            val expected = defaults.copy(fontSizeSp = 19f, lineHeight = 1.7f, marginDp = 20,
                font = org.bookreader.mobile.settings.ReaderFont.SERIF,
                fontWeight = org.bookreader.mobile.settings.ReaderFontWeight.BOLD,
                theme = org.bookreader.mobile.settings.ReaderTheme.SEPIA,
                pageTransition = org.bookreader.mobile.settings.PageTransition.SLIDE,
                window = defaults.window.copy(brightness = .6f))
            compose.waitUntil(15_000) { active.state.value.let {
                it.phase == TxtReaderPhase.READY && !it.settingsSaving && it.settingsLoaded?.global == expected }
            }
            val loaded = (runBlocking { access.load(active.book.id) } as org.bookreader.mobile.settings.ReaderSettingsRead.Ready).value
            assertEquals(expected, loaded.global)
            assertEquals(preferences.copy(readingMode = null), loaded.book)
            assertEquals(anchor, active.state.value.targetOffset)
            assertEquals(before, saved(active.book))
            assertAnchorInFirstMeasuredLine(active, anchor)
            compose.readerControl("reader_back").performClick(); waitFor("library_content")
            compose.activityRule.scenario.recreate(); waitFor("library_content")
            compose.onNodeWithTag("read_${active.book.id}").performScrollTo().performClick(); waitFor("reader_ready")
            assertEquals(expected, reader().state.value.settingsLoaded?.global)
            assertEquals(preferences.copy(readingMode = null), reader().state.value.settingsLoaded?.book)
            assertEquals(before, saved(active.book))
            assertAnchorInFirstMeasuredLine(reader(), anchor)
        } finally { restoreReaderGlobalSettings(originalSettings) }
    }

    @Test fun customFontPickerRejectsInvalidFilesAndPersistsWeightWithoutMovingAnchor() {
        val originalSettings = runBlocking { observerDatabase.readerSettingsDao().global() }
        val store = UserFontStore(target)
        val existingFonts = store.list().map { it.id }.toSet()
        try {
            sendFixture("reader-pages"); waitFor("reader_ready")
            val active = reader()
            compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
            compose.waitForIdle()
            compose.waitUntil(10_000) { active.state.value.stableOffset?.let { it > 0 } == true }
            val anchor = measuredVisibleLine(active)
            compose.waitUntil(10_000) { saved(active.book)?.let { offset(it) == anchor } == true }
            val before = saved(active.book)
            compose.readerControl("reader_settings").performClick()
            fun pick(name: String) {
                val monitor = fontPicker(instrumentation, name)
                try {
                    compose.advancedReaderSetting("reader_font_import").performClick()
                    compose.waitUntil(15_000) {
                        compose.onAllNodesWithTag("reader_font_message").fetchSemanticsNodes(atLeastOneRootRequired = false).any { node ->
                            node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
                                .contains(if (name == "font-gothic") "등록 완료" else "지원하지 않는")
                        }
                    }
                    compose.onNodeWithTag("reader_font_import").assertIsEnabled()
                } finally { instrumentation.removeMonitor(monitor) }
            }
            pick("font-invalid")
            assertEquals(existingFonts, store.list().map { it.id }.toSet())
            assertEquals(before, saved(active.book))
            pick("font-gothic")
            val id = MessageDigest.getInstance("SHA-256").digest(target.assets.open("reader-fonts/NanumGothic-Regular.ttf").use { it.readBytes() })
                .joinToString("") { "%02x".format(it) }
            val imported = requireNotNull(store.find(id))
            assertEquals("font-gothic.ttf", imported.label)
            compose.onNodeWithTag("reader_weight_BOLD").performScrollTo().performClick()
            val generation = active.state.value.restoreGeneration
            compose.onNodeWithTag("reader_mode_PAGES").performScrollTo().performClick()
            compose.onNodeWithTag("reader_settings_apply").performClick()
            compose.waitUntil(20_000) { active.state.value.let { it.restoreGeneration > generation && it.phase == TxtReaderPhase.READY &&
                !it.settingsSaving && it.settings.customFontId == id && it.settings.fontWeight == org.bookreader.mobile.settings.ReaderFontWeight.BOLD &&
                it.settings.readingMode == org.bookreader.mobile.settings.TxtReadingMode.PAGES } }
            assertEquals(id, active.state.value.settings.customFontId)
            assertEquals(org.bookreader.mobile.settings.ReaderFontWeight.BOLD, active.state.value.settings.fontWeight)
            assertEquals(anchor, active.state.value.targetOffset)
            assertEquals(before, saved(active.book))
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("reader_page").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, layouts.single().layoutInput.style.fontWeight)
            // Compose's AndroidFileFont uses object identity, not value equality.
            val displayedFonts = layouts.single().layoutInput.style.fontFamily as androidx.compose.ui.text.font.FontListFontFamily
            assertEquals(androidx.compose.ui.text.font.Font(imported.file).toString(), displayedFonts.fonts.single().toString())
            ReaderFixtureScreenshot.capture("txt-custom-font-bold.png")
            compose.activityRule.scenario.recreate(); waitFor("reader_ready")
            assertEquals(id, reader().state.value.settings.customFontId)
            assertEquals(org.bookreader.mobile.settings.ReaderFontWeight.BOLD, reader().state.value.settings.fontWeight)
            assertEquals(before, saved(active.book))
            // Import is an app-owned copy: no persistable provider grant is needed on reopening.
            compose.readerControl("reader_back").performClick(); waitFor("continue_reading")
            compose.onNodeWithTag("continue_reading").performClick(); waitFor("reader_ready")
            assertEquals(id, reader().state.value.settings.customFontId)
            assertTrue(imported.file.isFile)
            compose.readerControl("reader_settings").performClick()
            val registered = store.list().map { it.id }.toSet()
            pick("font-gothic")
            assertEquals(registered, store.list().map { it.id }.toSet())
            compose.advancedReaderSetting("reader_font_remove_$id").assertIsNotEnabled()
            compose.onNodeWithTag("reader_font_SANS").performScrollTo().performClick()
            compose.onNodeWithTag("reader_weight_NORMAL").performScrollTo().performClick()
            compose.onNodeWithTag("reader_settings_apply").performClick()
            compose.waitUntil(20_000) { reader().state.value.let { it.settings.customFontId == null && it.phase == TxtReaderPhase.READY } }
            assertEquals(before, saved(active.book))
            if (id !in existingFonts) {
                compose.readerControl("reader_settings").performClick()
                compose.advancedReaderSetting("reader_font_remove_$id").performClick()
                compose.onNodeWithTag("reader_font_confirm_remove").performClick()
                compose.waitUntil(10_000) { store.find(id) == null }
                assertEquals(before, saved(active.book))
            }
        } finally {
            compose.activityRule.scenario.close()
            runBlocking {
                if (originalSettings == null) observerDatabase.useWriterConnection { it.execSQL("DELETE FROM reader_global_settings WHERE id = 'global'") }
                else observerDatabase.readerSettingsDao().putGlobal(originalSettings)
                store.list().filter { it.id !in existingFonts }.forEach { store.remove(it.id) }
            }
        }
    }

    @Test fun measuredCanonicalLineIsSavedAndContinueRestoresAfterCacheLossAndRecreation() {
        sendFixture("reader-scroll")
        waitFor("reader_ready")
        val controller = reader()
        val book = controller.book
        val original = File(File(target.filesDir, "managed"), book.managedRelativePath)
        val originalHash = digest(original)
        repeat(8) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitUntil(10_000) { controller.state.value.stableOffset?.let { it > 0 } == true }
        val visible = measuredVisibleLine(controller)
        assertEquals(visible, controller.state.value.stableOffset)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == visible } == true }
        val committed = requireNotNull(saved(book))
        ReaderFixtureScreenshot.capture("txt-reader-generated-scroll.png")
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        // Only this fixture's derived caches are removed. Private original and Room stay intact.
        val key = MessageDigest.getInstance("SHA-256").digest(requireNotNull(book.currentRevision).toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(target.cacheDir, "txt").listFiles()?.filter { it.name.startsWith(key) }?.forEach {
            assertTrue(it.delete())
        }
        assertEquals(committed, saved(book))
        assertEquals(originalHash, digest(original))
        compose.onNodeWithTag("continue_reading").performClick()
        waitFor("reader_ready")
        val continued = reader()
        assertEquals(visible, continued.state.value.targetOffset)
        assertEquals(visible, measuredVisibleLine(continued))
        // Opening/restoration alone never replaces the committed locator with an initial callback.
        assertEquals(committed, saved(book))
        compose.activityRule.scenario.recreate()
        waitFor("reader_ready")
        assertSame(continued, reader())
        assertEquals(visible, measuredVisibleLine(reader()))
        assertEquals(committed, saved(book))
    }

    @Test fun sixtySecondsOfContinuousTouchKeepsCommittingMeasuredAnchors() {
        val reportFile = File(target.filesDir, "txt-continuous-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        sendFixture("reader-continuous")
        waitFor("reader_ready")
        val controller = reader()
        repeat(8) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitUntil(10_000) { saved(controller.book)?.let { offset(it) > 0 } == true }
        val before = requireNotNull(saved(controller.book))
        val node = compose.onNodeWithTag("reader_text").fetchSemanticsNode()
        val x = node.positionOnScreen.x + node.size.width / 2f
        val upper = node.positionOnScreen.y + node.size.height * 0.3f
        val lower = node.positionOnScreen.y + node.size.height * 0.7f
        val downTime = SystemClock.uptimeMillis()
        val start = SystemClock.elapsedRealtime()
        val commits = JSONArray()
        val anchors = JSONArray()
        val inputTimes = JSONArray()
        var lastSequence = before.writerSequence
        var lastOffset = controller.state.value.stableOffset
        var y = lower
        var held = false
        var passed = false
        val report = JSONObject().apply {
            put("fixture_id", "reader-continuous")
            put("fixture_sha256", controller.book.sourceSha256)
            put("fixture_bytes", controller.book.sourceByteSize)
            put("build_fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("abi", Build.SUPPORTED_ABIS.first())
            put("app_version", target.packageManager.getPackageInfo(target.packageName, 0).versionName)
            put("writer_epoch", before.writerSessionEpoch)
            put("initial_sequence", before.writerSequence)
            put("commits", commits)
            put("anchors", anchors)
            put("input_elapsed_ms", inputTimes)
            put("measurement", "Monotonic real-time touch and independent Room observations; not SQL write-ack latency")
        }
        fun inject(action: Int) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue("Actual pointer injection failed", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
        try {
            inject(MotionEvent.ACTION_DOWN)
            held = true
            while (SystemClock.elapsedRealtime() - start < 60_000) {
                val elapsed = SystemClock.elapsedRealtime() - start
                // One held pointer reverses every two seconds, away from book/viewport boundaries.
                val phase = (elapsed % 4_000) / 2_000f
                y = lower - (lower - upper) * if (phase <= 1f) phase else 2f - phase
                inject(MotionEvent.ACTION_MOVE)
                inputTimes.put(SystemClock.elapsedRealtime() - start)
                compose.waitForIdle() // Pump measured Compose layout without advancing wall-clock time.
                val state = controller.state.value
                assertEquals(TxtReaderPhase.READY, state.phase)
                assertFalse(state.saveError)
                if (state.stableOffset != lastOffset) {
                    lastOffset = state.stableOffset
                    anchors.put(JSONObject().put("elapsed_ms", SystemClock.elapsedRealtime() - start)
                        .put("offset", requireNotNull(lastOffset)))
                }
                val committed = requireNotNull(saved(controller.book))
                assertEquals(before.writerSessionEpoch, committed.writerSessionEpoch)
                assertTrue(committed.writerSequence >= lastSequence)
                if (committed.writerSequence > lastSequence) {
                    lastSequence = committed.writerSequence
                    commits.put(JSONObject().put("elapsed_ms", SystemClock.elapsedRealtime() - start)
                        .put("sequence", committed.writerSequence).put("offset", offset(committed)))
                }
                // Real input pacing; no manual flush/close/restore or fabricated visibleAnchor calls.
                SystemClock.sleep(50)
            }
            val movementDuration = SystemClock.elapsedRealtime() - start
            report.put("movement_duration_ms", movementDuration)
            val inputGaps = (listOf(0L) + (0 until inputTimes.length()).map { inputTimes.getLong(it) } + movementDuration)
                .zipWithNext { a, b -> b - a }
            report.put("max_input_gap_ms", inputGaps.max())
            inject(MotionEvent.ACTION_UP)
            held = false
            compose.waitForIdle()
            val finalAnchor = measuredVisibleLine(controller)
            assertEquals(finalAnchor, controller.state.value.stableOffset)
            compose.waitUntil(10_000) { saved(controller.book)?.let { offset(it) == finalAnchor } == true }
            report.put("gesture_end_to_settled_observation_ms", SystemClock.elapsedRealtime() - start - movementDuration)
            report.put("final_offset", finalAnchor)
            report.put("final_sequence", requireNotNull(saved(controller.book)).writerSequence)
            assertTrue("Must move for at least 60 real seconds", movementDuration >= 60_000)
            assertTrue("Held input must continue at least once per second", inputGaps.max() <= 1_000)
            assertTrue("Measured anchor must change throughout the held gesture", anchors.length() >= 120)
            assertTrue("Periodic durable commits must occur before pointer-up", commits.length() >= 12)
            val observedTimes = listOf(0L) + (0 until commits.length()).map { commits.getJSONObject(it).getLong("elapsed_ms") } + movementDuration
            val maxGap = observedTimes.zipWithNext { a, b -> b - a }.max()
            report.put("max_observed_commit_gap_ms", maxGap)
            assertTrue("Durable commit observation gap exceeded 5s: $maxGap", maxGap <= 5_000)
            for (bucket in 0 until 6) {
                assertTrue("Missing measured movement in 10s window $bucket", (0 until anchors.length()).any {
                    anchors.getJSONObject(it).getLong("elapsed_ms") / 10_000 == bucket.toLong()
                })
                assertTrue("Missing commit in 10s window $bucket", (0 until commits.length()).any {
                    commits.getJSONObject(it).getLong("elapsed_ms") / 10_000 == bucket.toLong()
                })
            }
            passed = true
        } finally {
            try { if (held) inject(MotionEvent.ACTION_CANCEL) }
            finally {
                report.put("status", if (passed) "PASS" else "FAIL")
                reportFile.writeText(report.toString(2))
            }
        }
    }

    @Test fun sqliteFullShowsSaveErrorPreservesRecordsAndRetriesAfterCapacityReturns() {
        val reportFile = File(target.filesDir, "txt-full-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        // Another real imported book/progress must remain unchanged throughout the failure.
        sendFixture("reader-full-other")
        waitFor("reader_ready")
        val other = reader().book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        val otherProgress = requireNotNull(saved(other))
        val otherRecord = books().single { it.id == other.id }
        val otherFile = File(File(target.filesDir, "managed"), other.managedRelativePath)
        val otherHash = digest(otherFile)

        sendFixture("reader-full")
        waitFor("reader_ready")
        val controller = reader()
        val book = controller.book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val initialAnchor = measuredVisibleLine(controller)
        assertTrue(initialAnchor > 0)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == initialAnchor } == true }
        val before = requireNotNull(saved(book))
        val beforeBook = books().single { it.id == book.id }
        val original = File(File(target.filesDir, "managed"), book.managedRelativePath)
        val originalHash = digest(original)
        // Borrow the same one-writer Room pool used by the real Reader; never close it here.
        val database = AndroidDatabaseOwner.borrow(target)
        fun scalar(sql: String): Long = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared(sql) { check(it.step()); it.getLong(0) }
        } }
        fun execute(sql: String) = runBlocking { database.useWriterConnection { it.execSQL(sql) } }
        val originalLimit = scalar("PRAGMA max_page_count")
        val beforeOrder = scalar("SELECT value FROM app_metadata WHERE `key` = 'read_order'")
        val report = JSONObject().apply {
            put("fixture_id", "reader-full")
            put("fixture_sha256", book.sourceSha256)
            put("fixture_bytes", book.sourceByteSize)
            put("other_fixture_sha256", other.sourceSha256)
            put("build_fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("abi", Build.SUPPORTED_ABIS.first())
            put("mechanism", "Native SQLite max_page_count plus test-only growth trigger; not filesystem ENOSPC")
            put("initial_offset", initialAnchor)
            put("initial_sequence", before.writerSequence)
            put("writer_epoch", before.writerSessionEpoch)
        }
        var passed = false
        try {
            execute("CREATE TABLE full_test_payload (payload BLOB NOT NULL)")
            val bytes = (scalar("PRAGMA page_count") + 4) * scalar("PRAGMA page_size")
            check(bytes in 1..1_048_576)
            // Test-only growth happens after the normal progress UPDATE, in its real transaction.
            check(book.id.matches(Regex("[a-f0-9-]+")))
            execute("CREATE TRIGGER full_test_write AFTER UPDATE ON reading_progress " +
                "WHEN NEW.bookId = '${book.id}' BEGIN INSERT INTO full_test_payload VALUES (zeroblob($bytes)); END")
            val pages = scalar("PRAGMA page_count")
            assertEquals(pages, scalar("PRAGMA max_page_count = $pages"))
            report.put("page_limit", pages).put("page_size", scalar("PRAGMA page_size")).put("growth_bytes", bytes)
            // Confirm code 13 from the native engine on this same constrained writer connection.
            val nativeFailure = runCatching { execute("INSERT INTO full_test_payload VALUES (zeroblob($bytes))") }.exceptionOrNull()
            assertTrue(nativeFailure is SQLiteException)
            assertTrue(nativeFailure?.message.orEmpty().contains("Error code: 13,"))
            val nativeType = requireNotNull(requireNotNull(nativeFailure)::class.simpleName)
            report.put("native_probe_code", 13).put("native_probe_type", nativeType)

            compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
            // The error banner changes the viewport and starts asynchronous reflow.
            // Measure only after its restoration, never during the temporary scroll-to-item frame.
            waitFor("reader_save_error")
            waitFor("reader_ready")
            compose.waitForIdle()
            val pendingAnchor = measuredVisibleLine(controller)
            assertNotEquals(initialAnchor, pendingAnchor)
            val failed = controller.state.value
            assertEquals(TxtReaderPhase.READY, failed.phase)
            assertEquals(pendingAnchor, failed.stableOffset)
            assertTrue(failed.saveError)
            assertEquals(ReaderFailureStage.SAVE, failed.failureDiagnostic?.stage)
            assertEquals(DatabaseFailureStage.SAVE_COMMIT, failed.failureDiagnostic?.database?.stage)
            assertTrue(failed.failureDiagnostic?.database?.exceptionTypes?.contains(nativeType) == true)
            assertEquals(before, saved(book))
            assertEquals(beforeBook, books().single { it.id == book.id })
            assertEquals(otherProgress, saved(other))
            assertEquals(otherRecord, books().single { it.id == other.id })
            assertEquals(beforeOrder, scalar("SELECT value FROM app_metadata WHERE `key` = 'read_order'"))
            assertEquals(0L, scalar("SELECT count(*) FROM full_test_payload"))
            assertEquals(originalHash, digest(original))
            assertEquals(otherHash, digest(otherFile))
            report.put("pending_offset", pendingAnchor).put("save_error_visible", true)
                .put("records_preserved_on_failure", true).put("managed_files_preserved", true)

            // Keep the trigger. Capacity relief must make the same actual write succeed, via retry.
            assertEquals(originalLimit, scalar("PRAGMA max_page_count = $originalLimit"))
            compose.waitUntil(10_000) {
                !controller.state.value.saveError && saved(book)?.let { offset(it) == pendingAnchor } == true
            }
            val committed = requireNotNull(saved(book))
            compose.onNodeWithTag("reader_save_error").assertDoesNotExist()
            assertNull(controller.state.value.failureDiagnostic)
            assertEquals(before.writerSessionEpoch, committed.writerSessionEpoch)
            assertTrue(committed.writerSequence > before.writerSequence)
            assertEquals(1L, scalar("SELECT count(*) FROM full_test_payload"))
            assertEquals(otherProgress, saved(other))
            assertEquals(otherRecord, books().single { it.id == other.id })
            assertEquals(beforeBook, books().single { it.id == book.id })
            assertEquals(beforeOrder, scalar("SELECT value FROM app_metadata WHERE `key` = 'read_order'"))
            assertEquals(originalHash, digest(original))
            assertEquals(otherHash, digest(otherFile))
            report.put("retry_offset", offset(committed)).put("retry_sequence", committed.writerSequence)
                .put("retry_cleared_error", true).put("other_book_preserved_after_retry", true)
            passed = true
        } finally {
            try {
                assertEquals(originalLimit, scalar("PRAGMA max_page_count = $originalLimit"))
                execute("DROP TRIGGER IF EXISTS full_test_write")
                execute("DROP TABLE IF EXISTS full_test_payload")
                report.put("fixture_limit_and_schema_restored", true)
            } finally {
                report.put("status", if (passed && report.optBoolean("fixture_limit_and_schema_restored")) "PASS" else "FAIL")
                reportFile.writeText(report.toString(2))
            }
        }
    }

    @Test fun missingManagedCopyShowsRetryAndPreservesCommittedProgress() {
        sendFixture("reader-failure")
        waitFor("reader_ready")
        val controller = reader()
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitUntil(10_000) { controller.state.value.stableOffset?.let { it > 0 } == true }
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        val book = controller.book
        val committed = requireNotNull(saved(book))
        val original = File(File(target.filesDir, "managed"), book.managedRelativePath)
        val held = File(original.parentFile, "test-held-original")
        assertTrue(original.renameTo(held))
        try {
            compose.onNodeWithTag("read_${book.id}").performClick()
            waitFor("reader_error")
            assertEquals(committed, saved(book))
            compose.onNodeWithTag("reader_retry").assertIsDisplayed()
            assertTrue(held.renameTo(original))
            compose.onNodeWithTag("reader_retry").performClick()
            waitFor("reader_ready")
            assertEquals(offset(committed), reader().state.value.targetOffset)
            assertEquals(committed, saved(book))
        } finally {
            if (held.exists()) held.renameTo(original)
        }
    }

    @Test fun nativeProgressQueryFailureShowsRetryWithoutChangingRecords() {
        verifyProgressReadFailure(nativeQueryFailure = true)
    }

    @Test fun malformedLocatorShowsRetryWithoutReplacingStoredRecord() {
        verifyProgressReadFailure(nativeQueryFailure = false)
    }

    private fun verifyProgressReadFailure(nativeQueryFailure: Boolean) {
        val fixture = if (nativeQueryFailure) "reader-query-error" else "reader-locator-error"
        val reportFile = File(target.filesDir, "$fixture-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        sendFixture("reader-read-other")
        waitFor("reader_ready")
        val other = reader().book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        val otherProgress = requireNotNull(saved(other))
        sendFixture(fixture)
        waitFor("reader_ready")
        val book = reader().book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        val committed = requireNotNull(saved(book))
        assertTrue(offset(committed) > 0)
        val beforeBooks = books()
        val beforeBook = beforeBooks.single { it.id == book.id }
        val originals = listOf(book, other).map {
            File(File(target.filesDir, "managed"), it.managedRelativePath)
        }
        val hashes = originals.map(::digest)
        // Keep the app-owned pool open. DDL and repair use its serialized writer connection.
        val database = AndroidDatabaseOwner.borrow(target)
        runBlocking {
            for ((owner, progress) in listOf(book to committed, other to otherProgress)) {
                database.bookmarkDao().insert(org.bookreader.mobile.database.BookmarkEntity(
                    "read-error-${owner.id}", owner.id, requireNotNull(owner.currentRevision), "txt", 1,
                    org.bookreader.mobile.locator.LocatorCodec().encode(progress.locator), "Own fixture bookmark", 1, 1))
            }
        }
        fun bookmarkRows(): List<List<String?>> = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared("SELECT * FROM bookmarks ORDER BY id") { statement ->
                buildList { while (statement.step()) add((0 until 9).map {
                    if (statement.isNull(it)) null else statement.getText(it)
                }) }
            }
        } }
        val originalBookmarks = bookmarkRows()
        fun execute(sql: String) = runBlocking { database.useWriterConnection { it.execSQL(sql) } }
        fun records(): List<List<String?>> = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared("SELECT * FROM reading_progress ORDER BY 1, 2") { statement ->
                buildList {
                    while (statement.step()) add((0 until 9).map {
                        if (statement.isNull(it)) null else statement.getText(it)
                    })
                }
            }
        } }
        fun readOrder(): Long = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared("SELECT value FROM app_metadata WHERE `key` = 'read_order'") {
                check(it.step()); it.getLong(0)
            }
        } }
        fun locatorJson(value: String) = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared("UPDATE reading_progress SET locatorJson = ? WHERE bookId = ? AND contentRevision = ?") {
                it.bindText(1, value); it.bindText(2, book.id)
                it.bindText(3, requireNotNull(book.currentRevision)); it.step()
            }
        } }
        val originalRows = records()
        val originalJson = requireNotNull(originalRows.single { it[0] == book.id }[4])
        val beforeOrder = readOrder()
        var faultInstalled = false
        var passed = false
        val report = JSONObject().apply {
            put("fixture_id", fixture).put("fixture_sha256", book.sourceSha256)
            put("fixture_bytes", book.sourceByteSize).put("other_fixture_sha256", other.sourceSha256)
            put("api", Build.VERSION.SDK_INT).put("build_fingerprint", Build.FINGERPRINT)
            put("mechanism", if (nativeQueryFailure) "temporary query-column rename; native SQLite query failure"
                else "test-only malformed locator JSON; real Room repository decoding")
            put("initial_offset", offset(committed)).put("initial_sequence", committed.writerSequence)
            put("initial_epoch", beforeBook.activeSessionEpoch)
        }
        try {
            if (nativeQueryFailure) {
                // Values, rows and foreign-key/index definitions survive the reversible rename.
                // DAO WHERE bookId then fails in SQLite; no fake Error or closed borrowed DB.
                execute("ALTER TABLE reading_progress RENAME COLUMN bookId TO fixture_held_book_id")
            } else locatorJson("{malformed-test-locator")
            faultInstalled = true
            val failedRows = records()
            if (nativeQueryFailure) {
                assertTrue("DDL must preserve every progress value", originalRows == failedRows)
                val nativeFailure = runCatching { runBlocking {
                    observerDatabase.progressDao().findProgress(book.id, requireNotNull(book.currentRevision))
                } }.exceptionOrNull()
                assertTrue(nativeFailure is SQLiteException)
                assertTrue(nativeFailure?.message.orEmpty().contains("Error code: 1,"))
                report.put("native_probe_code", 1)
                report.put("native_probe_type", requireNotNull(nativeFailure)::class.simpleName)
            }
            compose.onNodeWithTag("read_${book.id}").performClick()
            waitFor("reader_error")
            val failedController = reader()
            fun assertPreservedError() {
                val state = failedController.state.value
                assertEquals(TxtReaderPhase.ERROR, state.phase)
                assertEquals(ReaderFailureStage.LOAD_PROGRESS, state.failureDiagnostic?.stage)
                assertEquals(if (nativeQueryFailure) "DATABASE_UNAVAILABLE" else "INVALID_RECORD",
                    state.failureDiagnostic?.resultCode)
                if (nativeQueryFailure) {
                    assertEquals(DatabaseFailureStage.READ_PROGRESS, state.failureDiagnostic?.database?.stage)
                    assertTrue(state.failureDiagnostic?.database?.exceptionTypes?.contains(report.getString("native_probe_type")) == true)
                } else assertNull(state.failureDiagnostic?.database)
                assertNull(state.stableOffset)
                compose.onNodeWithTag("reader_retry").assertIsDisplayed()
                compose.onNodeWithTag("reader_text").assertDoesNotExist()
                assertTrue("Error/retry must preserve all raw progress columns", failedRows == records())
                assertTrue("Error/retry must preserve every book including epoch and read order", beforeBooks == books())
                assertEquals(beforeOrder, readOrder())
                assertEquals(hashes, originals.map(::digest))
                assertTrue("Query errors and retries preserve all bookmark columns", originalBookmarks == bookmarkRows())
                compose.readerControl("reader_bookmarks").assertIsNotEnabled()
            }
            assertPreservedError()
            repeat(2) {
                val generation = failedController.state.value.restoreGeneration
                compose.onNodeWithTag("reader_retry").performClick()
                compose.waitUntil(10_000) {
                    val state = failedController.state.value
                    state.restoreGeneration > generation && state.phase == TxtReaderPhase.ERROR
                }
                // Observe beyond the normal 1s sampling period while the read remains failed.
                SystemClock.sleep(1_250)
                assertPreservedError()
            }
            report.put("failed_retries", 2).put("error_ui_visible", true)
                .put("all_records_preserved_on_error", true).put("managed_files_preserved", true)
            if (nativeQueryFailure) execute("ALTER TABLE reading_progress RENAME COLUMN fixture_held_book_id TO bookId")
            else locatorJson(originalJson)
            faultInstalled = false
            assertTrue("Fixture repair restores original values", originalRows == records())
            compose.onNodeWithTag("reader_retry").performClick()
            waitFor("reader_ready")
            compose.waitForIdle()
            assertSame(failedController, reader())
            assertEquals(offset(committed), reader().state.value.targetOffset)
            assertEquals(offset(committed), measuredVisibleLine(reader()))
            assertEquals(committed, saved(book))
            assertEquals(otherProgress, saved(other))
            assertTrue("Restoration must not write a new progress record", originalRows == records())
            val afterBooks = books()
            assertEquals(beforeBooks.filter { it.id != book.id }, afterBooks.filter { it.id != book.id })
            val restoredBook = afterBooks.single { it.id == book.id }
            assertEquals(beforeBook.activeSessionEpoch + 1, restoredBook.activeSessionEpoch)
            assertEquals(beforeOrder + 1, readOrder())
            assertEquals(hashes, originals.map(::digest))
            assertNull(reader().state.value.failureDiagnostic)
            assertTrue("Recovery preserves all bookmarks", originalBookmarks == bookmarkRows())
            report.put("bookmarks_preserved", true)
            compose.onNodeWithTag("reader_error").assertDoesNotExist()
            report.put("restored_offset", measuredVisibleLine(reader()))
                .put("restored_epoch", restoredBook.activeSessionEpoch)
                .put("retry_cleared_error", true).put("progress_unchanged_after_restore", true)
                .put("other_book_preserved_after_retry", true)
            // Successful retry must also re-enable ordinary user-driven saving in the new epoch.
            compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
            compose.waitForIdle()
            val moved = measuredVisibleLine(reader())
            assertNotEquals(offset(committed), moved)
            compose.waitUntil(10_000) { saved(book)?.let {
                offset(it) == moved && it.writerSessionEpoch == restoredBook.activeSessionEpoch
            } == true }
            assertEquals(otherProgress, saved(other))
            assertEquals(hashes, originals.map(::digest))
            report.put("new_offset", moved).put("new_epoch", requireNotNull(saved(book)).writerSessionEpoch)
                .put("save_after_retry", true)
            passed = true
        } finally {
            try {
                if (faultInstalled) {
                    if (nativeQueryFailure) execute("ALTER TABLE reading_progress RENAME COLUMN fixture_held_book_id TO bookId")
                    else locatorJson(originalJson)
                }
                // Real DAO query must work after cleanup, including a failed assertion path.
                runBlocking { assertNotNull(observerDatabase.progressDao().findProgress(book.id, requireNotNull(book.currentRevision))) }
                report.put("fixture_restored", true)
            } finally {
                report.put("status", if (passed && report.optBoolean("fixture_restored")) "PASS" else "FAIL")
                reportFile.writeText(report.toString(2))
            }
        }
    }

    @Test fun alternatingBooksWaitForPendingNativeWritesAndKeepBackwardProgress() {
        val reportFile = File(target.filesDir, "reader-switch-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        val fixtures = listOf("reader-switch-a", "reader-switch-b")
        val selected = fixtures.map { fixture ->
            sendFixture(fixture)
            waitFor("reader_ready")
            val book = reader().book
            repeat(4) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
            compose.readerControl("reader_back").performClick()
            waitFor("library_content")
            book
        }
        val files = selected.map { File(File(target.filesDir, "managed"), it.managedRelativePath) }
        val hashes = files.map(::digest)
        val expected = selected.associate { it.id to requireNotNull(saved(it)) }.toMutableMap()
        val events = JSONArray()
        var passed = false
        try {
            repeat(6) { iteration ->
                val book = selected[iteration % 2]
                val other = selected[(iteration + 1) % 2]
                val previous = requireNotNull(expected[book.id])
                val otherBook = books().single { it.id == other.id }
                compose.onNodeWithTag("read_${book.id}").performClick()
                waitFor("reader_ready")
                val active = reader()
                assertEquals(book.id, active.book.id)
                assertEquals(offset(previous), measuredVisibleLine(active))
                assertEquals(previous, saved(book))
                val epoch = books().single { it.id == book.id }.activeSessionEpoch
                val backward = iteration >= 2 && iteration < 4
                var pending = -1L
                withHeldNativeWriter { release ->
                    compose.onNodeWithTag("reader_text").performTouchInput {
                        if (backward) swipeDown() else swipeUp()
                    }
                    compose.waitForIdle()
                    pending = measuredVisibleLine(active)
                    assertEquals(pending, active.state.value.stableOffset)
                    if (backward) assertTrue(pending < offset(previous))
                    else assertTrue(pending > offset(previous))
                    // Real 250ms settled save reaches the occupied production Room writer.
                    SystemClock.sleep(350)
                    compose.readerControl("reader_back").performClick()
                    compose.waitForIdle()
                    // The library cannot expose another book before close's flush finishes.
                    compose.onNodeWithTag("library_content").assertDoesNotExist()
                    assertSame(active, reader())
                    assertEquals(previous, saved(book))
                    assertEquals(expected[other.id], saved(other))
                    assertEquals(otherBook, books().single { it.id == other.id })
                    release()
                    waitFor("library_content")
                }
                val committed = requireNotNull(saved(book))
                assertEquals(pending, offset(committed))
                assertEquals(epoch, committed.writerSessionEpoch)
                assertTrue(committed.writerSequence > 0)
                assertEquals(expected[other.id], saved(other))
                assertEquals(otherBook, books().single { it.id == other.id })
                assertEquals(hashes, files.map(::digest))
                expected[book.id] = committed
                events.put(JSONObject().put("iteration", iteration).put("fixture_id", fixtures[iteration % 2])
                    .put("previous_offset", offset(previous)).put("committed_offset", pending)
                    .put("epoch", epoch).put("sequence", committed.writerSequence)
                    .put("backward", backward).put("close_waited_for_writer", true))
            }
            // Reopen both actual screens after all delayed writes and compare measured positions.
            for (book in selected) {
                compose.onNodeWithTag("read_${book.id}").performClick()
                waitFor("reader_ready")
                assertEquals(offset(requireNotNull(expected[book.id])), measuredVisibleLine(reader()))
                assertEquals(expected[book.id], saved(book))
                compose.readerControl("reader_back").performClick()
                waitFor("library_content")
            }
            passed = true
        } finally {
            reportFile.writeText(JSONObject().put("status", if (passed) "PASS" else "FAIL")
                .put("build_fingerprint", Build.FINGERPRINT).put("api", Build.VERSION.SDK_INT)
                .put("fixture_sha256", JSONArray(hashes)).put("events", events)
                .put("mechanism", "Held production Room writer connection; actual UI scroll/back/open; no fake save result")
                .toString(2))
        }
    }

    @Test fun repeatedCloseDuringRestorationCancelsBeforeSameBookCanActivate() {
        val reportFile = File(target.filesDir, "reader-reopen-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        sendFixture("reader-reopen")
        waitFor("reader_ready")
        val book = reader().book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        val committed = requireNotNull(saved(book))
        val beforeBooks = books()
        val beforeEpoch = beforeBooks.single { it.id == book.id }.activeSessionEpoch
        val original = File(File(target.filesDir, "managed"), book.managedRelativePath)
        val hash = digest(original)
        val closed = mutableListOf<TxtReaderController>()
        var passed = false
        try {
            withHeldNativeWriter {
                repeat(5) {
                    compose.onNodeWithTag("read_${book.id}").performClick()
                    waitFor("txt_reader")
                    val opening = reader()
                    assertTrue(closed.none { it === opening })
                    compose.waitUntil(10_000) {
                        opening.state.value.let { it.phase == TxtReaderPhase.RESTORING && it.cache.complete }
                    }
                    compose.waitForIdle()
                    compose.onNodeWithTag("reader_ready").assertDoesNotExist()
                    compose.readerControl("reader_back").performClick()
                    waitFor("library_content")
                    closed += opening
                    assertEquals(committed, saved(book))
                    assertTrue("Cancelled openings must not change book metadata", beforeBooks == books())
                    assertEquals(hash, digest(original))
                }
            }
            // After releasing the real writer, cancelled activations must stay cancelled.
            SystemClock.sleep(1_250)
            assertEquals(committed, saved(book))
            assertTrue("No delayed activation after release", beforeBooks == books())
            assertTrue(closed.all { it.state.value.phase != TxtReaderPhase.READY })
            compose.onNodeWithTag("read_${book.id}").performClick()
            waitFor("reader_ready")
            assertEquals(offset(committed), measuredVisibleLine(reader()))
            assertEquals(committed, saved(book))
            assertEquals(beforeEpoch + 1, books().single { it.id == book.id }.activeSessionEpoch)
            compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
            compose.waitForIdle()
            val moved = measuredVisibleLine(reader())
            compose.waitUntil(10_000) { saved(book)?.let {
                offset(it) == moved && it.writerSessionEpoch == beforeEpoch + 1
            } == true }
            assertTrue(moved > offset(committed))
            assertEquals(hash, digest(original))
            passed = true
        } finally {
            reportFile.writeText(JSONObject().put("status", if (passed) "PASS" else "FAIL")
                .put("build_fingerprint", Build.FINGERPRINT).put("api", Build.VERSION.SDK_INT)
                .put("fixture_sha256", hash).put("cancelled_openings", closed.size)
                .put("original_offset", offset(committed)).put("original_epoch", beforeEpoch)
                .put("mechanism", "Native writer held through five actual UI open/restore/close cycles; release then reopen and save")
                .toString(2))
        }
    }

    @Test fun actualRotationAndStopPreservePendingAnchorWithoutAnotherActivation() {
        val reportFile = File(target.filesDir, "reader-reflow-stop-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        sendFixture("reader-reflow-stop")
        waitFor("reader_ready")
        val active = reader()
        val book = active.book
        repeat(4) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val originalOffset = measuredVisibleLine(active)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == originalOffset } == true }
        val committed = requireNotNull(saved(book))
        val beforeBook = books().single { it.id == book.id }
        val original = File(File(target.filesDir, "managed"), book.managedRelativePath)
        val hash = digest(original)
        var priorOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var initialConfiguration = Configuration.ORIENTATION_UNDEFINED
        compose.activityRule.scenario.onActivity {
            priorOrientation = it.requestedOrientation
            initialConfiguration = it.resources.configuration.orientation
        }
        val destination = if (initialConfiguration == Configuration.ORIENTATION_PORTRAIT)
            Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        val request = if (destination == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val generation = active.state.value.restoreGeneration
        var pending = -1L
        var passed = false
        try {
            withHeldNativeWriter { release ->
                compose.onNodeWithTag("reader_text").performTouchInput { swipeDown() }
                compose.waitForIdle()
                pending = measuredVisibleLine(active)
                assertTrue(pending < originalOffset)
                assertEquals(pending, active.state.value.stableOffset)
                compose.activityRule.scenario.onActivity { it.requestedOrientation = request }
                compose.waitUntil(30_000) {
                    var configuration = Configuration.ORIENTATION_UNDEFINED
                    compose.activityRule.scenario.onActivity { configuration = it.resources.configuration.orientation }
                    configuration == destination && active.state.value.restoreGeneration > generation &&
                        active.state.value.phase == TxtReaderPhase.READY
                }
                assertSame(active, reader())
                assertEquals(pending, active.state.value.targetOffset)
                assertAnchorInFirstMeasuredLine(active, pending)
                assertEquals(committed, saved(book))
                // Actual Activity ON_STOP, distinct from calling controller.flush() directly.
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(Lifecycle.State.CREATED, compose.activityRule.scenario.state)
                assertEquals(committed, saved(book))
                release()
                runBlocking { withTimeout(10_000) {
                    while (saved(book)?.let { offset(it) == pending } != true) delay(25)
                } }
                val stopped = requireNotNull(saved(book))
                assertEquals(committed.writerSessionEpoch, stopped.writerSessionEpoch)
                assertTrue(stopped.writerSequence > committed.writerSequence)
                assertEquals(beforeBook, books().single { it.id == book.id })
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                waitFor("reader_ready")
                assertSame(active, reader())
                assertAnchorInFirstMeasuredLine(active, pending)
                assertEquals(stopped, saved(book))
                assertEquals(hash, digest(original))
            }
            passed = true
        } finally {
            try {
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                compose.activityRule.scenario.onActivity { it.requestedOrientation = priorOrientation }
            } finally {
                reportFile.writeText(JSONObject().put("status", if (passed) "PASS" else "FAIL")
                    .put("build_fingerprint", Build.FINGERPRINT).put("api", Build.VERSION.SDK_INT)
                    .put("fixture_sha256", hash).put("original_offset", originalOffset).put("pending_offset", pending)
                    .put("initial_orientation", initialConfiguration).put("changed_orientation", destination)
                    .put("initial_generation", generation).put("final_generation", active.state.value.restoreGeneration)
                    .put("writer_epoch", beforeBook.activeSessionEpoch)
                    .put("mechanism", "Actual Activity orientation change and ON_STOP with pending native writer; not process death or an isolated ON_STOP-only trigger")
                    .toString(2))
            }
        }
    }

    private fun assertAnchorInFirstMeasuredLine(controller: TxtReaderController, anchor: Long) {
        val measured = measuredVisibleLine(controller)
        val index = controller.cache.fragmentFor(measured)
        assertEquals(index, controller.cache.fragmentFor(anchor))
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("reader_fragment_$index", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        val layout = layouts.single()
        val start = controller.state.value.cache.fragments[index].start
        assertEquals(layout.getLineForOffset((measured - start).toInt()), layout.getLineForOffset((anchor - start).toInt()))
    }

    @Test fun deletingDuringRealDecodeCancelsPartialIndexAndQueuedReopen() {
        val reportFile = File(target.filesDir, "reader-delete-decoding-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        sendFixture("reader-switch-a")
        waitFor("reader_ready")
        val other = reader().book
        compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        val otherProgress = requireNotNull(saved(other))
        val otherRecord = books().single { it.id == other.id }
        val otherFile = File(File(target.filesDir, "managed"), other.managedRelativePath)
        val otherHash = digest(otherFile)
        lateinit var model: AppViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val report = JSONObject().put("build_fingerprint", Build.FINGERPRINT).put("api", Build.VERSION.SDK_INT)
            .put("mechanism", "Real 32MiB managed TXT decode; AppViewModel deletion request during partial cache; not a visible Reader delete-button flow")
        var passed = false
        val deletion = owner.async {
            val active = withTimeout(60_000) { model.state.map { it.reader }.filterNotNull().first {
                it.book.originalDisplayName == "../../reader-delete-decoding.txt"
            } }
            val partial = withTimeout(10_000) { active.state.first {
                it.cache.committedLength > 0 && !it.cache.complete
            } }
            assertFalse(active.state.value.cache.complete)
            assertNotEquals(TxtReaderPhase.READY, active.state.value.phase)
            report.put("fixture_sha256", active.book.sourceSha256).put("fixture_bytes", active.book.sourceByteSize)
                .put("partial_utf16_length", partial.cache.committedLength)
            // The UI model uses the real coordinator: durable DELETING -> close -> file/row removal.
            model.requestDelete(active.book)
            model.confirmDelete()
            model.openBook(active.book) // A reopen requested after deletion begins must be rejected.
            withTimeout(30_000) {
                while (withContext(Dispatchers.IO) { observerDatabase.bookDao().findBook(active.book.id) } != null) delay(25)
                model.state.first { it.reader == null }
            }
            active
        }
        try {
            sendFixture("reader-delete-decoding")
            val deleted = runBlocking { withTimeout(90_000) { deletion.await() } }
            waitFor("library_content")
            val book = deleted.book
            assertEquals(32L * 1024 * 1024, book.sourceByteSize)
            assertFalse(deleted.state.value.cache.complete)
            assertFalse(File(File(target.filesDir, "managed"), book.managedRelativePath).exists())
            val key = MessageDigest.getInstance("SHA-256").digest(requireNotNull(book.currentRevision).toByteArray())
                .joinToString("") { "%02x".format(it) }
            assertTrue(File(target.cacheDir, "txt").listFiles().orEmpty().none { it.name.startsWith(key) })
            assertEquals(ProgressReadResult.Missing, runBlocking {
                RoomProgressRepository(observerDatabase).loadProgress(book.id, requireNotNull(book.currentRevision))
            })
            // Resume queued dispatcher work and sample beyond the ordinary save interval.
            SystemClock.sleep(1_250)
            assertFalse(books().any { it.id == book.id })
            assertEquals(otherProgress, saved(other))
            assertEquals(otherRecord, books().single { it.id == other.id })
            assertEquals(otherHash, digest(otherFile))
            report.put("book_and_progress_absent", true).put("managed_copy_absent", true)
                .put("partial_cache_removed", true).put("other_book_preserved", true)
            passed = true
        } finally {
            owner.cancel()
            runBlocking { withTimeout(10_000) { deletion.join() } }
            report.put("status", if (passed) "PASS" else "FAIL")
            reportFile.writeText(report.toString(2))
        }
    }

    @Test fun pagesNavigateAndTypographyAndModeChangesPreserveCommittedAnchor() {
        val originalSettings = runBlocking { observerDatabase.readerSettingsDao().global() }
        val originalScale = uiShell("settings get system font_scale").trim().toFloatOrNull()
        val initialScale = compose.activity.resources.configuration.fontScale
        val densityOutput = uiShell("wm density")
        val originalDensityOverride = Regex("Override density: (\\d+)").find(densityOutput)?.groupValues?.get(1)?.toInt()
        val initialDensity = compose.activity.resources.configuration.densityDpi
        var changedScale = false
        var changedDensity = false
        try {
        sendFixture("reader-pages")
        waitFor("reader_ready")
        val active = reader()
        val book = active.book
        val initialFontSize = active.state.value.settings.fontSizeSp
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val anchor = measuredVisibleLine(active)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == anchor } == true }
        val before = requireNotNull(saved(book))
        fun settings(action: () -> Unit) {
            val generation = active.state.value.restoreGeneration
            val loaded = active.state.value.settingsLoaded
            compose.readerControl("reader_settings").performClick()
            action()
            compose.onNodeWithTag("reader_settings_apply").performClick()
            compose.waitUntil(15_000) {
                active.state.value.let { it.restoreGeneration > generation && it.phase == TxtReaderPhase.READY &&
                    !it.settingsSaving && it.settingsLoaded !== loaded }
            }
            compose.waitForIdle()
        }
        settings {
            compose.onNodeWithTag("reader_mode_PAGES").performClick()
            compose.onNodeWithTag("reader_transition_SLIDE").performScrollTo().performClick()
        }
        assertEquals(org.bookreader.mobile.settings.PageTransition.SLIDE, active.state.value.settings.pageTransition)
        compose.onNodeWithTag("reader_page").assertIsDisplayed()
        compose.onNodeWithTag("reader_ready").assertIsDisplayed()
        compose.readerControl("reader_next").assertIsEnabled()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250) // Allow the ready frame to reach the screenshot surface.
        ReaderFixtureScreenshot.capture("txt-reader-generated-pages.png")
        assertEquals(anchor, active.state.value.targetOffset)
        assertEquals(before, saved(book))
        compose.waitUntil(30_000) {
            compose.onNodeWithTag("reader_bottom_pages").fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.Text].single().text.contains(" / ")
        }
        val footer = compose.onNodeWithTag("reader_bottom_pages").fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        val toolbar = compose.onNodeWithTag("reader_progress", useUnmergedTree = true).fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        assertEquals(footer, toolbar)
        ReaderFixtureScreenshot.capture("txt-page-counts.png")
        val pageStarts = mutableListOf(anchor)
        repeat(12) {
            val generation = active.state.value.restoreGeneration
            compose.readerControl("reader_next").performClick()
            compose.waitUntil(10_000) {
                active.state.value.let { it.restoreGeneration > generation && it.phase == TxtReaderPhase.READY }
            }
            assertTrue(active.state.value.targetOffset > pageStarts.last())
            pageStarts += active.state.value.targetOffset
        }
        repeat(12) { index ->
            val generation = active.state.value.restoreGeneration
            compose.readerControl("reader_previous").performClick()
            compose.waitUntil(10_000) {
                active.state.value.let { it.restoreGeneration > generation && it.phase == TxtReaderPhase.READY }
            }
            assertEquals(pageStarts[11 - index], active.state.value.targetOffset)
        }
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == anchor } == true }
        val returned = requireNotNull(saved(book))
        settings { compose.onNodeWithTag("font_size_increase").performScrollTo().performClick() }
        for (tag in listOf("reader_font_SERIF", "reader_font_NANUM_GOTHIC", "reader_font_NANUM_MYEONGJO", "line_height_increase", "letter_spacing_increase", "margin_increase")) {
            settings {
                if (tag.startsWith("letter_spacing")) compose.advancedReaderSetting(tag).performClick()
                else compose.onNodeWithTag(tag).performScrollTo().performClick()
            }
            assertEquals(anchor, active.state.value.targetOffset)
            assertEquals(returned, saved(book))
        }
        compose.waitUntil(30_000) {
            compose.onNodeWithTag("reader_bottom_pages").fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.Text].single().text.contains(" / ")
        }
        ReaderFixtureScreenshot.capture("txt-nanum-page-counts.png")
        val scale = if (kotlin.math.abs(initialScale - 1.3f) > .01f) 1.3f else 1.0f
        var generation = active.state.value.restoreGeneration
        changedScale = true
        uiShell("settings put system font_scale $scale")
        compose.waitUntil(30_000) {
            kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < .01f &&
                active.state.value.let { it.restoreGeneration > generation && it.phase == TxtReaderPhase.READY }
        }
        assertSame(active, reader())
        assertEquals(anchor, active.state.value.targetOffset)
        assertEquals(returned, saved(book))
        val density = if (initialDensity == 360) 420 else 360
        generation = active.state.value.restoreGeneration
        changedDensity = true
        uiShell("wm density $density")
        compose.waitUntil(30_000) {
            compose.activity.resources.configuration.densityDpi == density &&
                active.state.value.let { it.restoreGeneration > generation && it.phase == TxtReaderPhase.READY }
        }
        assertSame(active, reader())
        assertEquals(anchor, active.state.value.targetOffset)
        assertEquals(returned, saved(book))
        compose.onNodeWithTag("reader_ready").assertIsDisplayed()
        compose.readerControl("reader_next").assertIsEnabled()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        ReaderFixtureScreenshot.capture("txt-reader-generated-page-reflow.png")
        assertEquals(anchor, active.state.value.targetOffset)
        assertEquals(returned, saved(book))
        settings { compose.onNodeWithTag("reader_mode_SCROLL").performClick() }
        assertAnchorInFirstMeasuredLine(active, anchor)
        assertEquals(returned, saved(book))
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        compose.onNodeWithTag("read_${book.id}").performClick()
        waitFor("reader_ready")
        assertEquals(org.bookreader.mobile.settings.ReaderFont.NANUM_MYEONGJO, reader().state.value.settings.font)
        assertEquals(org.bookreader.mobile.settings.PageTransition.SLIDE, reader().state.value.settings.pageTransition)
        assertEquals((initialFontSize + 1).coerceAtMost(36f), reader().state.value.settings.fontSizeSp)
        assertEquals(org.bookreader.mobile.settings.TxtReadingMode.SCROLL, reader().state.value.settings.readingMode)
        assertAnchorInFirstMeasuredLine(reader(), anchor)
        assertEquals(returned, saved(book))
        } finally {
            try {
            if (changedDensity) uiShell("wm density ${originalDensityOverride ?: "reset"}")
            if (changedScale) uiShell(if (originalScale == null) "settings delete system font_scale"
                else "settings put system font_scale $originalScale")
            if (changedScale || changedDensity) compose.waitUntil(30_000) {
                compose.activity.resources.configuration.let {
                    it.densityDpi == initialDensity && kotlin.math.abs(it.fontScale - initialScale) < .01f
                }
            }
            } finally {
            var current: TxtReaderController? = null
            compose.activityRule.scenario.onActivity { current = ViewModelProvider(it)[AppViewModel::class.java].state.value.reader }
            runBlocking {
                try { current?.close() } finally {
                if (originalSettings == null) observerDatabase.useWriterConnection {
                    it.execSQL("DELETE FROM reader_global_settings WHERE id = 'global'")
                } else observerDatabase.readerSettingsDao().putGlobal(originalSettings)
                }
            }
            }
        }
    }

    @Test fun menuSeekCancellationGesturesAndWindowExitPreserveCanonicalProgress() {
        val originalSettings = runBlocking { observerDatabase.readerSettingsDao().global() }
        val initialBrightness = compose.activity.window.attributes.screenBrightness
        val initialFlags = compose.activity.window.attributes.flags
        val initialOrientation = compose.activity.requestedOrientation
        try {
            sendFixture("reader-pages")
            waitFor("reader_ready")
            val active = reader()
            val book = active.book
            repeat(2) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
            // A periodic save can match the current anchor while the preceding fling is still
            // moving. Drain Compose's real scroll animation before taking the immutable baseline.
            compose.waitForIdle()
            val settledAnchor = measuredVisibleLine(active)
            compose.waitUntil(10_000) { saved(book)?.let { offset(it) == settledAnchor && offset(it) > 0 } == true }
            assertEquals(settledAnchor, active.state.value.stableOffset)
            val before = requireNotNull(saved(book))
            compose.readerControl("reader_menu").performClick()
            compose.onNodeWithTag("reader_seek").performTouchInput {
                down(center)
                moveTo(androidx.compose.ui.geometry.Offset(width * .8f, centerY), 100)
            }
            compose.waitForIdle()
            assertEquals(before, saved(book))
            compose.onNodeWithTag("reader_seek").performTouchInput { cancel() }
            compose.waitForIdle()
            SystemClock.sleep(400)
            assertEquals(before, saved(book))
            assertEquals(offset(before), active.state.value.stableOffset)
            compose.onNodeWithTag("reader_seek").performSemanticsAction(SemanticsActions.SetProgress) { it(.7f) }
            try {
                compose.waitUntil(15_000) { active.state.value.let {
                    it.phase == TxtReaderPhase.READY && (it.stableOffset ?: 0) in
                        (it.cache.committedLength * .69).toLong()..(it.cache.committedLength * .71).toLong()
                } }
            } catch (failure: Throwable) {
                val observed = active.state.value
                val preview = compose.onNodeWithTag("reader_seek_preview").fetchSemanticsNode().config
                throw AssertionError("Seek state phase=${observed.phase} target=${observed.targetOffset} " +
                    "stable=${observed.stableOffset} generation=${observed.restoreGeneration} " +
                    "complete=${observed.cache.complete} length=${observed.cache.committedLength} preview=$preview", failure)
            }
            val target = requireNotNull(active.state.value.stableOffset)
            compose.waitUntil(10_000) { saved(book)?.let { offset(it) == target } == true }
            compose.onNodeWithTag("reader_menu_close").performClick()
            assertAnchorInFirstMeasuredLine(active, target)
            compose.readerControl("reader_settings").performClick()
            compose.onNodeWithTag("reader_mode_PAGES").performClick()
            compose.onNodeWithTag("reader_settings_apply").performClick()
            compose.waitUntil(15_000) { active.state.value.settings.readingMode == org.bookreader.mobile.settings.TxtReadingMode.PAGES &&
                active.state.value.phase == TxtReaderPhase.READY }
            compose.readerControl("reader_next").assertIsEnabled()
            val pageAnchor = active.state.value.stableOffset
            val bounds = compose.onNodeWithTag("reader_ready").fetchSemanticsNode().boundsInRoot
            val generationBeforeOverlay = active.state.value.restoreGeneration
            compose.readerControl("reader_hide").performClick()
            compose.onNodeWithTag("reader_chrome").assertDoesNotExist()
            ReaderFixtureScreenshot.capture("txt-immersive.png")
            compose.tapReader("reader", .5f); waitFor("reader_chrome")
            assertEquals(bounds, compose.onNodeWithTag("reader_ready").fetchSemanticsNode().boundsInRoot)
            assertEquals(generationBeforeOverlay, active.state.value.restoreGeneration)
            assertEquals(pageAnchor, active.state.value.stableOffset)
            ReaderFixtureScreenshot.capture("txt-controls.png")
            compose.tapReader("reader", .5f)
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("reader_chrome").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("reader_ready").performTouchInput {
                doubleClick(androidx.compose.ui.geometry.Offset(width * .85f, height * .4f))
            }
            waitFor("reader_seek")
            assertEquals(pageAnchor, active.state.value.stableOffset)
            compose.onNodeWithTag("reader_menu_close").performClick()
            compose.onNodeWithTag("reader_ready").performTouchInput {
                click(androidx.compose.ui.geometry.Offset(width * .85f, height * .4f))
            }
            compose.waitUntil(10_000) { active.state.value.phase == TxtReaderPhase.READY && active.state.value.stableOffset != pageAnchor }
            val moved = active.state.value.stableOffset
            compose.readerControl("reader_settings").performClick()
            compose.waitForIdle()
            ReaderFixtureScreenshot.capture("txt-settings.png")
            compose.onNodeWithTag("reader_settings_advanced").performScrollTo().performClick()
            for (tag in listOf("reader_fullscreen_option", "reader_keep_screen_on_option", "reader_rotation_lock_option", "window_brightness_increase"))
                compose.onNodeWithTag(tag).performScrollTo().performClick()
            compose.onNodeWithTag("reader_settings_apply").performClick()
            compose.waitUntil(15_000) { active.state.value.settings.window.fullscreen && active.state.value.phase == TxtReaderPhase.READY }
            assertEquals(moved, active.state.value.stableOffset)
            compose.runOnIdle {
                assertEquals(.6f, compose.activity.window.attributes.screenBrightness, .001f)
                assertTrue(compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
                assertEquals(ActivityInfo.SCREEN_ORIENTATION_LOCKED, compose.activity.requestedOrientation)
            }
            compose.activityRule.scenario.recreate()
            waitFor("reader_ready")
            assertSame(active, reader())
            assertEquals(moved, active.state.value.stableOffset)
            compose.readerControl("reader_menu").performClick()
            compose.onNodeWithTag("reader_menu_exit").performScrollTo().performClick()
            waitFor("library_content")
            compose.runOnIdle {
                assertEquals(initialBrightness, compose.activity.window.attributes.screenBrightness, .001f)
                val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                assertEquals(initialFlags and flag, compose.activity.window.attributes.flags and flag)
                assertEquals(initialOrientation, compose.activity.requestedOrientation)
            }
        } finally { restoreReaderGlobalSettings(originalSettings) }
    }

    @Test fun sliderSeeksRestoreUncomposedDistantScrollFragmentsInBothDirections() {
        sendFixture("reader-pages")
        waitFor("reader_ready")
        val active = reader()
        compose.readerControl("reader_menu").performClick()
        for (fraction in listOf(.7f, .1f, .9f, .3f, .8f, .2f)) {
            compose.onNodeWithTag("reader_seek").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(fraction)) }
            val expected = (active.state.value.cache.committedLength * fraction.toDouble()).toLong()
            compose.waitUntil(15_000) { active.state.value.let {
                it.phase == TxtReaderPhase.READY && kotlin.math.abs((it.stableOffset ?: -1000) - expected) < 10
            } }
            val accepted = requireNotNull(active.state.value.stableOffset)
            compose.waitUntil(10_000) { saved(active.book)?.let { offset(it) == accepted } == true }
            assertAnchorInFirstMeasuredLine(active, accepted)
        }
        compose.onNodeWithTag("reader_menu_close").performClick()
    }

    @Test fun readerControlsRemainReachableAtSystemFontScalesWithoutMovingAnchor() {
        val originalSettings = runBlocking { observerDatabase.readerSettingsDao().global() }
        val originalScale = uiShell("settings get system font_scale").trim().toFloatOrNull()
        val initialScale = compose.activity.resources.configuration.fontScale
        try {
            sendFixture("reader-pages")
            waitFor("reader_ready")
            val active = reader()
            repeat(2) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
            val anchor = measuredVisibleLine(active)
            compose.waitUntil(10_000) { saved(active.book)?.let { offset(it) == anchor } == true }
            val before = requireNotNull(saved(active.book))
            for (scale in listOf(1f, 1.3f, 2f)) {
                uiShell("settings put system font_scale $scale")
                compose.waitUntil(30_000) { kotlin.math.abs(compose.activity.resources.configuration.fontScale - scale) < .01f &&
                    active.state.value.phase == TxtReaderPhase.READY }
                compose.readerControl("reader_menu").assertIsDisplayed().performClick()
                compose.onNodeWithTag("reader_seek").assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithTag("reader_menu_close").performClick()
                compose.readerControl("reader_settings").assertIsDisplayed().performClick()
                compose.advancedReaderSetting("reader_rotation_lock_option").assertIsDisplayed()
                compose.onNodeWithTag("reader_settings_apply").assertIsDisplayed().performClick()
                waitFor("reader_ready")
                assertEquals(anchor, active.state.value.stableOffset)
                assertEquals(before, saved(active.book))
                assertAnchorInFirstMeasuredLine(active, anchor)
            }
        } finally {
            try {
                uiShell(if (originalScale == null) "settings delete system font_scale" else "settings put system font_scale $originalScale")
                compose.waitUntil(30_000) { kotlin.math.abs(compose.activity.resources.configuration.fontScale - initialScale) < .01f }
            } finally { restoreReaderGlobalSettings(originalSettings) }
        }
    }

    private fun restoreReaderGlobalSettings(original: org.bookreader.mobile.database.ReaderGlobalSettingsEntity?) {
        var current: TxtReaderController? = null
        compose.activityRule.scenario.onActivity { current = ViewModelProvider(it)[AppViewModel::class.java].state.value.reader }
        runBlocking {
            try { current?.close() } finally {
                if (original == null) observerDatabase.useWriterConnection { it.execSQL("DELETE FROM reader_global_settings WHERE id = 'global'") }
                else observerDatabase.readerSettingsDao().putGlobal(original)
            }
        }
    }

    private fun uiShell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Test fun literalSearchAcrossCacheBoundaryReturnsOnceAndBookmarkSurvivesReopen() {
        sendFixture("reader-tools")
        waitFor("reader_ready")
        val active = reader()
        val book = active.book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val origin = measuredVisibleLine(active)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == origin } == true }
        compose.readerControl("reader_bookmarks").performClick()
        compose.waitUntil(10_000) { active.tools.state.value.bookmarksReadReady }
        compose.onNodeWithTag("reader_bookmark_label").performTextInput("돌아갈 곳")
        compose.onNodeWithTag("reader_bookmark_add").performClick()
        compose.waitUntil(10_000) { active.tools.state.value.bookmarks.size == 1 && !active.tools.state.value.bookmarkWriting }
        val bookmark = active.tools.state.value.bookmarks.single()
        assertEquals(origin, (bookmark.locator as TxtLocator).payload.utf16Offset)
        compose.onNodeWithTag("reader_bookmark_close").performClick()
        compose.readerControl("reader_search").performClick()
        compose.onNodeWithTag("reader_search_query").performClick()
        compose.onNodeWithTag("reader_search_query").performSemanticsAction(SemanticsActions.SetText) { setText ->
            active.beginReflow()
            assertEquals(TxtReaderPhase.REFLOWING, active.state.value.phase)
            assertTrue(setText(androidx.compose.ui.text.AnnotatedString("경계검색😀")))
        }
        compose.waitUntil(15_000) { active.tools.state.value.searchComplete }
        assertEquals(listOf(16382L), active.tools.state.value.hits.map { it.offset })
        compose.onNodeWithTag("reader_search_hit_16382").performClick()
        compose.waitUntil(10_000) { active.state.value.let { it.phase == TxtReaderPhase.READY && it.targetOffset == 16382L } }
        assertAnchorInFirstMeasuredLine(active, 16382)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == 16382L } == true }
        compose.readerControl("reader_search_return").performClick()
        compose.waitUntil(10_000) { active.state.value.let { it.phase == TxtReaderPhase.READY && it.targetOffset == origin } }
        compose.onNodeWithTag("reader_search_return").assertDoesNotExist()
        assertAnchorInFirstMeasuredLine(active, origin)
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        compose.onNodeWithTag("read_${book.id}").performClick()
        waitFor("reader_ready")
        compose.readerControl("reader_bookmarks").performClick()
        compose.waitUntil(10_000) { reader().tools.state.value.bookmarks.size == 1 }
        assertEquals(bookmark, reader().tools.state.value.bookmarks.single())
        compose.onNodeWithTag("reader_bookmark_${bookmark.id}").performClick()
        compose.waitUntil(10_000) { reader().state.value.phase == TxtReaderPhase.READY }
        assertAnchorInFirstMeasuredLine(reader(), origin)
        assertEquals(origin, offset(requireNotNull(saved(book))))
    }

    @Test fun manualEncodingRoundTripRestoresEachInterpretationAndItsBookmark() {
        sendFixture("reader-encoding-revision")
        waitFor("reader_ready")
        val originalBook = reader().book
        val file = File(File(target.filesDir, "managed"), originalBook.managedRelativePath)
        val hash = digest(file)
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val originalOffset = measuredVisibleLine(reader())
        compose.waitUntil(10_000) { saved(originalBook)?.let { offset(it) == originalOffset } == true }
        val originalProgress = requireNotNull(saved(originalBook))
        compose.readerControl("reader_bookmarks").performClick()
        compose.waitUntil(10_000) { reader().tools.state.value.bookmarksReadReady }
        compose.onNodeWithTag("reader_bookmark_add").performClick()
        compose.waitUntil(10_000) { reader().tools.state.value.bookmarks.size == 1 }
        val originalBookmark = reader().tools.state.value.bookmarks.single()
        compose.onNodeWithTag("reader_bookmark_close").performClick()
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        changeFixtureEncoding(originalBook.id, "UTF16LE", "utf-16le")
        // The encoding notice and larger cover can put the card actions below the viewport.
        // Reach the actual button by scrolling, then inject the same real touch as before.
        compose.onNodeWithTag("read_${originalBook.id}").performScrollTo().assertIsDisplayed().performClick()
        waitFor("reader_ready")
        val changed = reader().book
        assertNotEquals(originalBook.currentRevision, changed.currentRevision)
        assertEquals(0L, reader().state.value.targetOffset)
        assertEquals(32_768L, reader().state.value.cache.committedLength)
        assertEquals(originalProgress, saved(originalBook))
        repeat(2) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val changedOffset = measuredVisibleLine(reader())
        compose.waitUntil(10_000) { saved(changed)?.let { offset(it) == changedOffset } == true }
        val changedProgress = requireNotNull(saved(changed))
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        changeFixtureEncoding(originalBook.id, "UTF8", "utf-8")
        compose.onNodeWithTag("read_${originalBook.id}").performScrollTo().assertIsDisplayed().performClick()
        waitFor("reader_ready")
        assertEquals(originalBook.currentRevision, reader().book.currentRevision)
        assertAnchorInFirstMeasuredLine(reader(), originalOffset)
        assertEquals(originalProgress, saved(originalBook))
        assertEquals(changedProgress, saved(changed))
        compose.readerControl("reader_bookmarks").performClick()
        compose.waitUntil(10_000) { reader().tools.state.value.bookmarks.size == 1 }
        assertEquals(originalBookmark, reader().tools.state.value.bookmarks.single())
        assertEquals(hash, digest(file))
    }

    @Test fun invalidManualEncodingKeepsOriginalBookProgressAndManagedBytes() {
        sendFixture("reader-encoding-invalid")
        waitFor("reader_ready")
        val book = reader().book
        val file = File(File(target.filesDir, "managed"), book.managedRelativePath)
        val hash = digest(file)
        compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
        compose.readerControl("reader_back").performClick()
        waitFor("library_content")
        val progress = saved(book)
        val beforeBooks = books()
        compose.onNodeWithTag("info_${book.id}").performClick()
        compose.onNodeWithTag("book_change_encoding").performClick()
        lateinit var model: AppViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
        compose.waitUntil(10_000) { model.state.value.encodingChange?.loading == false }
        compose.waitForIdle()
        compose.onNodeWithTag("book_encoding_UTF16LE").performClick()
        waitFor("book_encoding_error")
        assertEquals(beforeBooks, books())
        assertEquals(progress, saved(book))
        assertEquals(hash, digest(file))
        compose.onNodeWithTag("book_encoding_cancel").performClick()
    }

    private fun changeFixtureEncoding(bookId: String, option: String, expectedId: String) {
        compose.onNodeWithTag("info_$bookId").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("book_change_encoding").performClick()
        lateinit var model: AppViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
        compose.waitUntil(10_000) { model.state.value.encodingChange?.loading == false }
        compose.onNodeWithTag("book_encoding_$option").performClick()
        compose.waitUntil(30_000) { model.state.value.encodingChange == null && books().single { it.id == bookId }.encodingId == expectedId }
        waitFor("library_content")
    }

    /** A test-owned coroutine borrows the production writer; it never closes the application DB. */
    private fun withHeldNativeWriter(action: (release: () -> Unit) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val entered = CompletableDeferred<Unit>()
        val released = CompletableDeferred<Unit>()
        val held = scope.async {
            AndroidDatabaseOwner.borrow(target).useWriterConnection {
                entered.complete(Unit)
                released.await()
            }
        }
        fun release() {
            released.complete(Unit)
            runBlocking { withTimeout(10_000) { held.await() } }
        }
        try {
            runBlocking { withTimeout(10_000) { entered.await() } }
            action(::release)
        } finally {
            try { release() } finally { scope.cancel() }
        }
    }

    private fun measuredVisibleLine(controller: TxtReaderController): Long {
        // A larger viewport also exposes prefetched semantics nodes with no measured text yet.
        // Read bounds and layout actions together on Main; only actual visible layout is evidence.
        var measured: Long? = null
        compose.waitUntil(5_000) {
            val viewportNode = compose.onAllNodesWithTag("reader_text", useUnmergedTree = true).fetchSemanticsNodes().single()
            val state = controller.state.value
            val nodes = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("TXT fragment") {
                it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }?.startsWith("reader_fragment_") == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
            measured = compose.runOnIdle {
                val viewport = viewportNode.boundsInRoot
                nodes.sortedBy { it.positionInRoot.y }.firstNotNullOfOrNull { node ->
                    if (node.boundsInRoot.height <= 0 || node.boundsInRoot.bottom <= viewport.top || node.boundsInRoot.top >= viewport.bottom)
                        return@firstNotNullOfOrNull null
                    val layouts = mutableListOf<TextLayoutResult>()
                    node.config.getOrElseNullable(SemanticsActions.GetTextLayoutResult) { null }?.action?.invoke(layouts)
                    val layout = layouts.singleOrNull() ?: return@firstNotNullOfOrNull null
                    val index = node.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].removePrefix("reader_fragment_").toInt()
                    val line = layout.getLineForVerticalPosition((viewport.top - node.positionInRoot.y).coerceAtLeast(0f))
                    state.cache.fragments[index].start + layout.getLineStart(line)
                }
            }
            measured != null
        }
        return requireNotNull(measured)
    }

    private fun reader(): TxtReaderController {
        var result: TxtReaderController? = null
        compose.activityRule.scenario.onActivity {
            result = ViewModelProvider(it)[AppViewModel::class.java].state.value.reader
        }
        return requireNotNull(result)
    }

    private fun saved(book: Book): ReadingProgress? = runBlocking {
        withContext(Dispatchers.IO) {
            val revision = requireNotNull(book.currentRevision)
            when (val read = RoomProgressRepository(observerDatabase).loadProgress(book.id, revision)) {
                is ProgressReadResult.Found -> read.progress
                ProgressReadResult.Missing -> null
                is ProgressReadResult.Error -> fixtureProgressReadFailed(observerDatabase, book.id, revision, read)
            }
        }
    }

    private fun offset(progress: ReadingProgress): Long = (progress.locator as TxtLocator).payload.utf16Offset

    private fun books(): List<Book> = runBlocking { withContext(Dispatchers.IO) {
        when (val library = RoomBookRepository(observerDatabase).loadBooks()) {
            is LibraryState.Content -> library.books
            LibraryState.Empty -> emptyList()
            else -> error("Fixture library read failed")
        }
    } }

    private fun sendFixture(id: String) {
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName(instrumentation.context, TestSenderActivity::class.java)
            putExtra("sendAction", Intent.ACTION_VIEW)
            putExtra("fixture", id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun waitFor(tag: String) {
        try {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            }
        } catch (failure: Throwable) {
            // Fixture-only diagnostics preserve the timeout and its original assertion.
            runCatching {
                compose.activityRule.scenario.onActivity { activity ->
                    val app = ViewModelProvider(activity)[AppViewModel::class.java].state.value
                    val reader = app.reader
                    failure.addSuppressed(AssertionError("Waiting for $tag: reader=${reader != null}, " +
                        "phase=${reader?.state?.value?.phase}, encoding=${reader?.book?.encodingId}, " +
                        "revision=${reader?.book?.currentRevision}, notice=${app.notice}"))
                }
                ReaderFixtureScreenshot.capture("txt-$tag-timeout.png")
            }.onFailure { failure.addSuppressed(it) }
            throw failure
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
