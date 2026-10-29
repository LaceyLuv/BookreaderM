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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.bookreader.mobile.library.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.repository.RoomBookRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
            val copied = android.text.format.Formatter.formatShortFileSize(target, "부분 본문\n".toByteArray().size.toLong())
            compose.onNodeWithText("$copied · 전체 크기 확인 중").assertIsDisplayed()
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
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        try {
            val uri = document("system-picker")
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,
                target.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION))
            compose.onNodeWithTag("add_txt").performClick()
            clickSystemNode("Show roots", description = true)
            clickSystemNode("BookReader test TXT")
            clickSystemNode("System picker fixture.txt", scrollDocuments = true)
            waitFor("import_success")
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
                target.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION))
            assertTrue(books().any { it.originalDisplayName == "System picker fixture.txt" })
            compose.onNodeWithTag("import_read").performClick()
            waitFor("reader_ready")
            compose.onNodeWithText("시스템 파일 선택기로 추가한 실제 TXT입니다.", substring = true).assertIsDisplayed()
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun clickSystemNode(label: String, description: Boolean = false, scrollDocuments: Boolean = false) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var lastVisible = emptyList<String>()
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
            if (root != null) {
                root.refresh()
                // Ask the live window too: a RecyclerView's cached child snapshot may
                // predate binding its document labels.
                if (!description) pending.addAll(root.findAccessibilityNodeInfosByText(label))
                pending.add(root)
            }
            var inspected = 0
            val visible = mutableListOf<String>()
            var documentList: AccessibilityNodeInfo? = null
            while (pending.isNotEmpty() && inspected++ < 1000) {
                val node = pending.removeFirst()
                if (scrollDocuments && node.isScrollable && node.isVisibleToUser && node.isEnabled &&
                    node.viewIdResourceName?.endsWith(":id/dir_list") == true) documentList = node
                val value = if (description) node.contentDescription else node.text
                if (node.isVisibleToUser && visible.size < 80 && (node.text != null || node.contentDescription != null)) {
                    // Keep diagnostics structural; other DocumentsUI roots can contain private names.
                    visible.add("target=${value?.toString() == label}, id=${node.viewIdResourceName}, clickable=${node.isClickable}")
                }
                if (node.isVisibleToUser && node.isEnabled && value?.toString() == label) {
                    var clickable: AccessibilityNodeInfo? = node
                    repeat(6) {
                        val current = clickable
                        // ACTION_CLICK can be accepted for a label without activating its row.
                        if (current != null && current.isClickable && current.isEnabled && current.isVisibleToUser &&
                            current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                        clickable = current?.parent
                    }
                    // API26 DocumentsUI can expose both the title and its row as
                    // non-clickable even when the document is visible and enabled.
                    // Tap only the exact, visible fixture label selected above.
                    if (scrollDocuments) {
                        val bounds = android.graphics.Rect()
                        node.getBoundsInScreen(bounds)
                        val windowBounds = android.graphics.Rect()
                        root?.getBoundsInScreen(windowBounds)
                        if (bounds.intersect(windowBounds) && !bounds.isEmpty) {
                            tapSystemPoint(bounds.exactCenterX(), bounds.exactCenterY())
                            return
                        }
                    }
                }
                for (child in 0 until node.childCount) node.getChild(child)?.let(pending::addLast)
            }
            lastVisible = visible
            // DocumentsUI only exposes bound RecyclerView rows. The fixture sorts after
            // the other test documents and can be below the API26 grid viewport.
            val scrolled = documentList?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
            SystemClock.sleep(if (scrolled) 250 else 100)
        }
        captureFailureDiagnostics("system-picker-node", "missing fixture selector=$label; visible=$lastVisible")
        error("System DocumentsUI did not expose selectable node: $label; visible=$lastVisible")
    }

    private fun tapSystemPoint(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue("DocumentsUI fixture touch must be delivered",
                    instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
            if (action == android.view.MotionEvent.ACTION_DOWN) SystemClock.sleep(50)
        }
    }

    @Test fun externalViewReceivesTemporaryGrantAndOpensActualReader() {
        val shared = org.bookreader.mobile.database.AndroidDatabaseOwner.borrow(target)
        val libraryLease = org.bookreader.mobile.ui.androidLibrarySessionFactory(compose.activity).open()
        libraryLease.close()
        assertSame(shared, org.bookreader.mobile.database.AndroidDatabaseOwner.borrow(target))
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

    @Test fun batchPickerCommitsMixedFormatsCountsDuplicateAndContinuesPastBadFile() {
        val initial=books().size
        val monitor=PickerMonitor("unknown",arrayOf("unknown","utf8","duplicate-utf8","binary","epub-product","comic-product")).also(instrumentation::addMonitor)
        try {
            compose.onNodeWithTag("add_txt").performClick()
            compose.waitUntil(30_000) { currentAppState()?.batch?.active == false }
            val result=checkNotNull(currentAppState()?.batch)
            assertEquals(6,result.completed);assertEquals(4,result.added);assertEquals(1,result.duplicates);assertEquals(1,result.failures)
            assertEquals(initial+4,books().size)
            assertTrue(books().map { it.format }.containsAll(org.bookreader.mobile.model.BookFormat.entries))
            waitFor("batch_results")
            compose.onNodeWithTag("batch_results").performClick()
            captureFixtureScreenshot("phase2-batch-results")
            assertEquals(6,result.results.size)
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun cancellingBatchKeepsCommittedBookAndRemovesOnlyPendingStaging() {
        val initial=books().map { it.id }.toSet()
        val monitor=PickerMonitor("unknown",arrayOf("unknown","stalled","epub-product")).also(instrumentation::addMonitor)
        try {
            compose.onNodeWithTag("add_txt").performClick()
            compose.waitUntil(15_000) { currentAppState()?.batch?.completed==1 &&
                File(target.filesDir,"managed/imports").listFiles().orEmpty().any { it.extension=="part" && it.length()>0 } }
            compose.onNodeWithTag("cancel_import").performClick()
            compose.waitUntil(15_000) { currentAppState()?.batch?.active==false }
            val result=checkNotNull(currentAppState()?.batch)
            assertTrue(result.cancelled);assertEquals(1,result.added);assertEquals(1,result.completed)
            assertEquals(1,books().count { it.id !in initial })
            assertTrue(File(target.filesDir,"managed/imports").listFiles().orEmpty().none { it.extension=="part" })
            assertTrue(!runBlocking { AndroidBookManagement(target).consumeInterruptedBatch() })
            compose.activityRule.scenario.recreate()
            waitFor("library_content")
            assertEquals(1,books().count { it.id !in initial })
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun batchEncodingChoiceResumesRemainingFilesAndRejectsAmbiguousExternalBatch() {
        val monitor=PickerMonitor("legacy",arrayOf("legacy","unknown")).also(instrumentation::addMonitor)
        try {
            compose.onNodeWithTag("add_txt").performClick()
            waitFor("encoding_EUC_KR")
            compose.onNodeWithTag("encoding_EUC_KR").performClick()
            compose.waitUntil(20_000) { currentAppState()?.batch?.active==false }
            assertEquals(2,currentAppState()?.batch?.added)
            assertEquals("euc-kr",books().first { it.originalDisplayName=="../../legacy.txt" }.encodingId)
            val clips=ClipData.newRawUri("fixture",document("unknown")).apply { addItem(ClipData.Item(document("utf8"))) }
            val intent=Intent(Intent.ACTION_VIEW,document("unknown")).apply { clipData=clips }
            assertEquals(DocumentIntentResult.Rejected,DocumentIntents.parseExternal(intent))
            assertTrue(DocumentIntents.parsePickerBatch(intent) is DocumentIntentResult.AcceptedBatch)
            intent.data=document("legacy")
            assertEquals(DocumentIntentResult.Rejected,DocumentIntents.parsePickerBatch(intent))
            val tooMany=ClipData.newRawUri("fixture",document("first"))
            repeat(64) { tooMany.addItem(ClipData.Item(document("fixture-$it"))) }
            assertEquals(DocumentIntentResult.Rejected,DocumentIntents.parsePickerBatch(Intent().apply { clipData=tooMany }))
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun metadataAndChosenCoverPersistAcrossRecreationWithoutChangingManagedBook() {
        var monitor=installPicker("unknown")
        try {
            compose.onNodeWithTag("add_txt").performClick();waitFor("import_success")
            compose.onNodeWithTag("dismiss_import_success").performClick()
            val before=books().first { it.originalDisplayName=="../../unknown.txt" }
            val original=File(target.filesDir,"managed/${before.managedRelativePath}").readBytes()
            waitFor("info_${before.id}")
            compose.onNodeWithTag("info_${before.id}").performClick()
            compose.onNodeWithTag("edit_book").performClick()
            compose.onNodeWithTag("edit_book_title").performTextReplacement("내가 정한 제목")
            compose.onNodeWithTag("edit_book_author").performTextReplacement("직접 입력 작가")
            hideKeyboard()
            compose.onNodeWithTag("edit_status_FINISHED").performScrollTo().performClick()
            instrumentation.removeMonitor(monitor);monitor=installPicker("cover-image")
            compose.onNodeWithTag("choose_book_cover").performScrollTo().performClick()
            compose.waitUntil(15_000) { currentAppState()?.pendingCoverKey!=null && currentAppState()?.coverLoading==false }
            captureFixtureScreenshot("phase2-book-editor")
            compose.onNodeWithTag("save_book_edit").performClick()
            compose.waitUntil(15_000) { currentAppState()?.editingBook==null && books().first { it.id==before.id }.title=="내가 정한 제목" }
            compose.activityRule.scenario.recreate();waitFor("library_content")
            val after=books().first { it.id==before.id }
            assertEquals("내가 정한 제목",after.title);assertEquals("직접 입력 작가",after.author)
            assertTrue(after.coverKey?.startsWith("u-")==true)
            val bitmap=runBlocking { BookCoverStore.get(target).bitmap(after.coverKey) }
            assertTrue(bitmap!=null && bitmap.width==96 && bitmap.height==144)
            assertTrue(original.contentEquals(File(target.filesDir,"managed/${after.managedRelativePath}").readBytes()))
            assertEquals(before.copy(title=after.title,author=after.author,coverKey=after.coverKey,updatedAt=after.updatedAt),after)
            assertEquals(ReadingStatus.FINISHED,currentAppState()?.catalog?.details?.get(after.id)?.readingStatus)
            captureFixtureScreenshot("phase2-library")
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun shelfClassificationViewSortAndFilterSurviveActivityRecreation() {
        val manager=RoomLibraryManager(org.bookreader.mobile.database.AndroidDatabaseOwner.borrow(target),System::currentTimeMillis)
        val original=runBlocking { manager.load() }
        var created:String?=null
        val monitor=installPicker("unknown")
        try {
            compose.onNodeWithTag("add_txt").performClick();waitFor("import_success")
            compose.onNodeWithTag("dismiss_import_success").performClick()
            val book=books().first { it.originalDisplayName=="../../unknown.txt" }
            compose.onNodeWithTag("manage_shelves").performScrollTo().performClick()
            compose.onNodeWithTag("new_shelf_name").performTextReplacement("검증 책장")
            hideKeyboard()
            compose.onNodeWithTag("create_shelf").performClick()
            compose.waitUntil(10_000) { currentAppState()?.catalog?.shelves?.any { it.name=="검증 책장" }==true }
            created=currentAppState()!!.catalog.shelves.single { it.name=="검증 책장" }.id
            compose.onNodeWithText("닫기").performClick()
            compose.onNodeWithTag("book_${book.id}").performTouchInput { longClick() }
            waitFor("classify_books");compose.onNodeWithTag("classify_books").performClick()
            compose.onNodeWithTag("classify_add_$created").performClick()
            compose.waitUntil(10_000) { currentAppState()?.catalog?.memberships?.get(book.id)?.contains(created)==true }
            compose.onNodeWithTag("clear_selection").performClick()
            compose.onNodeWithTag("library_view").performScrollTo().performClick()
            compose.onNodeWithTag("library_sort").performScrollTo().performClick()
            compose.onNodeWithTag("sort_TITLE").performClick()
            compose.onNodeWithTag("library_filter").performScrollTo().performClick()
            compose.onNodeWithTag("filter_UNREAD").performClick()
            compose.onNodeWithTag("library_shelf_filter").performScrollTo().performClick()
            compose.onNodeWithText("검증 책장").performClick()
            val expected=LibraryPreferences(LibrarySort.TITLE,LibraryView.LIST,LibraryFilter.UNREAD,created)
            compose.waitUntil(10_000) { currentAppState()?.catalog?.preferences==expected }
            compose.activityRule.scenario.recreate();waitFor("library_content")
            assertEquals(expected,currentAppState()?.catalog?.preferences)
            assertEquals(setOf(created),currentAppState()?.catalog?.memberships?.get(book.id))
            compose.onNodeWithTag("book_${book.id}").assertIsDisplayed()
            captureFixtureScreenshot("phase2-shelf-list")
        } finally {
            instrumentation.removeMonitor(monitor)
            runBlocking { created?.let { manager.deleteShelf(it) };manager.preferences(original.preferences) }
        }
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

    private fun hideKeyboard() {
        compose.activityRule.scenario.onActivity { activity ->
            val input=activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            input.hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
        }
        compose.waitForIdle()
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
        val reader = state?.reader?.state?.value
        return "lifecycle=$lifecycle, import=${state?.importing?.javaClass?.simpleName}, " +
            "errorCode=${error?.code}, phase=${error?.phase ?: working?.progress?.phase}, " +
            "library=${state?.library?.javaClass?.simpleName}, readerPhase=${reader?.phase}, " +
            "readerError=${reader?.error}, sourceAvailability=${reader?.sourceAvailability}, " +
            "readerFailure=${reader?.failureDiagnostic}, " +
            "cacheComplete=${reader?.cache?.complete}, restoreGeneration=${reader?.restoreGeneration}, targetOffset=${reader?.targetOffset}"
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
    private inner class PickerMonitor(private val fixture: String, private val fixtures: Array<String>? = null) : Instrumentation.ActivityMonitor() {
        @Volatile var observed: Intent? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action == Intent.ACTION_OPEN_DOCUMENT) {
                observed = Intent(intent)
                intent.component = ComponentName(instrumentation.context, TestPickerActivity::class.java)
                intent.putExtra("fixture", fixture)
                fixtures?.let { intent.putExtra("fixtures", it) }
            }
            return null
        }
    }
}
