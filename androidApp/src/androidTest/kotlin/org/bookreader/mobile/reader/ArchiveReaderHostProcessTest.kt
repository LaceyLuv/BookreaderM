package org.bookreader.mobile.reader

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Properties
import kotlinx.coroutines.*
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.importing.DeleteResult
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.*
import org.bookreader.mobile.model.*
import org.bookreader.mobile.repository.*
import org.bookreader.mobile.ui.AppViewModel
import org.bookreader.mobile.ui.AndroidBookManagement
import org.junit.*
import org.junit.Assert.*

class EpubReaderHostProcessTest : ArchiveReaderHostProcessTest() { override val format = "epub" }
class ComicReaderHostProcessTest : ArchiveReaderHostProcessTest() { override val format = "comic" }

/** Exact stages are selected by the external host; no Activity recreation substitutes for process death. */
abstract class ArchiveReaderHostProcessTest {
    protected abstract val format: String
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val database by lazy { createAndroidDatabase(target) }
    private val proof get() = File(target.filesDir, "$format-host-committed.properties")
    @Before fun openObserver() { runBlocking { database.bookDao().listBooks() } }
    @After fun closeObserver() { try { compose.activityRule.scenario.close() } finally { database.close() } }

    @Test fun prepareDurableReader() {
        val initial = books().map { it.id }.toSet()
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName(instrumentation.context, TestSenderActivity::class.java)
            putExtra("fixture", "$format-product"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        waitFor("${format}_ready")
        val book = currentBook()
        if (format == "epub") {
            compose.onNodeWithTag("epub_toc").performClick()
            compose.onAllNodesWithTag("epub_toc_item")[1].performClick()
            compose.waitUntil(20_000) { (saved(book)?.locator as? EpubLocator)?.payload?.locator?.get("href")?.toString()?.contains("two.xhtml") == true }
        } else {
            compose.onNodeWithTag("comic_next").performClick()
            compose.waitUntil(20_000) { (saved(book)?.locator as? ComicLocator)?.payload?.entryKey == "02.png" }
        }
        val committed = requireNotNull(saved(book))
        val properties = Properties().apply {
            setProperty("bookId", book.id); setProperty("revision", committed.contentRevision)
            setProperty("locator", LocatorCodec().encode(committed.locator))
            setProperty("sequence", committed.writerSequence.toString())
            setProperty("epoch", committed.writerSessionEpoch.toString())
            setProperty("updatedAt", committed.updatedAt.toString())
            setProperty("createdByThisHostFlow", (book.id !in initial).toString())
        }
        java.io.FileOutputStream(proof).use { properties.store(it, "Generated archive fixture acknowledged position"); it.fd.sync() }
    }
    @Test fun verifyColdLibraryContinue() {
        assertTrue(proof.isFile)
        val expected = Properties().apply { proof.inputStream().use(::load) }
        waitFor("library_content")
        compose.onNodeWithTag("txt_reader").assertDoesNotExist()
        compose.onNodeWithTag("epub_reader").assertDoesNotExist()
        compose.onNodeWithTag("comic_reader").assertDoesNotExist()
        val book = books().single { it.id == expected.getProperty("bookId") }
        val before = requireNotNull(saved(book))
        assertEquals(expected.getProperty("revision"), before.contentRevision)
        assertEquals(expected.getProperty("locator"), LocatorCodec().encode(before.locator))
        assertEquals(expected.getProperty("sequence").toLong(), before.writerSequence)
        assertEquals(expected.getProperty("epoch").toLong(), before.writerSessionEpoch)
        assertEquals(expected.getProperty("updatedAt").toLong(), before.updatedAt)
        compose.onNodeWithTag("continue_reading").performClick(); waitFor("${format}_ready")
        assertEquals(book.id, currentBook().id)
        assertEquals(before, saved(book))
        compose.activityRule.scenario.onActivity { activity ->
            val state = ViewModelProvider(activity)[AppViewModel::class.java].state.value
            val accepted = if (format == "epub") state.epubReader!!.progress.state.value.accepted else state.comicReader!!.progress.state.value.accepted
            assertEquals(before.locator, accepted)
            if (format == "epub") {
                val wrapper = activity.supportFragmentManager.fragments.filterIsInstance<org.bookreader.mobile.epub.EpubHostFragment>().single()
                val sdk = wrapper.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single().currentLocator.value.toJSON()
                val stored = org.json.JSONObject((before.locator as EpubLocator).payload.locator.toString())
                assertEquals(stored.getString("href"), sdk.getString("href"))
                assertEquals(stored.getJSONObject("locations").optDouble("progression", 0.0), sdk.getJSONObject("locations").optDouble("progression", 0.0), .03)
            } else assertEquals((before.locator as ComicLocator).payload.entryKey,
                state.comicReader!!.state.value.manifest!!.entryKeys[state.comicReader!!.state.value.index])
        }
        compose.activityRule.scenario.close()
        if (expected.getProperty("createdByThisHostFlow") == "true") runBlocking {
            assertEquals(DeleteResult.Deleted, AndroidBookManagement(target).delete(book.id))
        }
        assertTrue(proof.delete())
    }
    private fun currentBook(): Book {
        var book: Book? = null
        compose.activityRule.scenario.onActivity {
            val state = ViewModelProvider(it)[AppViewModel::class.java].state.value
            book = if (format == "epub") state.epubReader?.book else state.comicReader?.book
        }
        return requireNotNull(book)
    }
    private fun saved(book: Book): ReadingProgress? = runBlocking { withContext(Dispatchers.IO) {
        when (val read = RoomProgressRepository(database).loadProgress(book.id, requireNotNull(book.currentRevision))) {
            is ProgressReadResult.Found -> read.progress
            ProgressReadResult.Missing -> null
            is ProgressReadResult.Error -> error("Fixture progress read failed")
        }
    } }
    private fun books(): List<Book> = runBlocking { withContext(Dispatchers.IO) {
        when (val read = RoomBookRepository(database).loadBooks()) {
            is LibraryState.Content -> read.books
            LibraryState.Empty -> emptyList()
            else -> error("Fixture library read failed")
        }
    } }
    private fun waitFor(tag: String) {
        compose.waitUntil(40_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}
