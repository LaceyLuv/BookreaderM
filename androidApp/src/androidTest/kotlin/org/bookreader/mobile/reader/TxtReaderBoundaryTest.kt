package org.bookreader.mobile.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.locator.ContentLocator
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.model.ReadingProgress
import org.bookreader.mobile.repository.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real Compose TextLayout boundary regressions, separate from fake controller-only tests. */
class TxtReaderBoundaryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun eofAfterFinalEmojiRestoresWithoutTreatingLowSurrogateAsInvalid() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(target.cacheDir, "reader-boundary-${UUID.randomUUID()}").apply { mkdirs() }
        val original = File(folder, "original")
        val text = "첫 번째 줄\r\n끝 😀"
        val canonical = text.replace("\r\n", "\n")
        val bytes = text.toByteArray()
        original.writeBytes(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val book = Book("boundary-fixture", BookFormat.TXT, "Generated EOF fixture", "eof.txt",
            "books/boundary/original", hash, bytes.size.toLong(), 1, 1, 1,
            currentRevision = "revision-$hash", encodingId = "utf-8", normalizationVersion = 1)
        val locator = TxtLocator(requireNotNull(book.currentRevision), TxtLocatorPayload(canonical.length.toLong()))
        var activated = false
        var writes = 0
        val access = object : ReaderProgressAccess {
            override suspend fun load(bookId: String, revision: String) = ProgressReadResult.Found(
                ReadingProgress(bookId, locator, 100.0, 1, 1, 1))
            override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: ContentLocator?): ReadySessionResult {
                assertEquals(locator, restoredLocator)
                activated = true
                return ReadySessionResult.Ready(ProgressSession(bookId, revision, 2))
            }
            override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult {
                writes++
                return ProgressWriteResult.Committed
            }
            override suspend fun flush() = Unit
        }
        var controller: TxtReaderController? = null
        try {
            compose.setContent {
                val scope = rememberCoroutineScope()
                val active = androidx.compose.runtime.remember {
                    TxtReaderController(book, original, File(folder, "derived"), access, scope)
                }
                controller = active
                MaterialTheme { TxtReaderScreen(active, {}) }
            }
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("reader_ready").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader_ready").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(activated)
                assertEquals(0, writes)
                assertEquals(canonical.length.toLong(), controller!!.state.value.targetOffset)
            }
        } finally {
            controller?.let { runBlocking { it.close() } }
            folder.deleteRecursively()
        }
    }
}
