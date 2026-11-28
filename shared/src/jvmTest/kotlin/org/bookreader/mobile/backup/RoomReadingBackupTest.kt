package org.bookreader.mobile.backup

import androidx.room.execSQL
import androidx.room.useWriterConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.annotation.EpubAnchor
import org.bookreader.mobile.annotation.EpubAnnotations
import org.bookreader.mobile.database.*
import org.bookreader.mobile.importing.archiveContentRevision
import org.bookreader.mobile.importing.txtContentRevision
import org.bookreader.mobile.locator.*
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.RoomProgressRepository
import org.bookreader.mobile.settings.ReaderSettings
import org.bookreader.mobile.settings.ReaderSettingsCodec
import kotlin.test.*

class RoomReadingBackupTest {
    @Test fun snapshotAndColdRestoreKeepEveryRecordKindAndHistoricalTxtRevision() = fixture { dir, source, target ->
        val txt = seedTxt(source)
        val epub = book("epub", BookFormat.EPUB, "b".repeat(64))
        val comic = book("comic", BookFormat.COMIC, "c".repeat(64))
        source.bookDao().insertBook(epub)
        source.bookDao().insertBook(comic)
        val epubRevision = requireNotNull(epub.currentRevision)
        val locator = JsonFixture.sdkLocator()
        val epubLocation = EpubLocator(epubRevision, EpubLocatorPayload(locator = locator))
        source.progressDao().insertProgress(progress(epub.id, epubLocation, "epub"))
        source.progressDao().insertProgress(progress(comic.id, ComicLocator(requireNotNull(comic.currentRevision),
            ComicLocatorPayload("images/02.png", imageIndexHint = 1)), "comic"))
        val anchor = EpubAnchor("OPS/one.xhtml", "body > :nth-child(2)", 0, 4, "same", "", " tail")
        source.epubAnnotationDao().save(EpubAnnotationEntity(epub.id, epubRevision, "highlight-epub", 1,
            EpubAnnotations.json.encodeToString(EpubAnchor.serializer(), anchor),
            """{"href":"OPS/one.xhtml","locations":{"cssSelector":"body > :nth-child(2)"},"text":{"highlight":"same","after":" tail"}}""",
            "BLUE", "메모 😀", "revision-epub"))
        val historic = txtContentRevision(txt.sourceSha256, "cp949", 1)
        source.progressDao().insertProgress(progress(txt.id, TxtLocator(historic, TxtLocatorPayload(19)), "txt"))
        source.libraryDao().insertShelf(ShelfEntity("original-shelf", "읽는 중", "읽는 중"))
        source.libraryDao().insertMembership(ShelfMembershipEntity(txt.id, "original-shelf"))
        source.libraryDao().savePreferences(LibraryPreferencesEntity(sort = "TITLE", shelfId = "original-shelf"))
        source.readerSettingsDao().putGlobal(ReaderGlobalSettingsEntity(schemaVersion = 1,
            settingsJson = ReaderSettingsCodec.write(ReaderSettings(fontSizeSp = 21f))))
        source.readerSettingsDao().putBook(BookReaderPreferencesEntity(txt.id, 1, """{"readingMode":"PAGES"}"""))

        val snapshot = service(source).snapshot()
        val overview = service(target).preview(snapshot).overview
        assertEquals(BackupOverview(100, 3, 0, 0, 1, 4, 1, 2, 1, true), overview)
        val result = service(target).restore(service(target).preview(snapshot), BackupRestoreMode.NEW_BOOKS_ONLY)
        assertEquals(3, result.newBooks)
        assertEquals(1, result.addedShelves)
        target.close()
        useDatabase(dir.resolve("target.db")) { reopened ->
            val records = reopened.bookDao().listBooks()
            assertEquals(setOf("MISSING"), records.map { it.availability }.toSet())
            records.forEach { row ->
                assertNull(row.sourceUri)
                assertNull(row.coverKey)
                assertNull(row.readOrder)
                assertEquals(0L, row.activeSessionEpoch)
                assertEquals("books/${row.id}/original", row.managedRelativePath)
                assertNotEquals(snapshot.books.first { it.sha256 == row.sourceSha256 }.displayName, row.managedRelativePath)
            }
            val restored = service(reopened).snapshot()
            assertEquals(snapshot.books.associateBy { it.sha256 }, restored.books.associateBy { it.sha256 })
            assertEquals(snapshot.librarySettings, restored.librarySettings)
            assertEquals(snapshot.readerSettings, restored.readerSettings)
            val nextTxt = records.single { it.format == "TXT" }
            assertNotEquals(txt.id, nextTxt.id)
            val saved = assertIs<ProgressReadResult.Found>(RoomProgressRepository(reopened).loadProgress(nextTxt.id, historic)).progress
            assertEquals(TxtLocator(historic, TxtLocatorPayload(19)), saved.locator)
            assertEquals(0L, saved.writerSessionEpoch)
            assertEquals(0L, saved.writerSequence)
        }
        assertEquals(txt, source.bookDao().findBook(txt.id))
    }

