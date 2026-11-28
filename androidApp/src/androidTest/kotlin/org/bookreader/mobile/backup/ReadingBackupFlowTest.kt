package org.bookreader.mobile.backup

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.BookmarkEntity
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.importing.DeleteResult
import org.bookreader.mobile.importing.TestPickerActivity
import org.bookreader.mobile.importing.TestSenderActivity
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.reader.ReaderFixtureScreenshot
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.ui.AppViewModel
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadingBackupFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val database by lazy { createAndroidDatabase(app) }
    private var ownsFixtures = false

    @After fun removeOnlyGeneratedBooks() {
        try {
            compose.activityRule.scenario.close()
            if (ownsFixtures) runBlocking {
                for (book in database.bookDao().listBooks().filter { it.originalDisplayName in fixtureNames }) {
                    assertEquals(DeleteResult.Deleted, AndroidBookManagement(app).delete(book.id))
                }
            }
        } finally { database.close() }
    }

    @Test fun settingsBackupPreviewMergeRelinkAndCorruptionPreserveManagedData() {
        // The exporter is exercised against a dedicated fixture library only.
        waitFor("library_empty")
        runBlocking {
            assertTrue(database.bookDao().listBooks().isEmpty())
            assertTrue(database.libraryDao().shelves().isEmpty())
            assertNull(database.libraryDao().preferences())
            assertNull(database.readerSettingsDao().global())
        }
        ownsFixtures = true

        val local = importAndClose("backup-local")
        val original = importAndClose("backup-original")
        val originalBytes = managed(original).readBytes()
        val localBytes = managed(local).readBytes()
        val originalProgress = seed(original, 15L)
        val localProgress = seed(local, 4L)
        val backupName = "backup-test-" + UUID.randomUUID().toString().replace("-", "")
        val backupUri = Uri.parse("content://org.bookreader.mobile.tests.assets/$backupName")
        val picker = BackupPicker(backupName)
        instrumentation.addMonitor(picker)
        try {
            backupMenu()
            compose.onNodeWithTag("backup_export").performClick()
            waitFor("backup_exported")
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.lastAction)
            assertTrue(requireNotNull(picker.lastName).endsWith(".brmbackup"))
            val snapshot = requireNotNull(app.contentResolver.openInputStream(backupUri)).use { ReadingBackupFile.decode(it) }
            assertEquals(setOf(local.sourceSha256, original.sourceSha256), snapshot.books.map { it.sha256 }.toSet())
            assertEquals(2, snapshot.books.sumOf { it.bookmarks.size })
            compose.onNodeWithTag("backup_close").performClick()

            // Simulate a target with a newer position and an original that has to be relinked.
            val keptProgress = localProgress.copy(locatorJson = LocatorCodec().encode(
                TxtLocator(localProgress.contentRevision, TxtLocatorPayload(8))), writerSequence = 2, updatedAt = 7)
            runBlocking {
                database.progressWriterDao().updateProgress(keptProgress)
                database.bookmarkDao().delete(local.id, "backup-${local.id}")
                assertEquals(DeleteResult.Deleted, AndroidBookManagement(app).delete(original.id))
            }
            assertFalse(managed(original).exists())
            backupMenu()
            compose.onNodeWithTag("backup_restore_pick").performClick()
            waitFor("backup_preview")
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.lastAction)
            compose.onNodeWithText("새 책 1권 · 같은 원본 1권").assertExists()
            assertNull(rowByHash(original.sourceSha256))
            assertEquals(keptProgress, progress(local))
            ReaderFixtureScreenshot.capture("phase4-backup-preview.png")

            // The provider's bytes can change after preview. Commit only the verified payload.
            requireNotNull(app.contentResolver.openOutputStream(backupUri, "wt")).use { it.write(byteArrayOf(0, 1, 2)) }
            compose.onNodeWithTag("backup_mode_ADD_MISSING_RECORDS").performScrollTo().performClick()
            compose.onNodeWithTag("backup_commit").performClick()
            waitFor("backup_restored")
            ReaderFixtureScreenshot.capture("phase4-backup-restored.png")
            val restored = requireNotNull(rowByHash(original.sourceSha256))
            assertNotEquals(original.id, restored.id)
            assertEquals("MISSING", restored.availability)
            assertFalse(managed(restored).exists())
            assertEquals(originalProgress.locatorJson, progress(restored)?.locatorJson)
            assertEquals(keptProgress, progress(local))
            assertEquals(local, rowByHash(local.sourceSha256))
            assertEquals(1, bookmarks(local))
            assertEquals(1, bookmarks(restored))
            assertArrayEquals(localBytes, managed(local).readBytes())
            compose.onNodeWithTag("backup_close").performClick()

            val beforeInvalid = books().associateBy { it.id }
            backupMenu()
            compose.onNodeWithTag("backup_restore_pick").performClick()
            waitFor("backup_failed")
            compose.onNodeWithTag("backup_commit").assertDoesNotExist()
            assertEquals(beforeInvalid, books().associateBy { it.id })
            assertEquals(keptProgress, progress(local))
            compose.onNodeWithTag("backup_close").performClick()

            send("backup-original")
            waitFor("reader_ready")
            var openedBook: String? = null
            var targetOffset: Long? = null
            compose.activityRule.scenario.onActivity {
                val opened = requireNotNull(ViewModelProvider(it)[AppViewModel::class.java].state.value.reader)
                openedBook = opened.book.id
                targetOffset = opened.state.value.targetOffset
            }
            assertEquals(restored.id, openedBook)
            assertEquals(15L, targetOffset)
            val verifiedOriginal = requireNotNull(rowByHash(original.sourceSha256))
            assertEquals("READY", verifiedOriginal.availability)
            assertArrayEquals(originalBytes, managed(verifiedOriginal).readBytes())
            assertEquals(originalProgress.locatorJson, progress(verifiedOriginal)?.locatorJson)
            assertEquals(keptProgress, progress(local))
            assertEquals(1, bookmarks(verifiedOriginal))
        } finally { instrumentation.removeMonitor(picker) }
    }

    @Test fun v2AssetBackupRestoresBooksAndFontsDirectlyToReadyAndRejectsTamperedBundle() {
        waitFor("library_empty")
        val originalSettings = runBlocking { database.readerSettingsDao().global() }
        val fontStore = org.bookreader.mobile.reader.UserFontStore(app)
        val existingFonts = fontStore.list().map { it.id }.toSet()
        val fontBytes = app.assets.open("reader-fonts/NanumGothic-Regular.ttf").use { it.readBytes() }
        val fontId = java.security.MessageDigest.getInstance("SHA-256").digest(fontBytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        var installedByTest = false
        ownsFixtures = true
        val backupName = "backup-test-" + UUID.randomUUID().toString().replace("-", "")
        val backupUri = Uri.parse("content://org.bookreader.mobile.tests.assets/$backupName")
        val picker = BackupPicker(backupName)
        instrumentation.addMonitor(picker)
        try {
            val local = importAndClose("backup-local")
            val original = importAndClose("backup-original")
            val originalBytes = managed(original).readBytes()
            val localBytes = managed(local).readBytes()
            val originalProgress = seed(original, 15L)
            val localProgress = seed(local, 4L)

            if (fontStore.find(fontId) == null) {
                val fontDir = File(app.filesDir, "reader-fonts/$fontId").apply { mkdirs() }
                File(fontDir, "font").writeBytes(fontBytes)
                File(fontDir, "label").writeText("NanumGothic-Backup.ttf")
                installedByTest = true
            }
            assertNotNull(fontStore.find(fontId))

            // Export v2 bundle containing both managed book originals and user font.
            backupMenu()
            compose.onNodeWithTag("backup_include_books").performScrollTo().performClick()
            compose.onNodeWithTag("backup_include_fonts").performScrollTo().performClick()
            compose.onNodeWithTag("backup_export").performClick()
            waitFor("backup_exported")
            compose.onNodeWithTag("backup_close").performClick()

            // Remove one book and the user font to simulate restoring onto a library missing both.
            runBlocking {
                assertEquals(DeleteResult.Deleted, AndroidBookManagement(app).delete(original.id))
                fontStore.remove(fontId)
            }
            assertFalse(managed(original).exists())
            assertNull(fontStore.find(fontId))

            // Preview v2 bundle and verify asset summary is displayed before committing.
            backupMenu()
            compose.onNodeWithTag("backup_restore_pick").performClick()
            waitFor("backup_preview")
            compose.onNodeWithTag("backup_preview_assets").assertExists()
            assertNull(rowByHash(original.sourceSha256))
            assertNull(fontStore.find(fontId))

            // Tamper with the external provider bytes after preview: restore must commit only verified staged data.
            val rawV2 = requireNotNull(app.contentResolver.openInputStream(backupUri)).use { it.readBytes() }
            val tampered = rawV2.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0xff).toByte() }
            requireNotNull(app.contentResolver.openOutputStream(backupUri, "wt")).use { it.write(tampered) }

            compose.onNodeWithTag("backup_mode_NEW_BOOKS_ONLY").performScrollTo().performClick()
            compose.onNodeWithTag("backup_commit").performClick()
            waitFor("backup_restored")
            compose.onNodeWithTag("backup_close").performClick()

            // Book must already be READY with fresh UUID and exact original bytes; user font re-registered.
            val restored = requireNotNull(rowByHash(original.sourceSha256))
            assertNotEquals(original.id, restored.id)
            assertEquals("READY", restored.availability)
            assertTrue(managed(restored).isFile)
            assertArrayEquals(originalBytes, managed(restored).readBytes())
            assertEquals(originalProgress.locatorJson, progress(restored)?.locatorJson)
            assertEquals(1, bookmarks(restored))
            assertEquals(local, rowByHash(local.sourceSha256))
            assertEquals(localProgress, progress(local))
            assertArrayEquals(localBytes, managed(local).readBytes())
            val restoredFont = requireNotNull(fontStore.find(fontId))
            assertArrayEquals(fontBytes, restoredFont.file.readBytes())

            // Re-opening the tampered v2 bundle must fail closed with zero DB/file/font mutation.
            val booksBeforeInvalid = books().associateBy { it.id }
            backupMenu()
            compose.onNodeWithTag("backup_restore_pick").performClick()
            waitFor("backup_failed")
            compose.onNodeWithTag("backup_commit").assertDoesNotExist()
            assertEquals(booksBeforeInvalid, books().associateBy { it.id })
            assertArrayEquals(localBytes, managed(local).readBytes())
            assertArrayEquals(originalBytes, managed(restored).readBytes())
            assertNotNull(fontStore.find(fontId))
            compose.onNodeWithTag("backup_close").performClick()

            // Open the restored book directly from Library without re-importing the original file.
            compose.onNodeWithTag("tab_LIBRARY").performClick()
            waitFor("library_content")
            compose.onNodeWithTag("read_${restored.id}").performScrollTo().performClick()
            waitFor("reader_ready")
            var openedBook: String? = null
            var targetOffset: Long? = null
            compose.activityRule.scenario.onActivity {
                val opened = requireNotNull(ViewModelProvider(it)[AppViewModel::class.java].state.value.reader)
                openedBook = opened.book.id
                targetOffset = opened.state.value.targetOffset
            }
            assertEquals(restored.id, openedBook)
            assertEquals(15L, targetOffset)
        } finally {
            instrumentation.removeMonitor(picker)
            runBlocking {
                if (installedByTest && fontId !in existingFonts && fontStore.find(fontId) != null) {
                    runCatching { fontStore.remove(fontId) }
                }
                if (originalSettings == null) {
                    database.useWriterConnection { it.execSQL("DELETE FROM reader_global_settings WHERE id = 'global'") }
                } else {
                    database.readerSettingsDao().putGlobal(originalSettings)
                }
            }
        }
    }

    private fun importAndClose(fixture: String): BookEntity {
        send(fixture)
        waitFor("reader_ready")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("library_content")
        return books().single { it.originalDisplayName == "../../$fixture.txt" }
    }

    private fun seed(book: BookEntity, offset: Long) = runBlocking {
        val revision = requireNotNull(book.currentRevision)
        val value = LocatorCodec().encode(TxtLocator(revision, TxtLocatorPayload(offset)))
        val progress = ReadingProgressEntity(book.id, revision, "txt", 1, value, 12.0, book.activeSessionEpoch, 1, 5)
        val writer = database.progressWriterDao()
        if (database.progressDao().findProgress(book.id, revision) == null) {
            writer.insertProgress(progress)
        } else {
            writer.updateProgress(progress)
        }
        database.bookmarkDao().insert(BookmarkEntity("backup-${book.id}", book.id, revision, "txt", 1, value, "백업 북마크", 5, 5))
        progress
    }

    private fun backupMenu() {
        compose.onNodeWithTag("tab_SETTINGS").performClick()
        compose.onNodeWithTag("settings_content").performScrollToNode(hasTestTag("settings_backup"))
        compose.onNodeWithTag("settings_backup").performClick()
        waitFor("backup_export")
    }

    private fun send(fixture: String) {
        instrumentation.context.startActivity(Intent().apply {
            component = ComponentName(instrumentation.context, TestSenderActivity::class.java)
            putExtra("fixture", fixture)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(40_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun books() = runBlocking { database.bookDao().listBooks() }
    private fun rowByHash(hash: String) = books().singleOrNull { it.sourceSha256 == hash }
    private fun progress(book: BookEntity) = runBlocking { database.progressDao().findProgress(book.id, requireNotNull(book.currentRevision)) }
    private fun bookmarks(book: BookEntity) = runBlocking {
        database.bookmarkDao().page(book.id, requireNotNull(book.currentRevision), Long.MAX_VALUE, "z").size
    }
    private fun managed(book: BookEntity) = File(app.filesDir, "managed/${book.managedRelativePath}")

    private inner class BackupPicker(private val backup: String) : Instrumentation.ActivityMonitor() {
        @Volatile var lastAction: String? = null
        @Volatile var lastName: String? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action == Intent.ACTION_CREATE_DOCUMENT || intent.action == Intent.ACTION_OPEN_DOCUMENT) {
                lastAction = intent.action
                lastName = intent.getStringExtra(Intent.EXTRA_TITLE)
                intent.component = ComponentName(instrumentation.context, TestPickerActivity::class.java)
                intent.putExtra("backupFixture", backup)
            }
            return null
        }
    }

    private companion object {
        val fixtureNames = setOf("../../backup-local.txt", "../../backup-original.txt")
    }
}
