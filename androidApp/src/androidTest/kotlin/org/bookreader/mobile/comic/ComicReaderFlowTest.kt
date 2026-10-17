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
        compose.onNodeWithTag("comic_settings").performClick()
        compose.onNodeWithTag("comic_mode_PAGES").performClick()
        compose.onNodeWithTag("comic_direction_RTL").performClick()
        compose.onNodeWithTag("comic_settings_apply").performClick()
        compose.waitUntil(20_000) { active.state.value.settings.readingMode == org.bookreader.mobile.settings.TxtReadingMode.PAGES &&
            active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        waitFor("comic_ready")
        compose.onNodeWithTag("comic_image").performTouchInput { swipeRight() }
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "02.png" && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        compose.onNodeWithTag("comic_image").performTouchInput { swipeLeft() }
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "01.png" && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        compose.onNodeWithTag("comic_image").performTouchInput { doubleClick(center) }
        compose.onNodeWithTag("comic_image").performTouchInput {
            down(0, center - Offset(30f, 0f)); down(1, center + Offset(30f, 0f))
            moveTo(0, center - Offset(100f, 0f)); moveTo(1, center + Offset(100f, 0f))
            up(0); up(1)
        }
        compose.onNodeWithTag("comic_image").performTouchInput { swipeLeft() }
        assertEquals("01.png", active.state.value.manifest!!.entryKeys[active.state.value.index])
        compose.onNodeWithTag("comic_next").performClick()
        waitFor("comic_ready")
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "02.png" }
        val beforeBroken = saved(book)
        compose.onNodeWithTag("comic_next").performClick()
        waitFor("comic_image_error")
        assertEquals(beforeBroken, saved(book))
        compose.onNodeWithTag("comic_skip").performClick()
        waitFor("comic_ready")
        compose.waitUntil(15_000) { saved(book)?.payload?.entryKey == "04.png" }
        compose.onNodeWithTag("comic_resolution_limit").assertIsDisplayed()
        compose.onNodeWithTag("comic_bookmarks").performClick()
        compose.onNodeWithTag("comic_bookmark_add").performClick()
        waitFor("comic_bookmark_item")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithTag("comic_settings").performClick()
        compose.onNodeWithTag("comic_webtoon").performClick()
        compose.onNodeWithTag("comic_settings_apply").performClick()
        compose.waitUntil(20_000) { active.state.value.settings.readingMode == org.bookreader.mobile.settings.TxtReadingMode.SCROLL &&
            active.state.value.settings.comicGapDp == 0 && active.progress.state.value.phase == org.bookreader.mobile.reader.ArchiveReaderPhase.READY }
        waitFor("comic_ready")
        compose.onNodeWithTag("comic_enlarge_04.png").performClick()
        waitFor("comic_zoom_dialog")
        compose.onNodeWithTag("comic_zoom_close").performClick()
        waitFor("comic_ready")
        compose.onNodeWithTag("comic_continuous").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val settled = active.progress.state.value.accepted as ComicLocator
        assertTrue(settled.payload.normalizedY > 0)
        compose.waitUntil(15_000) { saved(book) == settled }
        val anchor = saved(book)
        compose.activityRule.scenario.recreate(); waitFor("comic_ready")
        assertEquals(anchor, saved(book))
        compose.onNodeWithTag("comic_back").performClick(); waitFor("continue_reading")
        compose.onNodeWithTag("continue_reading").performClick(); waitFor("comic_ready")
        assertEquals(anchor, saved(book))
        compose.onNodeWithTag("comic_menu").performClick()
        compose.onNodeWithTag("comic_seek").performTouchInput {
            down(center); moveTo(androidx.compose.ui.geometry.Offset(width * .1f, centerY)); cancel()
        }
        compose.waitForIdle(); android.os.SystemClock.sleep(400)
        assertEquals(anchor, saved(book))
        compose.onNodeWithTag("comic_seek").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.waitUntil(20_000) { saved(book)?.payload?.entryKey == "01.png" }
        compose.onNodeWithTag("comic_menu_close").performClick()
        compose.onNodeWithTag("comic_return").performClick()
        compose.waitUntil(20_000) { saved(book) == anchor }
        assertArrayEquals(source, File(target.filesDir, "managed/${book.managedRelativePath}").readBytes())
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
