package org.bookreader.mobile.reader

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.ReadingProgress
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.RoomBookRepository
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
        compose.onNodeWithTag("reader_back").performClick()
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

    @Test fun missingManagedCopyShowsRetryAndPreservesCommittedProgress() {
        sendFixture("reader-failure")
        waitFor("reader_ready")
        val controller = reader()
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitUntil(10_000) { controller.state.value.stableOffset?.let { it > 0 } == true }
        compose.onNodeWithTag("reader_back").performClick()
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

    private fun measuredVisibleLine(controller: TxtReaderController): Long {
        val viewport = compose.onNodeWithTag("reader_text").fetchSemanticsNode().positionInRoot.y
        val state = controller.state.value
        for (index in state.cache.fragments.indices) {
            val nodes = compose.onAllNodesWithTag("reader_fragment_$index").fetchSemanticsNodes()
            if (nodes.isEmpty()) continue
            val node = nodes.single()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("reader_fragment_$index").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            val layout = layouts.single()
            if (node.positionInRoot.y + layout.size.height <= viewport) continue
            val line = layout.getLineForVerticalPosition((viewport - node.positionInRoot.y).coerceAtLeast(0f))
            return state.cache.fragments[index].start + layout.getLineStart(line)
        }
        error("No measured visible TXT fragment")
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
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
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
