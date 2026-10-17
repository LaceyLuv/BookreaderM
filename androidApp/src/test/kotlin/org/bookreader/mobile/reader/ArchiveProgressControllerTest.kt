package org.bookreader.mobile.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.*
import org.bookreader.mobile.locator.*
import org.bookreader.mobile.model.*
import org.bookreader.mobile.repository.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveProgressControllerTest {
    private fun book(format: BookFormat = BookFormat.EPUB) = Book("book", format, "Book", "book.epub", "books/book/original", "a".repeat(64), 1, 1, 1, 1, currentRevision = "revision")
    private fun epub(href: String) = EpubLocator("revision", EpubLocatorPayload(locator = buildJsonObject { put("href", href); put("type", "application/xhtml+xml"); putJsonObject("locations") { put("progression", .4); put("unknownFutureSdkKey", "keep") } }))
    private class Memory : ReaderProgressAccess {
        var read: ProgressReadResult = ProgressReadResult.Missing
        var starts = 0
        var fail = false
        var waitActivation: CompletableDeferred<Unit>? = null
        val events = mutableListOf<ProgressWriteEvent>()
        override suspend fun load(bookId: String, revision: String) = read
        override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: ContentLocator?): ReadySessionResult {
            waitActivation?.await()
            starts++
            return ReadySessionResult.Ready(ProgressSession(bookId, revision, 7))
        }
        override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult {
            if (fail) return ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE)
            events += event; return ProgressWriteResult.Committed
        }
        override suspend fun flush() = Unit
    }
    @Test fun failedOrUnsupportedReadCannotActivateOrWriteDefaults() = runTest {
        for (read in listOf(ProgressReadResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE),
            ProgressReadResult.Found(ReadingProgress("book", TxtLocator("revision", TxtLocatorPayload(25)), 10.0, 1, 1, 1)))) {
            val memory = Memory().apply { this.read = read }
            val controller = ArchiveProgressController(book(), memory, this)
            runCurrent()
            assertEquals(ArchiveReaderPhase.ERROR, controller.state.value.phase)
            controller.restored(0); controller.userAnchor(0, epub("zero.xhtml"), 0.0); controller.close()
            assertEquals(0, memory.starts); assertTrue(memory.events.isEmpty())
        }
    }
    @Test fun fullSdkLocatorAndAcceptedBackwardAnchorSurviveReflowErrorAndClose() = runTest {
        val original = epub("chapter-two.xhtml")
        val memory = Memory().apply { read = ProgressReadResult.Found(ReadingProgress("book", original, 60.0, 2, 9, 1)) }
        val controller = ArchiveProgressController(book(), memory, this)
        runCurrent()
        assertEquals(original, controller.state.value.accepted)
        controller.restored(0); controller.restored(0); runCurrent()
        assertEquals(1, memory.starts); assertTrue(memory.events.isEmpty())
        val backward = epub("chapter-one.xhtml")
        controller.userAnchor(0, backward, 20.0)
        val generation = controller.beginReflow()
        controller.userAnchor(0, epub("zero.xhtml"), 0.0)
        controller.restored(0)
        assertEquals(ArchiveReaderPhase.REFLOWING, controller.state.value.phase)
        controller.restorationFailed()
        controller.close()
        assertEquals(backward, memory.events.single().locator)
        assertEquals(7L, memory.events.single().session.sessionEpoch)
        assertEquals(1, generation)
    }
    @Test fun cancellationDuringActivationDoesNotActivateOrFlushUnrestoredLocation() = runTest {
        val memory = Memory().apply { waitActivation = CompletableDeferred() }
        val controller = ArchiveProgressController(book(), memory, this)
        runCurrent(); controller.restored(0); runCurrent()
        controller.close(); memory.waitActivation!!.complete(Unit); runCurrent()
        assertEquals(0, memory.starts); assertTrue(memory.events.isEmpty())
        assertEquals(ArchiveReaderPhase.CLOSED, controller.state.value.phase)
    }
    @Test fun continuousMovesPersistPeriodicallyAndRetryKeepsSessionSequenceAndComicIdentity() = runTest {
        val memory = Memory()
        val controller = ArchiveProgressController(book(BookFormat.COMIC), memory, this)
        runCurrent(); controller.restored(0); runCurrent()
        repeat(30) { index ->
            controller.userAnchor(0, ComicLocator("revision", ComicLocatorPayload("folder/${index % 3}.png", normalizedY = .4)), 30.0)
            advanceTimeBy(100); runCurrent()
        }
        assertTrue(memory.events.size >= 2)
        val before = memory.events.last()
        memory.fail = true
        val latest = ComicLocator("revision", ComicLocatorPayload("folder/final.png", normalizedX = .3, normalizedY = .7))
        controller.userAnchor(0, latest, 75.0); controller.flush()
        assertTrue(controller.state.value.saveError); assertEquals(before, memory.events.last())
        memory.fail = false; controller.flush()
        assertFalse(controller.state.value.saveError)
        assertEquals(latest, memory.events.last().locator)
        assertTrue(memory.events.last().sequence > before.sequence)
        assertTrue(memory.events.all { it.session == before.session })
        controller.close()
    }
}
