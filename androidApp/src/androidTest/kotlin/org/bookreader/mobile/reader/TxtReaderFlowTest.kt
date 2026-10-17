package org.bookreader.mobile.reader

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.AndroidDatabaseOwner
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.ReadingProgress
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.RoomBookRepository
import org.bookreader.mobile.repository.DatabaseFailureStage
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

    @Test fun sixtySecondsOfContinuousTouchKeepsCommittingMeasuredAnchors() {
        val reportFile = File(target.filesDir, "txt-continuous-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        sendFixture("reader-continuous")
        waitFor("reader_ready")
        val controller = reader()
        repeat(8) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitUntil(10_000) { saved(controller.book)?.let { offset(it) > 0 } == true }
        val before = requireNotNull(saved(controller.book))
        val node = compose.onNodeWithTag("reader_text").fetchSemanticsNode()
        val x = node.positionOnScreen.x + node.size.width / 2f
        val upper = node.positionOnScreen.y + node.size.height * 0.3f
        val lower = node.positionOnScreen.y + node.size.height * 0.7f
        val downTime = SystemClock.uptimeMillis()
        val start = SystemClock.elapsedRealtime()
        val commits = JSONArray()
        val anchors = JSONArray()
        val inputTimes = JSONArray()
        var lastSequence = before.writerSequence
        var lastOffset = controller.state.value.stableOffset
        var y = lower
        var held = false
        var passed = false
        val report = JSONObject().apply {
            put("fixture_id", "reader-continuous")
            put("fixture_sha256", controller.book.sourceSha256)
            put("fixture_bytes", controller.book.sourceByteSize)
            put("build_fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("abi", Build.SUPPORTED_ABIS.first())
            put("app_version", target.packageManager.getPackageInfo(target.packageName, 0).versionName)
            put("writer_epoch", before.writerSessionEpoch)
            put("initial_sequence", before.writerSequence)
            put("commits", commits)
            put("anchors", anchors)
            put("input_elapsed_ms", inputTimes)
            put("measurement", "Monotonic real-time touch and independent Room observations; not SQL write-ack latency")
        }
        fun inject(action: Int) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue("Actual pointer injection failed", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
        try {
            inject(MotionEvent.ACTION_DOWN)
            held = true
            while (SystemClock.elapsedRealtime() - start < 60_000) {
                val elapsed = SystemClock.elapsedRealtime() - start
                // One held pointer reverses every two seconds, away from book/viewport boundaries.
                val phase = (elapsed % 4_000) / 2_000f
                y = lower - (lower - upper) * if (phase <= 1f) phase else 2f - phase
                inject(MotionEvent.ACTION_MOVE)
                inputTimes.put(SystemClock.elapsedRealtime() - start)
                compose.waitForIdle() // Pump measured Compose layout without advancing wall-clock time.
                val state = controller.state.value
                assertEquals(TxtReaderPhase.READY, state.phase)
                assertFalse(state.saveError)
                if (state.stableOffset != lastOffset) {
                    lastOffset = state.stableOffset
                    anchors.put(JSONObject().put("elapsed_ms", SystemClock.elapsedRealtime() - start)
                        .put("offset", requireNotNull(lastOffset)))
                }
                val committed = requireNotNull(saved(controller.book))
                assertEquals(before.writerSessionEpoch, committed.writerSessionEpoch)
                assertTrue(committed.writerSequence >= lastSequence)
                if (committed.writerSequence > lastSequence) {
                    lastSequence = committed.writerSequence
                    commits.put(JSONObject().put("elapsed_ms", SystemClock.elapsedRealtime() - start)
                        .put("sequence", committed.writerSequence).put("offset", offset(committed)))
                }
                // Real input pacing; no manual flush/close/restore or fabricated visibleAnchor calls.
                SystemClock.sleep(50)
            }
            val movementDuration = SystemClock.elapsedRealtime() - start
            report.put("movement_duration_ms", movementDuration)
            val inputGaps = (listOf(0L) + (0 until inputTimes.length()).map { inputTimes.getLong(it) } + movementDuration)
                .zipWithNext { a, b -> b - a }
            report.put("max_input_gap_ms", inputGaps.max())
            inject(MotionEvent.ACTION_UP)
            held = false
            compose.waitForIdle()
            val finalAnchor = measuredVisibleLine(controller)
            assertEquals(finalAnchor, controller.state.value.stableOffset)
            compose.waitUntil(10_000) { saved(controller.book)?.let { offset(it) == finalAnchor } == true }
            report.put("gesture_end_to_settled_observation_ms", SystemClock.elapsedRealtime() - start - movementDuration)
            report.put("final_offset", finalAnchor)
            report.put("final_sequence", requireNotNull(saved(controller.book)).writerSequence)
            assertTrue("Must move for at least 60 real seconds", movementDuration >= 60_000)
            assertTrue("Held input must continue at least once per second", inputGaps.max() <= 1_000)
            assertTrue("Measured anchor must change throughout the held gesture", anchors.length() >= 120)
            assertTrue("Periodic durable commits must occur before pointer-up", commits.length() >= 12)
            val observedTimes = listOf(0L) + (0 until commits.length()).map { commits.getJSONObject(it).getLong("elapsed_ms") } + movementDuration
            val maxGap = observedTimes.zipWithNext { a, b -> b - a }.max()
            report.put("max_observed_commit_gap_ms", maxGap)
            assertTrue("Durable commit observation gap exceeded 5s: $maxGap", maxGap <= 5_000)
            for (bucket in 0 until 6) {
                assertTrue("Missing measured movement in 10s window $bucket", (0 until anchors.length()).any {
                    anchors.getJSONObject(it).getLong("elapsed_ms") / 10_000 == bucket.toLong()
                })
                assertTrue("Missing commit in 10s window $bucket", (0 until commits.length()).any {
                    commits.getJSONObject(it).getLong("elapsed_ms") / 10_000 == bucket.toLong()
                })
            }
            passed = true
        } finally {
            try { if (held) inject(MotionEvent.ACTION_CANCEL) }
            finally {
                report.put("status", if (passed) "PASS" else "FAIL")
                reportFile.writeText(report.toString(2))
            }
        }
    }

    @Test fun sqliteFullShowsSaveErrorPreservesRecordsAndRetriesAfterCapacityReturns() {
        val reportFile = File(target.filesDir, "txt-full-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        // Another real imported book/progress must remain unchanged throughout the failure.
        sendFixture("reader-full-other")
        waitFor("reader_ready")
        val other = reader().book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("reader_back").performClick()
        waitFor("library_content")
        val otherProgress = requireNotNull(saved(other))
        val otherRecord = books().single { it.id == other.id }
        val otherFile = File(File(target.filesDir, "managed"), other.managedRelativePath)
        val otherHash = digest(otherFile)

        sendFixture("reader-full")
        waitFor("reader_ready")
        val controller = reader()
        val book = controller.book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val initialAnchor = measuredVisibleLine(controller)
        assertTrue(initialAnchor > 0)
        compose.waitUntil(10_000) { saved(book)?.let { offset(it) == initialAnchor } == true }
        val before = requireNotNull(saved(book))
        val beforeBook = books().single { it.id == book.id }
        val original = File(File(target.filesDir, "managed"), book.managedRelativePath)
        val originalHash = digest(original)
        // Borrow the same one-writer Room pool used by the real Reader; never close it here.
        val database = AndroidDatabaseOwner.borrow(target)
        fun scalar(sql: String): Long = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared(sql) { check(it.step()); it.getLong(0) }
        } }
        fun execute(sql: String) = runBlocking { database.useWriterConnection { it.execSQL(sql) } }
        val originalLimit = scalar("PRAGMA max_page_count")
        val beforeOrder = scalar("SELECT value FROM app_metadata WHERE `key` = 'read_order'")
        val report = JSONObject().apply {
            put("fixture_id", "reader-full")
            put("fixture_sha256", book.sourceSha256)
            put("fixture_bytes", book.sourceByteSize)
            put("other_fixture_sha256", other.sourceSha256)
            put("build_fingerprint", Build.FINGERPRINT)
            put("api", Build.VERSION.SDK_INT)
            put("abi", Build.SUPPORTED_ABIS.first())
            put("mechanism", "Native SQLite max_page_count plus test-only growth trigger; not filesystem ENOSPC")
            put("initial_offset", initialAnchor)
            put("initial_sequence", before.writerSequence)
            put("writer_epoch", before.writerSessionEpoch)
        }
        var passed = false
        try {
            execute("CREATE TABLE full_test_payload (payload BLOB NOT NULL)")
            val bytes = (scalar("PRAGMA page_count") + 4) * scalar("PRAGMA page_size")
            check(bytes in 1..1_048_576)
            // Test-only growth happens after the normal progress UPDATE, in its real transaction.
            check(book.id.matches(Regex("[a-f0-9-]+")))
            execute("CREATE TRIGGER full_test_write AFTER UPDATE ON reading_progress " +
                "WHEN NEW.bookId = '${book.id}' BEGIN INSERT INTO full_test_payload VALUES (zeroblob($bytes)); END")
            val pages = scalar("PRAGMA page_count")
            assertEquals(pages, scalar("PRAGMA max_page_count = $pages"))
            report.put("page_limit", pages).put("page_size", scalar("PRAGMA page_size")).put("growth_bytes", bytes)
            // Confirm code 13 from the native engine on this same constrained writer connection.
            val nativeFailure = runCatching { execute("INSERT INTO full_test_payload VALUES (zeroblob($bytes))") }.exceptionOrNull()
            assertTrue(nativeFailure is SQLiteException)
            assertTrue(nativeFailure?.message.orEmpty().contains("Error code: 13,"))
            val nativeType = requireNotNull(requireNotNull(nativeFailure)::class.simpleName)
            report.put("native_probe_code", 13).put("native_probe_type", nativeType)

            compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
            // The error banner changes the viewport and starts asynchronous reflow.
            // Measure only after its restoration, never during the temporary scroll-to-item frame.
            waitFor("reader_save_error")
            waitFor("reader_ready")
            compose.waitForIdle()
            val pendingAnchor = measuredVisibleLine(controller)
            assertNotEquals(initialAnchor, pendingAnchor)
            val failed = controller.state.value
            assertEquals(TxtReaderPhase.READY, failed.phase)
            assertEquals(pendingAnchor, failed.stableOffset)
            assertTrue(failed.saveError)
            assertEquals(ReaderFailureStage.SAVE, failed.failureDiagnostic?.stage)
            assertEquals(DatabaseFailureStage.SAVE_COMMIT, failed.failureDiagnostic?.database?.stage)
            assertTrue(failed.failureDiagnostic?.database?.exceptionTypes?.contains(nativeType) == true)
            assertEquals(before, saved(book))
            assertEquals(beforeBook, books().single { it.id == book.id })
            assertEquals(otherProgress, saved(other))
            assertEquals(otherRecord, books().single { it.id == other.id })
            assertEquals(beforeOrder, scalar("SELECT value FROM app_metadata WHERE `key` = 'read_order'"))
            assertEquals(0L, scalar("SELECT count(*) FROM full_test_payload"))
            assertEquals(originalHash, digest(original))
            assertEquals(otherHash, digest(otherFile))
            report.put("pending_offset", pendingAnchor).put("save_error_visible", true)
                .put("records_preserved_on_failure", true).put("managed_files_preserved", true)

            // Keep the trigger. Capacity relief must make the same actual write succeed, via retry.
            assertEquals(originalLimit, scalar("PRAGMA max_page_count = $originalLimit"))
            compose.waitUntil(10_000) {
                !controller.state.value.saveError && saved(book)?.let { offset(it) == pendingAnchor } == true
            }
            val committed = requireNotNull(saved(book))
            compose.onNodeWithTag("reader_save_error").assertDoesNotExist()
            assertNull(controller.state.value.failureDiagnostic)
            assertEquals(before.writerSessionEpoch, committed.writerSessionEpoch)
            assertTrue(committed.writerSequence > before.writerSequence)
            assertEquals(1L, scalar("SELECT count(*) FROM full_test_payload"))
            assertEquals(otherProgress, saved(other))
            assertEquals(otherRecord, books().single { it.id == other.id })
            assertEquals(beforeBook, books().single { it.id == book.id })
            assertEquals(beforeOrder, scalar("SELECT value FROM app_metadata WHERE `key` = 'read_order'"))
            assertEquals(originalHash, digest(original))
            assertEquals(otherHash, digest(otherFile))
            report.put("retry_offset", offset(committed)).put("retry_sequence", committed.writerSequence)
                .put("retry_cleared_error", true).put("other_book_preserved_after_retry", true)
            passed = true
        } finally {
            try {
                assertEquals(originalLimit, scalar("PRAGMA max_page_count = $originalLimit"))
                execute("DROP TRIGGER IF EXISTS full_test_write")
                execute("DROP TABLE IF EXISTS full_test_payload")
                report.put("fixture_limit_and_schema_restored", true)
            } finally {
                report.put("status", if (passed && report.optBoolean("fixture_limit_and_schema_restored")) "PASS" else "FAIL")
                reportFile.writeText(report.toString(2))
            }
        }
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

    @Test fun nativeProgressQueryFailureShowsRetryWithoutChangingRecords() {
        verifyProgressReadFailure(nativeQueryFailure = true)
    }

    @Test fun malformedLocatorShowsRetryWithoutReplacingStoredRecord() {
        verifyProgressReadFailure(nativeQueryFailure = false)
    }

    private fun verifyProgressReadFailure(nativeQueryFailure: Boolean) {
        val fixture = if (nativeQueryFailure) "reader-query-error" else "reader-locator-error"
        val reportFile = File(target.filesDir, "$fixture-report.json")
        if (reportFile.exists()) assertTrue(reportFile.delete())
        sendFixture("reader-read-other")
        waitFor("reader_ready")
        val other = reader().book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("reader_back").performClick()
        waitFor("library_content")
        val otherProgress = requireNotNull(saved(other))
        sendFixture(fixture)
        waitFor("reader_ready")
        val book = reader().book
        repeat(3) { compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("reader_back").performClick()
        waitFor("library_content")
        val committed = requireNotNull(saved(book))
        assertTrue(offset(committed) > 0)
        val beforeBooks = books()
        val beforeBook = beforeBooks.single { it.id == book.id }
        val originals = listOf(book, other).map {
            File(File(target.filesDir, "managed"), it.managedRelativePath)
        }
        val hashes = originals.map(::digest)
        // Keep the app-owned pool open. DDL and repair use its serialized writer connection.
        val database = AndroidDatabaseOwner.borrow(target)
        fun execute(sql: String) = runBlocking { database.useWriterConnection { it.execSQL(sql) } }
        fun records(): List<List<String?>> = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared("SELECT * FROM reading_progress ORDER BY 1, 2") { statement ->
                buildList {
                    while (statement.step()) add((0 until 9).map {
                        if (statement.isNull(it)) null else statement.getText(it)
                    })
                }
            }
        } }
        fun readOrder(): Long = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared("SELECT value FROM app_metadata WHERE `key` = 'read_order'") {
                check(it.step()); it.getLong(0)
            }
        } }
        fun locatorJson(value: String) = runBlocking { database.useWriterConnection { connection ->
            connection.usePrepared("UPDATE reading_progress SET locatorJson = ? WHERE bookId = ? AND contentRevision = ?") {
                it.bindText(1, value); it.bindText(2, book.id)
                it.bindText(3, requireNotNull(book.currentRevision)); it.step()
            }
        } }
        val originalRows = records()
        val originalJson = requireNotNull(originalRows.single { it[0] == book.id }[4])
        val beforeOrder = readOrder()
        var faultInstalled = false
        var passed = false
        val report = JSONObject().apply {
            put("fixture_id", fixture).put("fixture_sha256", book.sourceSha256)
            put("fixture_bytes", book.sourceByteSize).put("other_fixture_sha256", other.sourceSha256)
            put("api", Build.VERSION.SDK_INT).put("build_fingerprint", Build.FINGERPRINT)
            put("mechanism", if (nativeQueryFailure) "temporary query-column rename; native SQLite query failure"
                else "test-only malformed locator JSON; real Room repository decoding")
            put("initial_offset", offset(committed)).put("initial_sequence", committed.writerSequence)
            put("initial_epoch", beforeBook.activeSessionEpoch)
        }
        try {
            if (nativeQueryFailure) {
                // Values, rows and foreign-key/index definitions survive the reversible rename.
                // DAO WHERE bookId then fails in SQLite; no fake Error or closed borrowed DB.
                execute("ALTER TABLE reading_progress RENAME COLUMN bookId TO fixture_held_book_id")
            } else locatorJson("{malformed-test-locator")
            faultInstalled = true
            val failedRows = records()
            if (nativeQueryFailure) {
                assertTrue("DDL must preserve every progress value", originalRows == failedRows)
                val nativeFailure = runCatching { runBlocking {
                    observerDatabase.progressDao().findProgress(book.id, requireNotNull(book.currentRevision))
                } }.exceptionOrNull()
                assertTrue(nativeFailure is SQLiteException)
                assertTrue(nativeFailure?.message.orEmpty().contains("Error code: 1,"))
                report.put("native_probe_code", 1)
                report.put("native_probe_type", requireNotNull(nativeFailure)::class.simpleName)
            }
            compose.onNodeWithTag("read_${book.id}").performClick()
            waitFor("reader_error")
            val failedController = reader()
            fun assertPreservedError() {
                val state = failedController.state.value
                assertEquals(TxtReaderPhase.ERROR, state.phase)
                assertEquals(ReaderFailureStage.LOAD_PROGRESS, state.failureDiagnostic?.stage)
                assertEquals(if (nativeQueryFailure) "DATABASE_UNAVAILABLE" else "INVALID_RECORD",
                    state.failureDiagnostic?.resultCode)
                if (nativeQueryFailure) {
                    assertEquals(DatabaseFailureStage.READ_PROGRESS, state.failureDiagnostic?.database?.stage)
                    assertTrue(state.failureDiagnostic?.database?.exceptionTypes?.contains("SQLiteException") == true)
                } else assertNull(state.failureDiagnostic?.database)
                assertNull(state.stableOffset)
                compose.onNodeWithTag("reader_retry").assertIsDisplayed()
                compose.onNodeWithTag("reader_text").assertDoesNotExist()
                assertTrue("Error/retry must preserve all raw progress columns", failedRows == records())
                assertTrue("Error/retry must preserve every book including epoch and read order", beforeBooks == books())
                assertEquals(beforeOrder, readOrder())
                assertEquals(hashes, originals.map(::digest))
            }
            assertPreservedError()
            repeat(2) {
                val generation = failedController.state.value.restoreGeneration
                compose.onNodeWithTag("reader_retry").performClick()
                compose.waitUntil(10_000) {
                    val state = failedController.state.value
                    state.restoreGeneration > generation && state.phase == TxtReaderPhase.ERROR
                }
                // Observe beyond the normal 1s sampling period while the read remains failed.
                SystemClock.sleep(1_250)
                assertPreservedError()
            }
            report.put("failed_retries", 2).put("error_ui_visible", true)
                .put("all_records_preserved_on_error", true).put("managed_files_preserved", true)
            if (nativeQueryFailure) execute("ALTER TABLE reading_progress RENAME COLUMN fixture_held_book_id TO bookId")
            else locatorJson(originalJson)
            faultInstalled = false
            assertTrue("Fixture repair restores original values", originalRows == records())
            compose.onNodeWithTag("reader_retry").performClick()
            waitFor("reader_ready")
            compose.waitForIdle()
            assertSame(failedController, reader())
            assertEquals(offset(committed), reader().state.value.targetOffset)
            assertEquals(offset(committed), measuredVisibleLine(reader()))
            assertEquals(committed, saved(book))
            assertEquals(otherProgress, saved(other))
            assertTrue("Restoration must not write a new progress record", originalRows == records())
            val afterBooks = books()
            assertEquals(beforeBooks.filter { it.id != book.id }, afterBooks.filter { it.id != book.id })
            val restoredBook = afterBooks.single { it.id == book.id }
            assertEquals(beforeBook.activeSessionEpoch + 1, restoredBook.activeSessionEpoch)
            assertEquals(beforeOrder + 1, readOrder())
            assertEquals(hashes, originals.map(::digest))
            assertNull(reader().state.value.failureDiagnostic)
            compose.onNodeWithTag("reader_error").assertDoesNotExist()
            report.put("restored_offset", measuredVisibleLine(reader()))
                .put("restored_epoch", restoredBook.activeSessionEpoch)
                .put("retry_cleared_error", true).put("progress_unchanged_after_restore", true)
                .put("other_book_preserved_after_retry", true)
            // Successful retry must also re-enable ordinary user-driven saving in the new epoch.
            compose.onNodeWithTag("reader_text").performTouchInput { swipeUp() }
            compose.waitForIdle()
            val moved = measuredVisibleLine(reader())
            assertNotEquals(offset(committed), moved)
            compose.waitUntil(10_000) { saved(book)?.let {
                offset(it) == moved && it.writerSessionEpoch == restoredBook.activeSessionEpoch
            } == true }
            assertEquals(otherProgress, saved(other))
            assertEquals(hashes, originals.map(::digest))
            report.put("new_offset", moved).put("new_epoch", requireNotNull(saved(book)).writerSessionEpoch)
                .put("save_after_retry", true)
            passed = true
        } finally {
            try {
                if (faultInstalled) {
                    if (nativeQueryFailure) execute("ALTER TABLE reading_progress RENAME COLUMN fixture_held_book_id TO bookId")
                    else locatorJson(originalJson)
                }
                // Real DAO query must work after cleanup, including a failed assertion path.
                runBlocking { assertNotNull(observerDatabase.progressDao().findProgress(book.id, requireNotNull(book.currentRevision))) }
                report.put("fixture_restored", true)
            } finally {
                report.put("status", if (passed && report.optBoolean("fixture_restored")) "PASS" else "FAIL")
                reportFile.writeText(report.toString(2))
            }
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
