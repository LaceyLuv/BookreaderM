package org.bookreader.mobile.reader

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bookreader.mobile.locator.ContentLocator
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.model.ReadingProgress
import org.bookreader.mobile.repository.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TxtReaderControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun readyRequiresSuccessfulProgressReadAndMeasuredRestoration() = runBlocking {
        val fixture = fixture()
        val access = MemoryProgress()
        val controller = controller(fixture, access)
        try {
            val restored = await(controller, TxtReaderPhase.RESTORING)
            controller.visibleAnchor(50, true)
            controller.flush()
            assertEquals(0, access.started)
            assertTrue(access.events.isEmpty())
            controller.restored(restored.restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            assertEquals(1, access.started)
            controller.visibleAnchor(50, false)
            controller.flush()
            assertTrue(access.events.isEmpty())
            controller.visibleAnchor(50, true)
            controller.flush()
            assertEquals(50, offset(access.events.single()))
            assertEquals(1L, access.events.single().sequence)
            controller.beginReflow()
            controller.visibleAnchor(0, true)
            controller.flush()
            assertEquals(1, access.events.size)
        } finally { controller.close() }
    }

    @Test fun continuousMovementSamplesAndBackwardFinalAnchorIsCommitted() = runBlocking {
        val fixture = fixture()
        val access = MemoryProgress()
        val controller = controller(fixture, access)
        try {
            val restored = await(controller, TxtReaderPhase.RESTORING)
            controller.restored(restored.restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            repeat(15) { index -> controller.visibleAnchor((index + 1) * 5L, true); delay(100) }
            // Every event is less than 250ms apart: an event here proves periodic sampling.
            assertFalse(access.events.isEmpty())
            controller.visibleAnchor(20, true)
            delay(350)
            assertEquals(20, offset(access.events.last()))
            assertTrue(access.events.zipWithNext().all { (a, b) -> a.sequence < b.sequence })
        } finally { controller.close() }
    }

    @Test fun progressReadFailureNeverActivatesOrOverwritesRecord() = runBlocking {
        val fixture = fixture()
        val access = MemoryProgress(ProgressReadResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE))
        val controller = controller(fixture, access)
        try {
            await(controller, TxtReaderPhase.ERROR)
            controller.restored(controller.state.value.restoreGeneration)
            controller.visibleAnchor(0, true)
            controller.flush()
            assertEquals(0, access.started)
            assertTrue(access.events.isEmpty())
            assertTrue(fixture.first.isFile)
        } finally { controller.close() }
    }

    @Test fun emptyTxtShowsExplicitErrorWithoutCreatingReadHistory() = runBlocking {
        val file = temporary.newFile()
        file.writeBytes(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val book = Book("empty-fixture", BookFormat.TXT, "Empty", "empty.txt", "books/empty/original",
            hash, file.length(), 1, 1, 1, currentRevision = "revision-$hash", encodingId = "utf-8", normalizationVersion = 1)
        val access = MemoryProgress()
        val controller = controller(file to book, access)
        try {
            val failed = await(controller, TxtReaderPhase.ERROR)
            assertEquals("내용이 없는 텍스트 파일입니다.", failed.error)
            assertEquals(0, access.started)
            assertTrue(access.events.isEmpty())
            assertTrue(file.isFile)
        } finally { controller.close() }
    }

    @Test fun outOfRangeAndLateSourceCorruptionPreservePreviousProgress() = runBlocking {
        val fixture = fixture()
        val old = TxtLocator(requireNotNull(fixture.second.currentRevision), TxtLocatorPayload(9_999))
        val access = MemoryProgress(ProgressReadResult.Found(ReadingProgress(
            fixture.second.id, old, 90.0, 1, 10, 1)))
        val controller = controller(fixture, access)
        try {
            await(controller, TxtReaderPhase.ERROR)
            controller.flush()
            assertEquals(0, access.started)
            assertTrue(access.events.isEmpty())
        } finally { controller.close() }
        val mutated = fixture.first.readBytes().also { it[it.lastIndex] = 'z'.code.toByte() }
        fixture.first.writeBytes(mutated)
        val corruptedAccess = MemoryProgress()
        val corrupted = controller(fixture, corruptedAccess)
        try {
            await(corrupted, TxtReaderPhase.ERROR)
            corrupted.restored(corrupted.state.value.restoreGeneration)
            corrupted.flush()
            assertEquals(0, corruptedAccess.started)
            assertTrue(corruptedAccess.events.isEmpty())
        } finally { corrupted.close() }
        val malformed = fixture.first.readBytes().also { it[it.lastIndex] = 0xff.toByte() }
        fixture.first.writeBytes(malformed)
        val malformedAccess = MemoryProgress(access.load(fixture.second.id, requireNotNull(fixture.second.currentRevision)))
        val malformedController = controller(fixture, malformedAccess)
        try {
            val failed = await(malformedController, TxtReaderPhase.ERROR)
            assertEquals(BookAvailability.CORRUPT, failed.sourceAvailability)
            malformedController.flush()
            assertEquals(0, malformedAccess.started)
            assertTrue(malformedAccess.events.isEmpty())
            assertArrayEquals(malformed, fixture.first.readBytes())
        } finally { malformedController.close() }
    }

    private suspend fun await(controller: TxtReaderController, phase: TxtReaderPhase): TxtReaderState =
        withTimeout(10_000) { controller.state.first { it.phase == phase &&
            (phase != TxtReaderPhase.RESTORING || it.cache.complete) } }

    private fun controller(fixture: Pair<File, Book>, access: MemoryProgress) = TxtReaderController(
        fixture.second, fixture.first, temporary.newFolder(), access,
        CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun fixture(): Pair<File, Book> {
        val file = temporary.newFile()
        val bytes = ("한글 text line\n".repeat(100)).toByteArray()
        file.writeBytes(bytes)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return file to Book("reader-fixture", BookFormat.TXT, "Fixture", "fixture.txt",
            "books/fixture/original", hash, bytes.size.toLong(), 1, 1, 1,
            currentRevision = "revision-$hash", encodingId = "utf-8", normalizationVersion = 1)
    }

    private fun offset(event: ProgressWriteEvent) = (event.locator as TxtLocator).payload.utf16Offset

    private class MemoryProgress(private val result: ProgressReadResult = ProgressReadResult.Missing) : ReaderProgressAccess {
        @Volatile var started = 0
        val events = CopyOnWriteArrayList<ProgressWriteEvent>()
        override suspend fun load(bookId: String, revision: String) = result
        override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: ContentLocator?): ReadySessionResult {
            started++
            return ReadySessionResult.Ready(ProgressSession(bookId, revision, started.toLong()))
        }
        override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult {
            events += event
            return ProgressWriteResult.Committed
        }
        override suspend fun flush() = Unit
    }
}
