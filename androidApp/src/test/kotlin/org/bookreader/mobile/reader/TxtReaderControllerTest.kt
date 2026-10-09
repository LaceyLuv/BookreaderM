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
            assertEquals(ReaderFailureStage.LOAD_PROGRESS, controller.state.value.failureDiagnostic?.stage)
            assertEquals("DATABASE_UNAVAILABLE", controller.state.value.failureDiagnostic?.resultCode)
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
            assertEquals(ReaderFailureStage.CACHE_BUILD, failed.failureDiagnostic?.stage)
            assertEquals(0, access.started)
            assertTrue(access.events.isEmpty())
            assertTrue(file.isFile)
        } finally { controller.close() }
    }

    @Test fun activationFailureReportsDatabaseStageAndNeverAllowsSave() = runBlocking {
        val failure = DatabaseFailureDiagnostic(DatabaseFailureStage.ACTIVATE, listOf("SQLiteException"))
        val access = MemoryProgress(activation = ReadySessionResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE),
            activationFailure = failure)
        val controller = controller(fixture(), access)
        try {
            val restoring = await(controller, TxtReaderPhase.RESTORING)
            controller.restored(restoring.restoreGeneration)
            val failed = await(controller, TxtReaderPhase.ERROR)
            assertEquals(ReaderFailureDiagnostic(ReaderFailureStage.ACTIVATE, "DATABASE_UNAVAILABLE",
                database = failure), failed.failureDiagnostic)
            controller.visibleAnchor(20, true)
            controller.flush()
            assertTrue(access.events.isEmpty())
        } finally { controller.close() }
    }

    @Test fun saveFailureReportsExactStageAndSuccessfulRetryClearsDiagnostic() = runBlocking {
        val access = MemoryProgress(saveResult = ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE))
        val controller = controller(fixture(), access)
        try {
            val restoring = await(controller, TxtReaderPhase.RESTORING)
            controller.restored(restoring.restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            controller.visibleAnchor(20, true)
            controller.flush()
            assertTrue(controller.state.value.saveError)
            assertEquals(ReaderFailureStage.SAVE, controller.state.value.failureDiagnostic?.stage)
            assertEquals("DATABASE_UNAVAILABLE", controller.state.value.failureDiagnostic?.resultCode)
            access.saveResult = ProgressWriteResult.Committed
            controller.flush()
            assertFalse(controller.state.value.saveError)
            assertNull(controller.state.value.failureDiagnostic)
        } finally { controller.close() }
    }

    @Test fun thrownLoadFailureKeepsBoundedCauseTypesAndNeverIncludesMessages() = runBlocking {
        val privateMessage = "private content://example/book SQL SELECT private_book"
        val access = object : ReaderProgressAccess {
            override suspend fun load(bookId: String, revision: String): ProgressReadResult =
                throw IllegalStateException(privateMessage, java.io.IOException(privateMessage))
            override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: ContentLocator?): ReadySessionResult = error("Must not activate")
            override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult = error("Must not save")
            override suspend fun flush() = Unit
        }
        val controller = controller(fixture(), access)
        try {
            val failed = await(controller, TxtReaderPhase.ERROR)
            assertEquals(listOf("IllegalStateException", "IOException"), failed.failureDiagnostic?.exceptionTypes)
            assertFalse(failed.failureDiagnostic.toString().contains(privateMessage))
            controller.flush()
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

    private fun controller(fixture: Pair<File, Book>, access: ReaderProgressAccess) = TxtReaderController(
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

    private class MemoryProgress(
        private val result: ProgressReadResult = ProgressReadResult.Missing,
        private val activation: ReadySessionResult? = null,
        private val activationFailure: DatabaseFailureDiagnostic? = null,
        @Volatile var saveResult: ProgressWriteResult = ProgressWriteResult.Committed,
    ) : ReaderProgressAccess {
        @Volatile var started = 0
        override fun lastDatabaseFailure() = if (started > 0) activationFailure else null
        val events = CopyOnWriteArrayList<ProgressWriteEvent>()
        override suspend fun load(bookId: String, revision: String) = result
        override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: ContentLocator?): ReadySessionResult {
            started++
            return activation ?: ReadySessionResult.Ready(ProgressSession(bookId, revision, started.toLong()))
        }
        override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult {
            events += event
            return saveResult
        }
        override suspend fun flush() = Unit
    }
}