    @Test fun duplicateModesKeepExistingBytesAndMergeOnlyMissingKeys() = fixture { _, source, target ->
        val origin = seedTxt(source)
        val revision = requireNotNull(origin.currentRevision)
        val local = origin.copy(id = "local", managedRelativePath = "books/local/original",
            title = "독자가 바꾼 제목", sourceUri = "content://local/durable-uri", activeSessionEpoch = 47,
            currentRevision = txtContentRevision(origin.sourceSha256, "cp949", 1), encodingId = "cp949", coverKey = "local-cover")
        target.bookDao().insertBook(local)
        val localProgress = progress(local.id, TxtLocator(revision, TxtLocatorPayload(41)), "txt").copy(writerSessionEpoch = 47)
        target.progressDao().insertProgress(localProgress)
        val localAnnotation = source.txtAnnotationDao().entries(origin.id, revision).single()
            .copy(bookId = local.id, note = "대상에서 바꾼 메모", changeId = "local-version")
        target.txtAnnotationDao().save(localAnnotation)
        val localSettings = ReaderGlobalSettingsEntity(schemaVersion = 1,
            settingsJson = ReaderSettingsCodec.write(ReaderSettings(fontSizeSp = 24f)))
        target.readerSettingsDao().putGlobal(localSettings)
        source.readerSettingsDao().putGlobal(localSettings.copy(settingsJson = ReaderSettingsCodec.write(ReaderSettings(fontSizeSp = 20f))))
        val snapshot = service(source).snapshot()
        val restore = service(target)
        assertEquals(1, restore.preview(snapshot).overview.matchedBooks)
        assertEquals(BackupRestoreResult(0, 0, 0, 0), restore.restore(restore.preview(snapshot), BackupRestoreMode.NEW_BOOKS_ONLY))
        assertTrue(target.bookmarkDao().page(local.id, revision, Long.MAX_VALUE, "z").isEmpty())

        val result = restore.restore(restore.preview(snapshot), BackupRestoreMode.ADD_MISSING_RECORDS)
        assertEquals(BackupRestoreResult(0, 1, 0, 2), result)
        assertEquals(local, target.bookDao().findBook(local.id))
        assertEquals(localProgress, target.progressDao().findProgress(local.id, revision))
        assertEquals(localSettings, target.readerSettingsDao().global())
        assertEquals(listOf(localAnnotation), target.txtAnnotationDao().entries(local.id, revision))
        assertEquals("읽던 문장", target.bookmarkDao().page(local.id, revision, Long.MAX_VALUE, "z").single().label)
        assertEquals("수정한 목차", target.txtTocDao().edits(local.id, revision).single().title)
        assertEquals(BackupRestoreResult(0, 0, 0, 0), restore.restore(restore.preview(snapshot), BackupRestoreMode.ADD_MISSING_RECORDS))
    }

