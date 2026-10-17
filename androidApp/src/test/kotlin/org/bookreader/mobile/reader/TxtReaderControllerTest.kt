package org.bookreader.mobile.reader

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

    @Test fun pageNavigationCommitsOnlyAfterCurrentGenerationIsMeasuredAndKeepsEpoch() = runBlocking {
        val memory = MemoryProgress()
        val active = controller(fixture(), memory)
        try {
            active.restored(await(active, TxtReaderPhase.RESTORING).restoreGeneration)
            await(active, TxtReaderPhase.READY)
            active.visibleAnchor(20, true)
            active.flush()
            val before = memory.events.last()
            val oldGeneration = active.state.value.restoreGeneration
            active.navigateTo(40)
            active.restored(oldGeneration)
            active.flush()
            assertEquals(TxtReaderPhase.REFLOWING, active.state.value.phase)
            assertEquals(before, memory.events.last())
            active.restored(active.state.value.restoreGeneration)
            await(active, TxtReaderPhase.READY)
            withTimeout(3_000) { active.state.first { it.stableOffset == 40L } }
            active.flush()
            assertEquals(40L, offset(memory.events.last()))
            assertEquals(before.session, memory.events.last().session)
            assertEquals(1, memory.started)
            val navigated = memory.events.last()
            active.beginReflow()
            active.visibleAnchor(0, false)
            active.restored(active.state.value.restoreGeneration)
            await(active, TxtReaderPhase.READY)
            active.flush()
            assertEquals(navigated, memory.events.last())
        } finally { active.close() }
    }

    @Test fun failedSettingsReadUsesDisplayDefaultsWithoutAutomaticOrExplicitOverwrite() = runBlocking {
        val data = fixture()
        val memory = MemoryProgress()
        var settingsWrites = 0
        val settings = object : ReaderSettingsAccess {
            override suspend fun load(bookId: String) = org.bookreader.mobile.settings.ReaderSettingsRead.Error
            override suspend fun save(expected: org.bookreader.mobile.settings.LoadedReaderSettings,
                global: org.bookreader.mobile.settings.ReaderSettings,
                book: org.bookreader.mobile.settings.BookReaderPreferences): org.bookreader.mobile.settings.ReaderSettingsWrite {
                settingsWrites++
                return org.bookreader.mobile.settings.ReaderSettingsWrite.SAVED
            }
        }
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val active = TxtReaderController(data.second, data.first, temporary.newFolder(), memory, owner, settings)
        try {
            val readyToRestore = await(active, TxtReaderPhase.RESTORING)
            assertTrue(readyToRestore.settingsError)
            assertEquals(org.bookreader.mobile.settings.ReaderSettings(), readyToRestore.settings)
            active.restored(readyToRestore.restoreGeneration)
            await(active, TxtReaderPhase.READY)
            active.saveSettings(org.bookreader.mobile.settings.ReaderSettings(fontSizeSp = 24f),
                org.bookreader.mobile.settings.BookReaderPreferences())
            active.flush()
            assertEquals(0, settingsWrites)
            assertTrue(memory.events.isEmpty())
        } finally { active.close(); owner.cancel() }
    }

    @Test fun restorationCallbackDuringPendingActivationStartsOnlyOneSession() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val memory = MemoryProgress()
        val allowActivation = CompletableDeferred<Unit>()
        lateinit var active: TxtReaderController
        val access = object : ReaderProgressAccess by memory {
            override suspend fun startReadySession(bookId: String, revision: String,
                restoredLocator: ContentLocator?): ReadySessionResult {
                val started = memory.startReadySession(bookId, revision, restoredLocator)
                // A repeated completion callback must not activate another epoch while this one waits.
                if (memory.started == 1) active.restored(active.state.value.restoreGeneration)
                allowActivation.await()
                return started
            }
        }
        active = controller(fixture(), access, owner)
        try {
            val restoring = await(active, TxtReaderPhase.RESTORING)
            active.restored(restoring.restoreGeneration)
            assertEquals(1, memory.started)
            allowActivation.complete(Unit)
            await(active, TxtReaderPhase.READY)
            active.visibleAnchor(20, true)
            active.flush()
            assertEquals(1, memory.started)
            assertEquals(1L, memory.events.single().session.sessionEpoch)
            assertEquals(20L, offset(memory.events.single()))
            assertFalse(active.state.value.saveError)
        } finally {
            allowActivation.complete(Unit)
            active.close()
            owner.cancel()
        }
    }

    @Test fun closeCancelsPendingActivationBeforeWaitingForProgressFlush() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val memory = MemoryProgress()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val databaseMutex = Mutex()
        val access = object : ReaderProgressAccess by memory {
            override suspend fun startReadySession(bookId: String, revision: String,
                restoredLocator: ContentLocator?): ReadySessionResult = databaseMutex.withLock {
                entered.complete(Unit)
                // Model AndroidReaderProgressAccess holding its mutex while waiting for Room.
                release.await()
                memory.startReadySession(bookId, revision, restoredLocator)
            }
            override suspend fun flush() { databaseMutex.withLock { } }
        }
        val active = controller(fixture(), access, owner)
        try {
            active.restored(await(active, TxtReaderPhase.RESTORING).restoreGeneration)
            withTimeout(5_000) { entered.await() }
            // Close must cancel the pending activation, not require external DB contention to end.
            withTimeout(3_000) { active.close() }
            assertFalse(release.isCompleted)
            assertEquals(0, memory.started)
            assertTrue(memory.events.isEmpty())
            release.complete(Unit)
            assertNotEquals(TxtReaderPhase.READY, active.state.value.phase)
        } finally {
            release.complete(Unit)
            owner.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun sixtySecondsOfUnsettledMovementSamplesEverySecondThenSettles() = runBlocking {
        val scheduler = TestCoroutineScheduler()
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val memory = MemoryProgress()
        val committedAt = mutableListOf<Long>()
        val access = object : ReaderProgressAccess by memory {
            override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult {
                committedAt += scheduler.currentTime
                return memory.save(event)
            }
        }
        val controller = controller(fixture(), access, owner)
        try {
            // Cache IO is real; only after it completes do we advance virtual reader time.
            withTimeout(10_000) {
                while (controller.state.value.phase != TxtReaderPhase.RESTORING || !controller.state.value.cache.complete) {
                    scheduler.runCurrent()
                    delay(5)
                }
            }
            controller.restored(controller.state.value.restoreGeneration)
            scheduler.runCurrent()
            assertEquals(TxtReaderPhase.READY, controller.state.value.phase)
            repeat(600) { index ->
                controller.visibleAnchor(100L + index % 500, true)
                scheduler.advanceTimeBy(100)
                scheduler.runCurrent()
            }
            // No 250ms quiet period occurred. Debounce-only saving must fail this assertion.
            assertEquals((1L..60L).map { it * 1_000 }, committedAt)
            assertEquals((1L..60L).toList(), memory.events.map { it.sequence })
            assertEquals((1..60).map { 100L + (it * 10 - 1) % 500 }, memory.events.map(::offset))
            controller.visibleAnchor(20, true)
            scheduler.advanceTimeBy(249)
            scheduler.runCurrent()
            assertEquals(60, memory.events.size)
            scheduler.advanceTimeBy(1)
            scheduler.runCurrent()
            assertEquals(60_250L, committedAt.last())
            assertEquals(20L, offset(memory.events.last()))
            assertEquals(61L, memory.events.last().sequence)
        } finally {
            owner.cancel()
            scheduler.runCurrent()
            controller.close()
        }
    }

    @Test fun reflowFlushCommitsPendingBackwardAnchorAndIgnoresTemporaryZero() = runBlocking {
        val access = MemoryProgress()
        val controller = controller(fixture(), access, this)
        try {
            controller.restored(await(controller, TxtReaderPhase.RESTORING).restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            controller.visibleAnchor(80, true)
            controller.flush()
            controller.visibleAnchor(20, true)
            controller.beginReflow()
            controller.visibleAnchor(0, true)
            controller.visibleAnchor(100, false)
            controller.flush() // ON_STOP uses this barrier without finishing layout restoration.
            assertEquals(TxtReaderPhase.REFLOWING, controller.state.value.phase)
            assertEquals(listOf(80L, 20L), access.events.map(::offset))
            assertEquals(listOf(1L, 2L), access.events.map { it.sequence })
            controller.flush()
            assertEquals(2, access.events.size)
            controller.restored(controller.state.value.restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            controller.visibleAnchor(0, false)
            controller.flush()
            assertEquals(2, access.events.size)
        } finally { controller.close() }
    }

    @Test fun closeDuringReflowWaitsForPendingSaveBeforeFinishing() = runBlocking {
        val entered = CompletableDeferred<ProgressWriteEvent>()
        val release = CompletableDeferred<Unit>()
        val memory = MemoryProgress()
        var flushed = false
        val access = object : ReaderProgressAccess by memory {
            override suspend fun save(event: ProgressWriteEvent): ProgressWriteResult {
                entered.complete(event)
                release.await()
                return memory.save(event)
            }
            override suspend fun flush() { flushed = true }
        }
        val controller = controller(fixture(), access, this)
        try {
            controller.restored(await(controller, TxtReaderPhase.RESTORING).restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            controller.visibleAnchor(60, true)
            controller.beginReflow()
            controller.visibleAnchor(0, true)
            val closing = async { controller.close() }
            val event = withTimeout(5_000) { entered.await() }
            assertEquals(60L, offset(event))
            assertFalse(closing.isCompleted)
            assertFalse(flushed)
            controller.restored(controller.state.value.restoreGeneration)
            controller.visibleAnchor(0, true)
            release.complete(Unit)
            withTimeout(5_000) { closing.await() }
            assertTrue(flushed)
            assertEquals(listOf(60L), memory.events.map(::offset))
        } finally { release.complete(Unit); controller.close() }
    }

    @Test fun reflowWithoutUserMovementNeverCreatesProgress() = runBlocking {
        val access = MemoryProgress()
        val controller = controller(fixture(), access, this)
        try {
            controller.restored(await(controller, TxtReaderPhase.RESTORING).restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            controller.beginReflow()
            controller.visibleAnchor(0, true)
            controller.flush()
            controller.close()
            assertTrue(access.events.isEmpty())
        } finally { controller.close() }
    }

    @Test fun failedReflowSaveRetainsPendingAnchorForRetry() = runBlocking {
        val access = MemoryProgress(saveResult = ProgressWriteResult.Error(ProgressErrorCode.DATABASE_UNAVAILABLE))
        val controller = controller(fixture(), access, this)
        try {
            controller.restored(await(controller, TxtReaderPhase.RESTORING).restoreGeneration)
            await(controller, TxtReaderPhase.READY)
            controller.visibleAnchor(40, true)
            controller.beginReflow()
            controller.flush()
            assertTrue(controller.state.value.saveError)
            assertEquals(ReaderFailureStage.SAVE, controller.state.value.failureDiagnostic?.stage)
            access.saveResult = ProgressWriteResult.Committed
            controller.visibleAnchor(0, true)
            controller.flush()
            assertFalse(controller.state.value.saveError)
            assertNull(controller.state.value.failureDiagnostic)
            assertEquals(listOf(40L, 40L), access.events.map(::offset))
            assertEquals(listOf(1L, 2L), access.events.map { it.sequence })
        } finally { controller.close() }
    }

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

    private fun controller(fixture: Pair<File, Book>, access: ReaderProgressAccess,
        parentScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)) = TxtReaderController(
        fixture.second, fixture.first, temporary.newFolder(), access,
        parentScope)

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
