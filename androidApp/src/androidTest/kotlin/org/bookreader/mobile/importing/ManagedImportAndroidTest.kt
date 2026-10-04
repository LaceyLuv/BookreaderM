package org.bookreader.mobile.importing

import android.app.Instrumentation
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.view.accessibility.AccessibilityNodeInfo
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.repository.RoomBookRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.bookreader.mobile.ui.AndroidBookManagement
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import androidx.lifecycle.ViewModelProvider
import org.bookreader.mobile.ui.AppViewModel
import org.bookreader.mobile.ui.ImportUiState
import org.bookreader.mobile.reader.TxtReaderPhase
import java.io.File

/** Exercises real Android provider streams, Activity result grants, exported intents and Room. */
class ManagedImportAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val testName = TestName()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext

    private var originalIds = emptySet<String>()
    @Before fun rememberOriginalLibrary() { originalIds = books().map { it.id }.toSet() }
    @After fun removeOnlyTestImports() {
        compose.activityRule.scenario.close()
        runBlocking { withContext(Dispatchers.IO) {
            val management = AndroidBookManagement(target)
            books().filter { it.id !in originalIds }.forEach { management.delete(it.id) }
        } }
    }

    @Test fun stalledProviderCopyCancelsAndRemovesPrivateStaging() {
        val initial = books().map { it.id }
        val monitor = installPicker("stalled")
        try {
            compose.onNodeWithTag("add_txt").performClick()
            waitFor("import_progress")
            compose.waitUntil(10_000) {
                File(target.filesDir, "managed/imports").listFiles().orEmpty().any { it.extension == "part" && it.length() > 0 }
            }
            compose.onNodeWithText("${"부분 본문\n".toByteArray().size} bytes · 전체 크기 미상").assertIsDisplayed()
            compose.onNodeWithTag("cancel_import").performClick()
            waitFor("import_error")
            assertEquals(initial, books().map { it.id })
            val staging = File(target.filesDir, "managed/imports")
            assertTrue(staging.listFiles().orEmpty().none { it.extension == "part" })
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun safButtonOpensDocumentAndCommitsPrivateCopyWithoutChangingSource() {
        val monitor = installPicker("unknown")
        try {
            compose.onNodeWithTag("add_txt").performClick()
            waitFor("import_success")
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, monitor.observed?.action)
            assertTrue(monitor.observed?.hasCategory(Intent.CATEGORY_OPENABLE) == true)
            assertEquals("*/*", monitor.observed?.type)
            val book = books().first { it.originalDisplayName == "../../unknown.txt" }
            val copy = File(File(target.filesDir, "managed"), book.managedRelativePath)
            val uri = document("unknown")
            val source = target.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            assertTrue(copy.readBytes().contentEquals(source))
            captureFixtureScreenshot("m02-library")
            assertTrue(book.managedRelativePath.matches(Regex("books/[0-9a-f-]{36}/original")))
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun systemDocumentsPickerSelectsTxtFromRealProvider() {
        // This case deliberately has no ActivityMonitor: actual DocumentsUI supplies the grant.
        val uri = document("system-picker")
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
            target.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION))
        compose.onNodeWithTag("add_txt").performClick()
        clickSystemNode("Show roots", description = true)
        clickSystemNode("BookReader test TXT")
        clickSystemNode("System picker fixture.txt")
        waitFor("import_success")
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            target.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(books().any { it.originalDisplayName == "System picker fixture.txt" })
        compose.onNodeWithTag("import_read").performClick()
        waitFor("reader_ready")
        compose.onNodeWithText("시스템 파일 선택기로 추가한 실제 TXT입니다.", substring = true).assertIsDisplayed()
    }

    private fun clickSystemNode(label: String, description: Boolean = false) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
            if (root != null) pending.add(root)
            var inspected = 0
            while (pending.isNotEmpty() && inspected++ < 1000) {
                val node = pending.removeFirst()
                val value = if (description) node.contentDescription else node.text
                if (value?.toString() == label) {
                    var clickable: AccessibilityNodeInfo? = node
                    repeat(6) {
                        val current = clickable
                        if (current != null && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                        clickable = current?.parent
                    }
                }
                for (child in 0 until node.childCount) node.getChild(child)?.let(pending::addLast)
            }
            SystemClock.sleep(100)
        }
        error("System DocumentsUI did not expose selectable node: $label")
    }

    @Test fun externalViewReceivesTemporaryGrantAndOpensActualReader() {
        sendFromProvider(Intent.ACTION_VIEW, "utf8")
        waitFor("reader_ready")
        compose.onNodeWithText("실제 문서 제공자에서 가져온 TXT입니다.", substring = true).assertIsDisplayed()
        captureFixtureScreenshot("m03-reader")
        assertTrue(books().any { it.originalDisplayName == "../../utf8.txt" })
        assertEquals(Intent.ACTION_MAIN, compose.activity.intent.action)
        assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED, compose.activityRule.scenario.state)
        compose.activityRule.scenario.recreate()
        waitFor("reader_ready")
        compose.onNodeWithText("실제 문서 제공자에서 가져온 TXT입니다.", substring = true).assertIsDisplayed()
    }

    @Test fun externalSendRequiresConfirmationAndReadOpensOnlyAfterCommit() {
        val initial = books().size
        sendFromProvider(Intent.ACTION_SEND, "unknown")
        waitFor("confirm_share")
        assertEquals(initial, books().size)
        compose.onNodeWithTag("confirm_share").performClick()
        waitFor("import_success")
        compose.onNodeWithTag("import_read").performClick()
        waitFor("reader_ready")
    }

    @Test fun manualEncodingUsesStagingAfterSourceGrantIsRevoked() {
        val monitor = installPicker("legacy")
        try {
            compose.onNodeWithTag("add_txt").performClick()
            waitFor("encoding_EUC_KR")
            target.revokeUriPermission(document("legacy"), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            compose.onNodeWithTag("encoding_EUC_KR").performClick()
            waitFor("import_success")
            assertEquals("euc-kr", books().first { it.originalDisplayName == "../../legacy.txt" }.encodingId)
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun assetSegmentImportHonorsOffsetAndDeclaredLength() {
        val monitor = installPicker("segment")
        try {
            compose.onNodeWithTag("add_txt").performClick()
            waitFor("import_success")
            val book = books().first { it.originalDisplayName == "segment.txt" }
            val copy = File(File(target.filesDir, "managed"), book.managedRelativePath)
            assertEquals(TestAssetProvider.TEXT, copy.readText())
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun binaryAndOversizeDocumentsFailWithoutChangingExistingLibrary() {
        val original = books().map { it.id }
        for (fixture in listOf("binary", "oversize")) {
            val monitor = installPicker(fixture)
            try {
                compose.onNodeWithTag("add_txt").performClick()
                waitFor("import_error")
                assertEquals(original, books().map { it.id })
                assertEquals(if (fixture == "binary") ImportErrorCode.UNSUPPORTED_FORMAT else ImportErrorCode.TOO_LARGE,
                    (currentAppState()?.importing as? ImportUiState.Error)?.code)
                compose.onNodeWithText("닫기").performClick()
            } finally { instrumentation.removeMonitor(monitor) }
        }
    }

    @Test fun unknownSizeSourceEnforcesActualStreamingByteLimit() {
        val monitor = installPicker("unknown")
        try {
            compose.onNodeWithTag("add_txt").performClick()
            waitFor("import_success")
            runBlocking { withContext(Dispatchers.IO) {
                val files = AndroidManagedImportFiles(target)
                val request = files.request(IncomingDocument(document("unknown"), DocumentOrigin.PICKER))
                assertEquals(null, request.expectedBytes)
                val path = "imports/${java.util.UUID.randomUUID()}.part"
                try {
                    files.copySource(request, path, 16) {}
                    error("Source exceeding actual byte limit was accepted")
                } catch (failure: ImportFailure) { assertEquals(ImportErrorCode.TOO_LARGE, failure.code) }
                finally { files.remove(path) }
            } }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun emptyTxtReportsNoContentWithoutCreatingReadingProgress() {
        val monitor = installPicker("empty")
        try {
            compose.onNodeWithTag("add_txt").performClick()
            waitFor("import_success")
            compose.onNodeWithTag("import_read").performClick()
            waitFor("reader_error")
            compose.onNodeWithText("내용이 없는 텍스트 파일입니다.").assertIsDisplayed()
            val book = books().first { it.originalDisplayName == "../../empty.txt" }
            assertEquals(null, book.lastReadAt)
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun managedPromotionRefusesExistingTargetAndRenamesPrivateStaging() = runBlocking {
        withContext(Dispatchers.IO) {
            val files = AndroidManagedImportFiles(target)
            val stage = "imports/${java.util.UUID.randomUUID()}.part"
            val final = "books/${java.util.UUID.randomUUID()}/original"
            val root = File(target.filesDir, "managed")
            val stagedFile = File(root, stage).apply { parentFile!!.mkdirs(); writeText("new fixture") }
            val finalFile = File(root, final).apply { parentFile!!.mkdirs(); writeText("existing fixture") }
            try {
                try {
                    files.promote(stage, final)
                    error("Promotion replaced an existing private copy")
                } catch (failure: ImportFailure) { assertEquals(ImportErrorCode.FILE_IO, failure.code) }
                assertEquals("new fixture", stagedFile.readText())
                assertEquals("existing fixture", finalFile.readText())
                files.remove(final)
                files.promote(stage, final)
                assertTrue(!stagedFile.exists())
                assertEquals("new fixture", finalFile.readText())
            } finally { files.remove(stage); files.remove(final) }
        }
    }

    @Test fun ownUidProviderIsRejectedBeforeAnyQuery() = runBlocking {
        val uri = Uri.parse("content://org.bookreader.mobile.private-test/private.txt")
        target.grantUriPermission(target.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            withContext(Dispatchers.IO) { AndroidManagedImportFiles(target).request(IncomingDocument(uri, DocumentOrigin.VIEW)) }
            error("Importer accepted an app-private provider")
        } catch (failure: ImportFailure) { assertEquals(ImportErrorCode.PERMISSION_DENIED, failure.code) }
    }

    @Test fun ungrantedAndAmbiguousIntentsAreRejectedWithoutLibraryChange() {
        val initial = books().map { it.id }
        val uri = document("ungranted")
        target.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        target.startActivity(Intent(Intent.ACTION_VIEW).apply {
            component = ComponentName(target, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        waitFor("import_error")
        assertEquals(ImportErrorCode.PERMISSION_DENIED, (currentAppState()?.importing as? ImportUiState.Error)?.code)
        assertEquals(initial, books().map { it.id })
        listOf("file:///sdcard/book.txt", "https://example.org/book.txt").forEach {
            assertEquals(DocumentIntentResult.Rejected, DocumentIntents.parseExternal(Intent(Intent.ACTION_VIEW, Uri.parse(it))))
        }
        val mismatched = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, document("utf8"))
            clipData = ClipData.newRawUri("malicious alternate", document("legacy"))
        }
        assertEquals(DocumentIntentResult.Rejected, DocumentIntents.parseExternal(mismatched))
    }

    private fun captureFixtureScreenshot(name: String) {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("captureFixtureScreenshots") != "true" || originalIds.isNotEmpty()) return
        val directory = args.getString("additionalTestOutputDir")?.let(::File) ?: return
        check(directory.isDirectory || directory.mkdirs())
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { java.io.FileOutputStream(File(directory, "$name.png")).use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        } } finally { bitmap.recycle() }
    }

    private fun document(id: String): Uri = DocumentsContract.buildDocumentUri("org.bookreader.mobile.tests.documents", id)
    private fun waitFor(tag: String) {
        try {
            compose.waitUntil(20_000) {
                val found = compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                if (!found) {
                    val state = currentAppState()
                    val failure = state?.importing as? ImportUiState.Error
                    if (tag != "import_error" && failure != null) {
                        throw AssertionError("Expected $tag but import failed: code=${failure.code}, phase=${failure.phase}")
                    }
                    if (tag != "reader_error" && state?.reader?.state?.value?.phase == TxtReaderPhase.ERROR) {
                        throw AssertionError("Expected $tag but reader entered ERROR")
                    }
                }
                found
            }
            compose.onNodeWithTag(tag).assertIsDisplayed()
        } catch (failure: Throwable) {
            val diagnostic = appDiagnostic()
            captureFailureDiagnostics(tag, diagnostic)
            throw AssertionError("Expected $tag; $diagnostic", failure)
        }
    }

    private fun currentAppState(): org.bookreader.mobile.ui.AppUiState? {
        var state: org.bookreader.mobile.ui.AppUiState? = null
        runCatching { compose.activityRule.scenario.onActivity {
            state = ViewModelProvider(it)[AppViewModel::class.java].state.value
        } }
        return state
    }

    private fun appDiagnostic(): String {
        val state = currentAppState()
        val error = state?.importing as? ImportUiState.Error
        val working = state?.importing as? ImportUiState.Working
        val lifecycle = runCatching { compose.activityRule.scenario.state }.getOrNull()
        return "lifecycle=$lifecycle, import=${state?.importing?.javaClass?.simpleName}, " +
            "errorCode=${error?.code}, phase=${error?.phase ?: working?.progress?.phase}, " +
            "library=${state?.library?.javaClass?.simpleName}, readerPhase=${state?.reader?.state?.value?.phase}"
    }

    private fun captureFailureDiagnostics(expectedTag: String, diagnostic: String) {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("captureFixtureScreenshots") != "true" || originalIds.isNotEmpty()) return
        val directory = args.getString("additionalTestOutputDir")?.let(::File) ?: return
        runCatching {
            check(directory.isDirectory || directory.mkdirs())
            val name = testName.methodName
            File(directory, "$name-state.txt").writeText("expected=$expectedTag\n$diagnostic\n")
            // Record structure only: no URI, document name, accessibility text, or book body.
            val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
            instrumentation.uiAutomation.rootInActiveWindow?.let(pending::add)
            val structure = StringBuilder()
            var count = 0
            while (pending.isNotEmpty() && count++ < 1000) {
                val node = pending.removeFirst()
                structure.append(node.packageName).append('|').append(node.className).append('|')
                    .append(node.viewIdResourceName).append("|clickable=").append(node.isClickable).append('\n')
                for (child in 0 until node.childCount) node.getChild(child)?.let(pending::addLast)
            }
            File(directory, "$name-ui-structure.txt").writeText(structure.toString())
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            if (bitmap != null) try {
                java.io.FileOutputStream(File(directory, "$name-failure.png")).use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
        }
    }
    private fun books() = runBlocking {
        withContext(Dispatchers.IO) {
            val db = createAndroidDatabase(target)
            try { when (val state = RoomBookRepository(db).loadBooks()) {
                LibraryState.Empty -> emptyList()
                is LibraryState.Content -> state.books
                else -> error("Actual Room library query failed: $state")
            } }
            finally { db.close() }
        }
    }
    private fun sendFromProvider(action: String, fixture: String) {
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName(instrumentation.context, TestSenderActivity::class.java)
            putExtra("sendAction", action); putExtra("fixture", fixture)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
    private fun installPicker(fixture: String): PickerMonitor = PickerMonitor(fixture).also(instrumentation::addMonitor)
    private inner class PickerMonitor(private val fixture: String) : Instrumentation.ActivityMonitor() {
        @Volatile var observed: Intent? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action == Intent.ACTION_OPEN_DOCUMENT) {
                observed = Intent(intent)
                intent.component = ComponentName(instrumentation.context, TestPickerActivity::class.java)
                intent.putExtra("fixture", fixture)
            }
            return null
        }
    }
}