    @Test fun abortedInsertRollsBackNewBooksShelvesAndSettings() = fixture { _, source, target ->
        seedTxt(source)
        source.libraryDao().insertShelf(ShelfEntity("shelf", "백업 책장", "백업 책장"))
        source.readerSettingsDao().putGlobal(ReaderGlobalSettingsEntity(schemaVersion = 1, settingsJson = "{}"))
        val keep = book("keep", BookFormat.COMIC, "e".repeat(64))
        target.bookDao().insertBook(keep)
        val keepProgress = progress(keep.id, ComicLocator(requireNotNull(keep.currentRevision), ComicLocatorPayload("page.png")), "comic")
        target.progressDao().insertProgress(keepProgress)
        target.useWriterConnection {
            it.execSQL("CREATE TRIGGER fixture_reject_restore BEFORE INSERT ON reading_progress BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        }
        val service = service(target)
        val preview = service.preview(service(source).snapshot())
        assertFails { service.restore(preview, BackupRestoreMode.ADD_MISSING_RECORDS) }
        assertEquals(listOf(keep), target.bookDao().listBooks())
        assertEquals(keepProgress, target.progressDao().findProgress(keep.id, requireNotNull(keep.currentRevision)))
        assertTrue(target.libraryDao().shelves().isEmpty())
        assertNull(target.readerSettingsDao().global())
    }

    @Test fun stalePreviewRefusesToApplyAndRestoreLimitPreservesLocalGroup() = fixture { _, source, target ->
        val origin = seedTxt(source)
        val snapshot = service(source).snapshot()
        val service = service(target)
        val firstPreview = service.preview(snapshot)
        val local = origin.copy(id = "original-local")
        target.bookDao().insertBook(local)
        assertEquals(BackupProblem.CHANGED_LIBRARY,
            assertFailsWith<BackupFailure> { service.restore(firstPreview, BackupRestoreMode.ADD_MISSING_RECORDS) }.problem)
        val revision = requireNotNull(local.currentRevision)
        target.useWriterConnection {
            it.execSQL("""
                WITH RECURSIVE counter(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM counter WHERE n < 500)
                INSERT INTO txt_annotations SELECT 'original-local', '$revision', 'local-' || n, 1, 0, 3, 'abc', 'BLUE', '', 'local'
                FROM counter
            """.trimIndent())
        }
        val entries = target.txtAnnotationDao().entries(local.id, revision)
        assertEquals(500, entries.size)
        assertEquals(BackupProblem.LIMIT, assertFailsWith<BackupFailure> {
            service.restore(service.preview(snapshot), BackupRestoreMode.ADD_MISSING_RECORDS)
        }.problem)
        assertEquals(entries, target.txtAnnotationDao().entries(local.id, revision))
        assertNull(target.progressDao().findProgress(local.id, revision))
        assertTrue(target.bookmarkDao().page(local.id, revision, Long.MAX_VALUE, "z").isEmpty())
    }

    @Test fun cannotBackUpFutureRecordOrLargeEmbeddedNulRow() = fixture { _, source, _ ->
        val txt = seedTxt(source)
        source.readerSettingsDao().putGlobal(ReaderGlobalSettingsEntity(schemaVersion = 99, settingsJson = "{}"))
        assertFailsWith<IllegalArgumentException> { service(source).snapshot() }
        source.useWriterConnection {
            it.execSQL("UPDATE reader_global_settings SET settingsJson = CAST(zeroblob(8388609) AS TEXT), schemaVersion = 1")
        }
        assertEquals(BackupProblem.LIMIT, assertFailsWith<BackupFailure> { service(source).snapshot() }.problem)
        assertEquals(txt, source.bookDao().findBook(txt.id))
    }

    private suspend fun seedTxt(db: BookReaderDatabase): BookEntity {
        val value = book("source-txt", BookFormat.TXT, "a".repeat(64))
        db.bookDao().insertBook(value)
        val revision = requireNotNull(value.currentRevision)
        val position = TxtLocator(revision, TxtLocatorPayload(17))
        db.progressDao().insertProgress(progress(value.id, position, "txt"))
        db.bookmarkDao().insert(BookmarkEntity("keep-bookmark", value.id, revision, "txt", 1,
            LocatorCodec().encode(position), "읽던 문장", 1, 2))
        db.txtTocDao().save(TxtTocOverrideEntity(value.id, revision, 7, 1, "수정한 목차", 0, "toc-change"))
        db.txtAnnotationDao().save(TxtAnnotationEntity(value.id, revision, "highlight-txt", 1, 7, 11,
            "same", "PINK", "선택한 문장 😀", "txt-change"))
        db.libraryDao().saveDetails(LibraryBookDetailsEntity(value.id, 3, true, "READING", 2))
        return value
    }

    private fun book(id: String, format: BookFormat, hash: String) = BookEntity(
        id, format.name, "저장한 제목 $id", "$id.fixture", "books/$id/original", hash, 160L, 1, 1, 2,
        author = "저자", sourceUri = "content://private/specific-user-document",
        currentRevision = if (format == BookFormat.TXT) txtContentRevision(hash, "utf-8", 1) else archiveContentRevision(format, hash),
        encodingId = if (format == BookFormat.TXT) "utf-8" else null,
        normalizationVersion = if (format == BookFormat.TXT) 1 else null,
        coverKey = "cached-cover", lastReadAt = 10, readOrder = 7, activeSessionEpoch = 8,
    )

    private fun progress(id: String, locator: ContentLocator, type: String) = ReadingProgressEntity(
        id, locator.contentRevision, type, 1, LocatorCodec().encode(locator), 32.0, 8, 2, 30,
    )
    private fun service(db: BookReaderDatabase) = RoomReadingBackup(db, { UUID.randomUUID().toString() }, { 100L })

    private fun fixture(action: suspend (Path, BookReaderDatabase, BookReaderDatabase) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("reading-backup-")
        try {
            useDatabase(dir.resolve("source.db")) { source ->
                useDatabase(dir.resolve("target.db")) { target -> action(dir, source, target) }
            }
        } finally {
            Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private suspend fun useDatabase(path: Path, action: suspend (BookReaderDatabase) -> Unit) {
        val db = createJvmDatabase(path)
        try { action(db) } finally { db.close() }
    }

    private object JsonFixture {
        fun sdkLocator() = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"href":"OPS/one.xhtml","type":"application/xhtml+xml","locations":{"progression":0.33}}"""
        ) as kotlinx.serialization.json.JsonObject
    }
}
