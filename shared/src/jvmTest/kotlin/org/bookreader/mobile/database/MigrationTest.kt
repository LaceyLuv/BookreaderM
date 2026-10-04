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
    @Test fun exportedVersionOneMigratesWithoutLosingBooksOrProgress() = runBlocking {
        val directory = Files.createTempDirectory("bookreader-migration-")
        val path = directory.resolve("db.sqlite")
        val schemaPath = listOf(
            Path.of("schemas/org.bookreader.mobile.database.BookReaderDatabase/1.json"),
            Path.of("shared/schemas/org.bookreader.mobile.database.BookReaderDatabase/1.json"),
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
                    for (index in e.getValue("indices").jsonArray) connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
                for (query in schema.getValue("setupQueries").jsonArray) connection.execSQL(query.jsonPrimitive.content)
                connection.execSQL("PRAGMA user_version = 1")
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
            val migrated = createJvmDatabase(path)
            try {
                assertEquals(book, migrated.bookDao().findBook(book.id))
                assertEquals(progress, migrated.progressDao().findProgress(book.id, progress.contentRevision))
                assertTrue(migrated.importDao().listJobs().isEmpty())
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
