package org.bookreader.mobile.comic

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.useWriterConnection
import androidx.room.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import org.bookreader.mobile.reader.readerControl
import org.bookreader.mobile.reader.tapReader
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.importing.DeleteResult
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.ComicLocator
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.repository.*
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.ui.AppViewModel
import org.junit.*
import org.junit.Assert.*

class ComicReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val database by lazy { createAndroidDatabase(target) }
    private var existing: Set<String>? = null
    private var originalGlobal: org.bookreader.mobile.database.ReaderGlobalSettingsEntity? = null
    @Before fun preserveExistingRecords() {
        existing = books().map { it.id }.toSet()
        originalGlobal = runBlocking { database.readerSettingsDao().global() }
    }
    @After fun cleanupOwnedFixtures() {
        try {
            compose.activityRule.scenario.close()
            val preserved = existing ?: return
            books().filter { it.id !in preserved && it.originalDisplayName == "../../comic-product.cbz" }.forEach {
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
    @Test fun managedComicModesSkipZoomBookmarksAndReopenKeepEntryIdentity() {
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName(instrumentation.context, TestSenderActivity::class.java)
            putExtra("fixture", "comic-product"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        waitFor("comic_ready")
        val active = reader(); val book = active.book
        assertEquals(BookFormat.COMIC, book.format)
        val source = target.assets.open("comic-fixtures/product.cbz").use { it.readBytes() }
        assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
        click("comic_settings")
        click("comic_mode_PAGES")
        click("comic_direction_RTL")
        click("comic_settings_apply")
        compose.waitUntil(20_000) { active.state.value.settings.readingMode == org.bookreader.mobile.settings.TxtReadingMode.PAGES &&
            active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        waitFor("comic_ready")
        click("comic_hide")
        compose.onNodeWithTag("comic_chrome").assertDoesNotExist()
        val bounds = compose.onAllNodesWithTag("comic_ready").fetchSemanticsNodes().single().let { compose.runOnIdle { it.boundsInRoot } }
        val generation = active.progress.state.value.generation
        org.bookreader.mobile.reader.ReaderFixtureScreenshot.capture("comic-immersive.png")
        compose.tapReader("comic", .5f); waitFor("comic_chrome")
        assertEquals(generation, active.progress.state.value.generation)
        assertEquals(bounds, compose.onAllNodesWithTag("comic_ready").fetchSemanticsNodes().single().let { compose.runOnIdle { it.boundsInRoot } })
        org.bookreader.mobile.reader.ReaderFixtureScreenshot.capture("comic-controls.png")
        compose.tapReader("comic", .5f)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("comic_chrome").fetchSemanticsNodes().isEmpty() }
        compose.tapReader("comic", .1f)
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "02.png" && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        compose.tapReader("comic", .9f)
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "01.png" && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        touch("comic_image") { b -> swipe(Offset(b.left + b.width * .1f, b.center.y), Offset(b.right - b.width * .1f, b.center.y)) }
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "02.png" && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        touch("comic_image") { b -> swipe(Offset(b.right - b.width * .1f, b.center.y), Offset(b.left + b.width * .1f, b.center.y)) }
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "01.png" && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        touch("comic_image") { b ->
            down(0, b.center); up(0); android.os.SystemClock.sleep(80); down(0, b.center); up(0)
        }
        touch("comic_image") { b ->
            down(0, b.center - Offset(30f, 0f)); down(1, b.center + Offset(30f, 0f))
            moveTo(0, b.center - Offset(100f, 0f)); moveTo(1, b.center + Offset(100f, 0f))
            up(0); up(1)
        }
        touch("comic_image") { b -> swipe(Offset(b.right - b.width * .1f, b.center.y), Offset(b.left + b.width * .1f, b.center.y)) }
        assertEquals("01.png", active.state.value.manifest!!.entryKeys[active.state.value.index])
        click("comic_next")
        waitFor("comic_ready")
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "02.png" }
        val beforeBroken = saved(book)
        click("comic_next")
        waitFor("comic_image_error")
        assertEquals(beforeBroken, saved(book))
        click("comic_skip")
        waitFor("comic_ready")
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "04.png" }
        compose.onNodeWithTag("comic_resolution_limit").assertIsDisplayed()
        click("comic_bookmarks")
        click("comic_bookmark_add")
        waitFor("comic_bookmark_item")
        clickNode(compose.onAllNodesWithText("닫기").fetchSemanticsNodes().single())
        click("comic_settings")
        click("comic_webtoon")
        click("comic_settings_apply")
        compose.waitUntil(20_000) { active.state.value.settings.readingMode == org.bookreader.mobile.settings.TxtReadingMode.SCROLL &&
            active.state.value.settings.comicGapDp == 0 && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        waitFor("comic_ready")
        click("comic_enlarge_04.png")
        waitFor("comic_zoom_dialog")
        click("comic_zoom_close")
        waitFor("comic_ready")
        click("comic_hide")
        touch("comic_continuous") { b -> swipe(Offset(b.center.x, b.bottom - b.height * .1f), Offset(b.center.x, b.top + b.height * .1f)) }
        compose.waitForIdle()
        val settled = active.progress.state.value.accepted as ComicLocator
        assertTrue(settled.payload.normalizedY > 0)
        compose.waitUntil(15_000) { saved(book) == settled }
        val anchor = saved(book)
        compose.activityRule.scenario.recreate(); waitFor("comic_ready")
        assertEquals(anchor, saved(book))
        click("comic_back"); waitFor("continue_reading")
        click("continue_reading"); waitFor("comic_ready")
        assertEquals(anchor, saved(book))
        click("comic_menu")
        touch("comic_seek") { b ->
            down(0, b.center); moveTo(0, Offset(b.left + b.width * .1f, b.center.y)); cancel()
        }
        compose.waitForIdle(); android.os.SystemClock.sleep(400)
        assertEquals(anchor, saved(book))
        val seekNode = compose.onAllNodesWithTag("comic_seek").fetchSemanticsNodes().single()
        compose.runOnIdle { assertTrue(seekNode.config[androidx.compose.ui.semantics.SemanticsActions.SetProgress].action!!.invoke(0f)) }
        compose.waitUntil(20_000) { saved(book)?.payload?.entryKey == "01.png" }
        click("comic_menu_close")
        click("comic_return")
        compose.waitUntil(20_000) { saved(book) == anchor }
        assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
    }
    // Compose 1.9.3 fetchSemanticsNode prints the live tree again on the test thread.
    // Fetch collections (selection on Main), then read properties/actions only on Main.
    private fun click(tag: String) {
        if (tag in setOf("comic_back", "comic_settings", "comic_menu", "comic_next", "comic_previous", "comic_bookmarks", "comic_return") || tag.startsWith("comic_enlarge_"))
            compose.readerControl(tag)
        else if (tag.startsWith("comic_mode_") || tag.startsWith("comic_direction_") || tag.startsWith("comic_fit_") || tag == "comic_webtoon" || tag == "comic_hide")
            compose.onNodeWithTag(tag).performScrollTo()
        clickNode(compose.onAllNodesWithTag(tag).fetchSemanticsNodes().single())
    }
    private fun clickNode(node: androidx.compose.ui.semantics.SemanticsNode) {
        compose.runOnIdle { assertTrue(node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()) }
    }
    private fun touch(tag: String, gesture: FixtureTouch.(androidx.compose.ui.geometry.Rect) -> Unit) {
        val node = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().single()
        val bounds = compose.runOnIdle {
            val view = (node.root as androidx.compose.ui.platform.ViewRootForTest).view
            val screen = IntArray(2); view.getLocationOnScreen(screen)
            node.boundsInRoot.translate(Offset(screen[0].toFloat(), screen[1].toFloat()))
        }
        assertTrue("Gesture target must be visible", bounds.width > 0 && bounds.height > 0)
        FixtureTouch(instrumentation.uiAutomation).gesture(bounds)
        compose.waitForIdle()
    }
    /** Actual Android touch events keep swipe, pinch, double-tap and cancel coverage. */
    private class FixtureTouch(private val automation: android.app.UiAutomation) {
        private val points = linkedMapOf<Int, Offset>()
        private var started = 0L
        fun down(id: Int, point: Offset) {
            if (points.isEmpty()) started = android.os.SystemClock.uptimeMillis()
            points[id] = point
            send(if (points.size == 1) android.view.MotionEvent.ACTION_DOWN else
                android.view.MotionEvent.ACTION_POINTER_DOWN or (points.keys.indexOf(id) shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        }
        fun moveTo(id: Int, point: Offset) { points[id] = point; send(android.view.MotionEvent.ACTION_MOVE) }
        fun up(id: Int) {
            send(if (points.size == 1) android.view.MotionEvent.ACTION_UP else
                android.view.MotionEvent.ACTION_POINTER_UP or (points.keys.indexOf(id) shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT))
            points.remove(id)
        }
        fun cancel() { send(android.view.MotionEvent.ACTION_CANCEL); points.clear() }
        fun swipe(from: Offset, to: Offset) {
            down(0, from)
            for (step in 1..16) {
                android.os.SystemClock.sleep(16)
                moveTo(0, from + (to - from) * (step / 16f))
            }
            up(0)
        }
        private fun send(action: Int) {
            val properties = points.keys.map { id -> android.view.MotionEvent.PointerProperties().apply {
                this.id = id; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray()
            val coordinates = points.values.map { point -> android.view.MotionEvent.PointerCoords().apply {
                x = point.x; y = point.y; pressure = 1f; size = 1f
            } }.toTypedArray()
            val event = android.view.MotionEvent.obtain(started, android.os.SystemClock.uptimeMillis(), action,
                points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue("Android must accept the fixture touch event", automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
    }
    private fun reader(): ComicReaderController {
        var owner: ComicReaderController? = null
        compose.activityRule.scenario.onActivity { owner = ViewModelProvider(it)[AppViewModel::class.java].state.value.comicReader }
        return requireNotNull(owner)
    }
    private fun books(): List<Book> = runBlocking { withContext(Dispatchers.IO) {
        when (val read = RoomBookRepository(database).loadBooks()) {
            is LibraryState.Content -> read.books
            LibraryState.Empty -> emptyList()
            else -> error("Fixture library read failed")
        }
    } }
    private fun saved(book: Book): ComicLocator? = runBlocking { withContext(Dispatchers.IO) {
        when (val read = RoomProgressRepository(database).loadProgress(book.id, requireNotNull(book.currentRevision))) {
            is ProgressReadResult.Found -> read.progress.locator as ComicLocator
            ProgressReadResult.Missing -> null
            is ProgressReadResult.Error -> error("Fixture progress read failed")
        }
    } }
    private fun waitFor(tag: String) {
        compose.waitUntil(40_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}
