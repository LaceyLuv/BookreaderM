package org.bookreader.mobile.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.database.AndroidDatabaseOwner
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.importing.txtContentRevision
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.reader.UserFontStore
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.ui.AppViewModel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test

/**
 * External adb force-stop host verification for Phase 4-2 resumable asset backup commit.
 * Preparation is intentionally killed by scripts/backup-asset-process-check.py after Book 1
 * is promoted and before Book 2 + user font complete, then verified in a cold new OS process.
 */
class BackupAssetHostProcessTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val target get() = instrumentation.targetContext
    private val proof get() = File(target.filesDir, "backup-asset-host.properties")
    private val db get() = AndroidDatabaseOwner.borrow(target)

    private fun state(): org.bookreader.mobile.ui.AppUiState {
        var model: AppViewModel? = null
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java] }
        return checkNotNull(model).state.value
    }

    @Test fun prepareInterruptedAssetRestoreForHostKill() {
        compose.waitUntil(20_000) {
            state().library !is org.bookreader.mobile.repository.LibraryState.Loading
        }
        val fontStore = UserFontStore(target)
        val fontBytes = target.assets.open("reader-fonts/NanumGothic-Regular.ttf").use { it.readBytes() }
        val fontId = sha256(fontBytes)
        val hadFontBefore = fontStore.find(fontId) != null
        val initialIds = runBlocking(Dispatchers.IO) { db.bookDao().listBooks().map { it.id }.toSet() }

        // Seed one pre-existing READY book + progress that must survive force-stop untouched.
        val existingBytes = "Existing host-preserved library book content.\n".repeat(12).toByteArray()
        val existingHash = sha256(existingBytes)
        val existingId = UUID.randomUUID().toString()
        val existingRel = "books/$existingId/original"
        val existingRev = txtContentRevision(existingHash, "utf-8", 1)
        val existingFile = File(target.filesDir, "managed/$existingRel").apply {
            parentFile!!.mkdirs()
            writeBytes(existingBytes)
        }
        val existingLoc = LocatorCodec().encode(TxtLocator(existingRev, TxtLocatorPayload(33L)))

        // Build two new books + one font packaged in a real v2 backup bundle and decoded into private staging.
        val book1Bytes = "Host kill asset book one content.\n".repeat(12).toByteArray()
        val book2Bytes = "Host kill asset book two content.\n".repeat(12).toByteArray()
        val book1Hash = sha256(book1Bytes)
        val book2Hash = sha256(book2Bytes)
        val rev1 = txtContentRevision(book1Hash, "utf-8", 1)
        val rev2 = txtContentRevision(book2Hash, "utf-8", 1)

        runBlocking(Dispatchers.IO) {
            db.bookDao().insertBook(
                BookEntity(
                    id = existingId, format = "TXT", title = "Existing Host Book",
                    originalDisplayName = "existing-host.txt", managedRelativePath = existingRel,
                    sourceSha256 = existingHash, sourceByteSize = existingBytes.size.toLong(),
                    addedAt = 1L, createdAt = 1L, updatedAt = 2L, currentRevision = existingRev,
                    encodingId = "utf-8", normalizationVersion = 1, availability = "READY", activeSessionEpoch = 4L,
                ),
            )
            db.progressDao().insertProgress(
                ReadingProgressEntity(existingId, existingRev, "txt", 1, existingLoc, 40.0, 4L, 2L, 10L),
            )

            val records = ReadingBackup(
                createdAt = 1000L,
                books = listOf(
                    backupBook(book1Hash, book1Bytes.size.toLong(), rev1, "Asset Book One", "asset1.txt", 12L),
                    backupBook(book2Hash, book2Bytes.size.toLong(), rev2, "Asset Book Two", "asset2.txt", 24L),
                ),
                shelves = emptyList(),
                readerSettings = null,
                librarySettings = null,
            )
            val entry1 = BackupAssetEntry(BackupAssetKind.BOOK, "books/$book1Hash.bin", book1Hash, book1Bytes.size.toLong(), "TXT")
            val entry2 = BackupAssetEntry(BackupAssetKind.BOOK, "books/$book2Hash.bin", book2Hash, book2Bytes.size.toLong(), "TXT")
            val entryFont = BackupAssetEntry(BackupAssetKind.FONT, "fonts/$fontId.bin", fontId, fontBytes.size.toLong(), null, "HostNanum.ttf")
            val bundle = BackupBundleV2(records, listOf(entry1, entry2, entryFont))
            val rawV2 = ByteArrayOutputStream().also { out ->
                ReadingBackupFile.writeBundleV2(
                    bundle = bundle,
                    sources = listOf(
                        ReadingBackupFile.AssetSource(entry1) { book1Bytes.inputStream() },
                        ReadingBackupFile.AssetSource(entry2) { book2Bytes.inputStream() },
                        ReadingBackupFile.AssetSource(entryFont) { fontBytes.inputStream() },
                    ),
                    output = out,
                )
            }.toByteArray()

            val storage = AndroidBackupAssetStorage(target)
            val sessionId = UUID.randomUUID().toString()
            val decoded = ReadingBackupFile.decodeBundle(
                input = rawV2.inputStream(),
                stageDirProvider = { storage.createStagingSession(sessionId) },
                reserveCheck = storage::ensureReserve,
            ) as ReadingBackupFile.DecodedBackup.V2
            for (staged in decoded.staged) {
                storage.validateStagedAsset(sessionId, staged, decoded.bundle.records)
            }
            storage.syncStagingSession(sessionId)

            val readingBackup = RoomReadingBackup(db, { UUID.randomUUID().toString() }, System::currentTimeMillis)
            val prepared = readingBackup.preview(decoded.bundle.records)
            val coordinator = BackupAssetRestoreCoordinator(
                database = db,
                storage = storage,
                readingBackup = readingBackup,
                newId = { UUID.randomUUID().toString() },
                now = System::currentTimeMillis,
            ) { checkpoint, detail ->
                if (checkpoint == BackupCommitCheckpoint.AVAILABILITY_UPDATED && detail == "book:$book1Hash") {
                    assertTrue(existingFile.isFile)
                    val props = Properties().apply {
                        setProperty("existingId", existingId)
                        setProperty("existingHash", existingHash)
                        setProperty("book1Hash", book1Hash)
                        setProperty("book2Hash", book2Hash)
                        setProperty("book1Bytes", book1Bytes.size.toString())
                        setProperty("book2Bytes", book2Bytes.size.toString())
                        setProperty("fontId", fontId)
                        setProperty("hadFontBefore", hadFontBefore.toString())
                        setProperty("initialIds", initialIds.joinToString(","))
                        setProperty("ready", "true")
                    }
                    FileOutputStream(proof).use { out ->
                        props.store(out, "Generated backup asset host checkpoint")
                        out.fd.sync()
                    }
                    // External host script observes durable checkpoint and runs adb shell am force-stop.
                    android.os.SystemClock.sleep(90_000)
                    fail("Host did not force-stop the active backup asset commit")
                }
            }
            coordinator.commit(prepared, BackupRestoreMode.NEW_BOOKS_ONLY, sessionId, decoded.staged)
        }
        fail("Preparation finished without being force-stopped by host")
    }

    @Test fun coldStartResumesInterruptedAssetRestoreAndPreservesLibrary() {
        val props = Properties().apply { proof.inputStream().use(::load) }
        val existingId = props.getProperty("existingId")
        val existingHash = props.getProperty("existingHash")
        val book1Hash = props.getProperty("book1Hash")
        val book2Hash = props.getProperty("book2Hash")
        val book1Size = props.getProperty("book1Bytes").toLong()
        val book2Size = props.getProperty("book2Bytes").toLong()
        val fontId = props.getProperty("fontId")
        val hadFontBefore = props.getProperty("hadFontBefore").toBoolean()
        val initialIds = props.getProperty("initialIds").split(',').filter { it.isNotEmpty() }.toSet()

        // Cold start must open Library (never Reader) and automatically resume interrupted asset restore.
        compose.waitUntil(30_000) {
            runBlocking(Dispatchers.IO) { !AndroidReadingBackup(target).hasPendingRestore() } &&
                state().library is org.bookreader.mobile.repository.LibraryState.Content
        }
        compose.onNodeWithTag("tab_LIBRARY").assertIsDisplayed()
        compose.onNodeWithTag("txt_reader").assertDoesNotExist()
        assertTrue(state().notice.orEmpty().contains("중단된 백업 복원"))

        val fontStore = UserFontStore(target)
        runBlocking(Dispatchers.IO) {
            // Existing book and its reading progress + managed bytes must be 100% untouched.
            val existing = checkNotNull(db.bookDao().findBook(existingId))
            assertEquals("READY", existing.availability)
            assertEquals(existingHash, sha256(File(target.filesDir, "managed/${existing.managedRelativePath}").readBytes()))
            val existingProgress = checkNotNull(db.progressDao().findProgress(existingId, checkNotNull(existing.currentRevision)))
            assertEquals(4L, existingProgress.writerSessionEpoch)
            assertTrue(existingProgress.locatorJson.contains("33"))

            // Both restored books must be READY with verified managed files and preserved progress.
            val restored1 = checkNotNull(db.importDao().findDuplicate(book1Hash, book1Size))
            val restored2 = checkNotNull(db.importDao().findDuplicate(book2Hash, book2Size))
            assertEquals("READY", restored1.availability)
            assertEquals("READY", restored2.availability)
            assertEquals(book1Hash, sha256(File(target.filesDir, "managed/${restored1.managedRelativePath}").readBytes()))
            assertEquals(book2Hash, sha256(File(target.filesDir, "managed/${restored2.managedRelativePath}").readBytes()))
            assertNotNull(db.progressDao().findProgress(restored1.id, checkNotNull(restored1.currentRevision)))
            assertNotNull(db.progressDao().findProgress(restored2.id, checkNotNull(restored2.currentRevision)))

            // User font must be registered with exact bytes; staging and journal must be clean.
            val restoredFont = checkNotNull(fontStore.find(fontId))
            val expectedFontBytes = target.assets.open("reader-fonts/NanumGothic-Regular.ttf").use { it.readBytes() }
            assertArrayEquals(expectedFontBytes, restoredFont.file.readBytes())
            val storage = AndroidBackupAssetStorage(target)
            assertFalse(AndroidReadingBackup(target).hasPendingRestore())
            assertTrue(storage.listStagingSessions().isEmpty())
        }

        // Clean up only test-created records/files/font.
        compose.activityRule.scenario.close()
        runBlocking(Dispatchers.IO) {
            val management = AndroidBookManagement(target)
            db.bookDao().listBooks().filter { it.id !in initialIds }.forEach { management.delete(it.id) }
            if (!hadFontBefore && fontStore.find(fontId) != null) {
                fontStore.remove(fontId)
            }
        }
        assertTrue(proof.delete())
    }

    private fun backupBook(hash: String, size: Long, revision: String, title: String, name: String, offset: Long): BackupBook {
        val loc = LocatorCodec().encode(TxtLocator(revision, TxtLocatorPayload(offset)))
        return BackupBook(
            sha256 = hash, byteSize = size, format = "TXT", title = title, displayName = name,
            author = null, revision = revision, encoding = "utf-8", normalizationVersion = 1,
            addedAt = 100L, lastReadAt = 101L, details = null, preferences = null, shelves = emptyList(),
            progress = listOf(BackupProgress(BackupLocation(revision, "txt", 1, loc), 15.0, 102L)),
            bookmarks = emptyList(), txtHighlights = emptyList(), epubHighlights = emptyList(), tocEdits = emptyList(),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
