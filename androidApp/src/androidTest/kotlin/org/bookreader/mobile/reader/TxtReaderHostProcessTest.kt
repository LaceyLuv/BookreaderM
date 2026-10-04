package org.bookreader.mobile.reader

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.ReadingProgress
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.RoomProgressRepository
import org.bookreader.mobile.repository.RoomBookRepository
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.ui.AppViewModel
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.importing.DeleteResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Two host-selected stages. The mandatory host script force-stops between them; no recreate claim. */
class TxtReaderHostProcessTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val proof get() = File(target.filesDir, "txt-host-committed.properties")

    @Test fun prepareDurableReader() {
        val initialIds = runBlocking { withContext(Dispatchers.IO) {
            val database = createAndroidDatabase(target)
            try { when (val library = RoomBookRepository(database).loadBooks()) {
                is LibraryState.Content -> library.books.map { it.id }.toSet()
                LibraryState.Empty -> emptySet()
                else -> error("Fixture library read failed")
            } } finally { database.close() }
        } }
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName(instrumentation.context, TestSenderActivity::class.java)
            putExtra("sendAction", Intent.ACTION_VIEW)
            putExtra("fixture", "reader-host")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        waitFor("reader_ready")
        val controller = reader()
        repeat(8) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitUntil(10_000) { controller.state.value.stableOffset?.let { it > 0 } == true }
        val expected = requireNotNull(controller.state.value.stableOffset)
        compose.waitUntil(10_000) { saved(controller.book)?.let { offset(it) == expected } == true }
        val committed = requireNotNull(saved(controller.book))
        ReaderFixtureScreenshot.capture("txt-reader-generated-host.png")
        val properties = Properties().apply {
            setProperty("bookId", controller.book.id)
            setProperty("revision", committed.contentRevision)
            setProperty("utf16Offset", offset(committed).toString())
            setProperty("sequence", committed.writerSequence.toString())
            setProperty("epoch", committed.writerSessionEpoch.toString())
            setProperty("updatedAt", committed.updatedAt.toString())
            setProperty("createdByThisHostFlow", (controller.book.id !in initialIds).toString())
        }
        // Proof is written only after the actual Room commit is observed.
        java.io.FileOutputStream(proof).use { output ->
            properties.store(output, "Generated fixture committed TXT anchor")
            output.fd.sync()
        }
        assertTrue(proof.isFile)
    }

    @Test fun verifyColdLibraryContinue() {
        assertTrue("Host prepare stage must commit its fixture first", proof.isFile)
        val properties = Properties().apply { proof.inputStream().use(::load) }
        waitFor("library_content")
        compose.onNodeWithTag("tab_LIBRARY").assertIsDisplayed()
        compose.onNodeWithTag("txt_reader").assertDoesNotExist()
        val expectedOffset = properties.getProperty("utf16Offset").toLong()
        val bookId = properties.getProperty("bookId")
        val revision = properties.getProperty("revision")
        val before = saved(bookId, revision)
        assertNotNull(before)
        assertEquals(expectedOffset, offset(requireNotNull(before)))
        assertEquals(properties.getProperty("sequence").toLong(), before.writerSequence)
        assertEquals(properties.getProperty("epoch").toLong(), before.writerSessionEpoch)
        assertEquals(properties.getProperty("updatedAt").toLong(), before.updatedAt)
        compose.onNodeWithTag("continue_reading").performClick()
        waitFor("reader_ready")
        assertEquals(bookId, reader().book.id)
        assertEquals(expectedOffset, reader().state.value.targetOffset)
        assertEquals(before, saved(bookId, revision))
        compose.activityRule.scenario.close()
        if (properties.getProperty("createdByThisHostFlow") == "true") {
            runBlocking { withContext(Dispatchers.IO) {
                assertEquals(DeleteResult.Deleted, AndroidBookManagement(target).delete(bookId))
            } }
        }
        proof.delete() // Fixture-only cleanup; any book that existed before the host flow is preserved.
    }

    private fun saved(book: Book) = saved(book.id, requireNotNull(book.currentRevision))
    private fun saved(bookId: String, revision: String): ReadingProgress? = runBlocking {
        withContext(Dispatchers.IO) {
            val database = createAndroidDatabase(target)
            try { when (val read = RoomProgressRepository(database).loadProgress(bookId, revision)) {
                is ProgressReadResult.Found -> read.progress
                ProgressReadResult.Missing -> null
                is ProgressReadResult.Error -> error("Fixture progress read failed: ${read.code}")
            } }
            finally { database.close() }
        }
    }
    private fun offset(progress: ReadingProgress) = (progress.locator as TxtLocator).payload.utf16Offset
    private fun reader(): TxtReaderController {
        var result: TxtReaderController? = null
        compose.activityRule.scenario.onActivity {
            result = ViewModelProvider(it)[AppViewModel::class.java].state.value.reader
        }
        return requireNotNull(result)
    }
    private fun waitFor(tag: String) {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}
