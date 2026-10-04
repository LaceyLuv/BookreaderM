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
import java.io.File

/** Exercises real Android provider streams, Activity result grants, exported intents and Room. */
class ManagedImportAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
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
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
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
