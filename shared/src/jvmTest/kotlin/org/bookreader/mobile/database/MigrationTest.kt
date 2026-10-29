package org.bookreader.mobile.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.repository.ReadySessionResult
import org.bookreader.mobile.repository.RoomProgressWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrationTest {
    @Test fun exportedVersionOneMigratesWithoutLosingBooksOrProgress() = migration(1)
    @Test fun exportedVersionTwoMigratesWithoutLosingBooksOrProgress() = migration(2)
    @Test fun exportedVersionThreePreservesSettingsBooksAndProgress() = migration(3)

    @Test fun exportedVersionFourPreservesRawBookmarksAndDefaultsExistingJournalsToTxt() = migration(4)
    @Test fun exportedVersionFiveAddsCatalogWithoutRewritingAnyReadingData() = migration(5)
    @Test fun catalogMigrationStatementFailurePreservesVersionFiveDatabase() = migration(5, "statement")
    @Test fun catalogMigrationValidationFailureRollsBackEveryCatalogTable() = migration(5, "validation")
    @Test fun migrationSqlFailurePreservesExistingDatabaseAcrossTwoOpenAttempts() = migration(4, "statement")
    @Test fun migrationValidationFailureRollsBackAddedColumnWithoutResettingRecords() = migration(4, "validation")

    private fun migration(version: Int, failure: String? = null) = runBlocking {
        val directory = Files.createTempDirectory("bookreader-migration-")
        val path = directory.resolve("db.sqlite")
        val schemaPath = listOf(
            Path.of("schemas/org.bookreader.mobile.database.BookReaderDatabase/$version.json"),
            Path.of("shared/schemas/org.bookreader.mobile.database.BookReaderDatabase/$version.json"),
        ).first { Files.isRegularFile(it) }
        val schema = Json.parseToJsonElement(Files.readString(schemaPath)).jsonObject.getValue("database").jsonObject
        val book = BookEntity(
            "old-book", "TXT", "Original title", "fixture.txt", "books/old-book/original", "a".repeat(64), 123,
            5, 6, 7, currentRevision = "old-revision", encodingId = "cp949", normalizationVersion = 1,
            lastOpenedAt = 13, lastReadAt = 15, readOrder = 17, activeSessionEpoch = 9,
        )
        val locator = TxtLocator("old-revision", TxtLocatorPayload(71))
        val progress = ReadingProgressEntity("old-book", "old-revision", "txt", 1, LocatorCodec().encode(locator), 24.0, 9, 12, 19)
        try {
            // Recreate the exact exported v1 schema, including identity hash and index definitions.
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                for (entity in schema.getValue("entities").jsonArray) {
                    val e = entity.jsonObject
                    val table = e.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                    for (index in e["indices"]?.jsonArray.orEmpty()) connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
                for (query in schema.getValue("setupQueries").jsonArray) connection.execSQL(query.jsonPrimitive.content)
                connection.execSQL("PRAGMA user_version = $version")
                if (version >= 2) connection.execSQL("INSERT INTO app_metadata (`key`, value) VALUES ('read_order', 17)")
                if (version >= 3) connection.execSQL("INSERT INTO reader_global_settings (id,schemaVersion,settingsJson) VALUES ('global',2,'{future}')")
                connection.prepare("INSERT INTO books (id,format,title,originalDisplayName,managedRelativePath,sourceSha256,sourceByteSize,addedAt,createdAt,updatedAt,currentRevision,encodingId,normalizationVersion,availability,lastOpenedAt,lastReadAt,readOrder,activeSessionEpoch) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)").use { insert ->
                    listOf(book.id, book.format, book.title, book.originalDisplayName, book.managedRelativePath, book.sourceSha256).forEachIndexed { i, value -> insert.bindText(i + 1, value) }
                    listOf(book.sourceByteSize, book.addedAt, book.createdAt, book.updatedAt).forEachIndexed { i, value -> insert.bindLong(i + 7, value) }
                    insert.bindText(11, book.currentRevision!!)
                    insert.bindText(12, book.encodingId!!)
                    insert.bindLong(13, 1)
                    insert.bindText(14, "READY")
                    insert.bindLong(15, 13)
                    insert.bindLong(16, 15)
                    insert.bindLong(17, 17)
                    insert.bindLong(18, 9)
                    insert.step()
                }
                connection.prepare("INSERT INTO reading_progress (bookId,contentRevision,locatorType,locatorVersion,locatorJson,percent,writerSessionEpoch,writerSequence,updatedAt) VALUES (?,?,?,?,?,?,?,?,?)").use { insert ->
                    insert.bindText(1, progress.bookId)
                    insert.bindText(2, progress.contentRevision)
                    insert.bindText(3, progress.locatorType)
                    insert.bindLong(4, 1)
                    insert.bindText(5, progress.locatorJson)
                    insert.bindDouble(6, 24.0)
                    insert.bindLong(7, 9)
                    insert.bindLong(8, 12)
                    insert.bindLong(9, 19)
                    insert.step()
                }
            }
            if (version >= 4) BundledSQLiteDriver().open(path.toString()).use { connection ->
                connection.execSQL("INSERT INTO import_jobs (id,proposedBookId,stagingPath,finalRelativePath,state,displayName,createdAt,updatedAt,bytesCopied) VALUES ('old-job','pending','imports/old-job.part','books/pending/original','COPYING','original.txt',41,42,43)")
                connection.prepare("INSERT INTO bookmarks (id,bookId,contentRevision,locatorType,locatorVersion,locatorJson,label,createdAt,updatedAt) VALUES ('raw-bookmark',?,?,'future',9,'{untouched}','label',51,52)").use {
                    it.bindText(1,book.id);it.bindText(2,progress.contentRevision);it.step()
                }
            }
            if (failure != null) {
                BundledSQLiteDriver().open(path.toString()).use { connection ->
                    connection.execSQL(if (failure == "statement")
                        if (version == 5) "CREATE TABLE library_shelves (id TEXT NOT NULL PRIMARY KEY)"
                        else "ALTER TABLE import_jobs ADD COLUMN format TEXT NOT NULL DEFAULT 'UNKNOWN'"
                        else "ALTER TABLE books ADD COLUMN futureData TEXT")
                }
                fun rawSnapshot(): List<List<String?>> = BundledSQLiteDriver().open(path.toString()).use { connection ->
                    buildList {
                        val queries = listOf("PRAGMA user_version", "SELECT name,sql FROM sqlite_master ORDER BY name") +
                            listOf("books", "reading_progress", "bookmarks", "import_jobs", "app_metadata",
                                "reader_global_settings", "book_reader_preferences", "room_master_table").map { "SELECT * FROM $it ORDER BY 1" }
                        for (query in queries) connection.prepare(query).use { statement ->
                            while (statement.step()) add(List(statement.getColumnCount()) {
                                if (statement.isNull(it)) null else statement.getText(it)
                            })
                        }
                    }
                }
                val before = rawSnapshot()
                repeat(2) {
                    val broken = createJvmDatabase(path)
                    try {
                        assertIs<org.bookreader.mobile.repository.LibraryState.Error>(
                            org.bookreader.mobile.repository.RoomBookRepository(broken).loadBooks())
                    } finally { broken.close() }
                    assertEquals(before, rawSnapshot(), "Failed migration must preserve schema version and every raw row")
                }
                return@runBlocking
            }
            val migrated = createJvmDatabase(path)
            try {
                assertEquals(book, migrated.bookDao().findBook(book.id))
                assertEquals(progress, migrated.progressDao().findProgress(book.id, progress.contentRevision))
                if (version < 4) assertTrue(migrated.importDao().listJobs().isEmpty()) else {
                    assertEquals(ImportJobEntity("old-job","pending","imports/old-job.part","books/pending/original","COPYING","original.txt",41,42,bytesCopied=43), migrated.importDao().listJobs().single())
                }
                assertEquals(ReaderSettingsRows(if (version >= 3) ReaderGlobalSettingsEntity(schemaVersion = 2, settingsJson = "{future}") else null, null), migrated.readerSettingsDao().read(book.id))
                val bookmarks = migrated.bookmarkDao().page(book.id, progress.contentRevision, Long.MAX_VALUE, "z")
                if (version < 4) assertTrue(bookmarks.isEmpty()) else assertEquals(
                    BookmarkEntity("raw-bookmark", book.id, progress.contentRevision, "future", 9, "{untouched}", "label", 51, 52), bookmarks.single())
                assertEquals(17L, migrated.progressWriterDao().readOrder())
                val ready = assertIs<ReadySessionResult.Ready>(RoomProgressWriter(migrated) { 30 }.startReadySession(book.id, progress.contentRevision, locator))
                assertEquals(10L, ready.session.sessionEpoch)
                assertEquals(18L, migrated.bookDao().findBook(book.id)!!.readOrder)
                assertEquals(progress, migrated.progressDao().findProgress(book.id, progress.contentRevision))
            } finally { migrated.close() }
            val reopened = createJvmDatabase(path)
            try { assertEquals(progress, reopened.progressDao().findProgress(book.id, progress.contentRevision)) }
            finally { reopened.close() }
        } finally {
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
