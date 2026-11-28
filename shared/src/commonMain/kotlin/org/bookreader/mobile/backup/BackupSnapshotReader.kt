package org.bookreader.mobile.backup

import androidx.room.PooledConnection
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Reads only the portable projection of Room 9 in a single read transaction. The final column
 * measures actual bytes (including data after a NUL) before any string is copied into the heap.
 * All SQL identifiers and predicates below are app-owned constants.
 */
internal class BackupSnapshotReader(private val connection: PooledConnection) {
    private var remainingBytes = BackupRules.MAX_PAYLOAD_BYTES.toLong()
    private var remainingRows = BackupRules.MAX_RECORDS

    suspend fun read(now: Long): ReadingBackup {
        val shelves = rows("library_shelves", "id, name, nameKey", limit = BackupRules.MAX_SHELVES) {
            val name = it.getText(1)
            require(it.getText(2) == name.lowercase())
            it.getText(0) to name
        }.toMap()
        fun shelf(id: String) = requireNotNull(shelves[id])

        val global = rows("reader_global_settings", "schemaVersion, settingsJson", "id = 'global'", limit = 1) {
            BackupSettings(it.getInt(0), it.getText(1))
        }.singleOrNull()
        val library = rows("library_preferences", "sort, view, filter, shelfId", "id = 'global'", limit = 1) {
            BackupLibrarySettings(it.getText(0), it.getText(1), it.getText(2), it.textOrNull(3)?.let(::shelf))
        }.singleOrNull()
        val books = rows(
            "books", "id, sourceSha256, sourceByteSize, format, title, originalDisplayName, author, " +
                "currentRevision, encodingId, normalizationVersion, addedAt, lastReadAt",
            "availability != 'DELETING'", limit = BackupRules.MAX_BOOKS,
        ) {
            it.getText(0) to BackupBook(it.getText(1), it.getLong(2), it.getText(3), it.getText(4), it.getText(5),
                it.textOrNull(6), it.getText(7), it.textOrNull(8), if (it.isNull(9)) null else it.getInt(9),
                it.getLong(10), it.longOrNull(11), null, null, emptyList(), emptyList(), emptyList(),
                emptyList(), emptyList(), emptyList())
        }
        val details = forBooks("library_book_details", "metadataEdits, readingStatus, coverPreset") {
            BackupBookDetails(it.getInt(1), it.textOrNull(2), it.getInt(3))
        }
        val preferences = forBooks("book_reader_preferences", "schemaVersion, preferencesJson") {
            BackupSettings(it.getInt(1), it.getText(2))
        }
        val memberships = forBooks("library_memberships", "shelfId") { shelf(it.getText(1)) }
        val progress = forBooks("reading_progress", "contentRevision, locatorType, locatorVersion, locatorJson, percent, updatedAt") {
            BackupProgress(it.location(1), if (it.isNull(5)) null else it.getDouble(5), it.getLong(6))
        }
        val bookmarks = forBooks("bookmarks", "id, contentRevision, locatorType, locatorVersion, locatorJson, label, createdAt, updatedAt") {
            BackupBookmark(it.getText(1), it.location(2), it.textOrNull(6), it.getLong(7), it.getLong(8))
        }
        val txtHighlights = forBooks("txt_annotations", "contentRevision, id, schemaVersion, startOffset, endOffset, quote, color, note, changeId") {
            BackupTxtHighlight(it.getText(1), it.getText(2), it.getInt(3), it.getLong(4), it.getLong(5),
                it.getText(6), it.getText(7), it.getText(8), it.getText(9))
        }
        val epubHighlights = forBooks("epub_annotations", "contentRevision, id, schemaVersion, anchorJson, locatorJson, color, note, changeId") {
            BackupEpubHighlight(it.getText(1), it.getText(2), it.getInt(3), it.getText(4), it.getText(5),
                it.getText(6), it.getText(7), it.getText(8))
        }
        val toc = forBooks("txt_toc_overrides", "contentRevision, offset, schemaVersion, title, excluded, changeId") {
            val excluded = it.getInt(5)
            require(excluded in 0..1)
            BackupTocEdit(it.getText(1), it.getLong(2), it.getInt(3), it.textOrNull(4), excluded == 1, it.getText(6))
        }
        return ReadingBackup(now, books.map { (id, book) ->
            book.copy(details = details[id]?.single(), preferences = preferences[id]?.single(), shelves = memberships[id].orEmpty(),
                progress = progress[id].orEmpty(), bookmarks = bookmarks[id].orEmpty(), txtHighlights = txtHighlights[id].orEmpty(),
                epubHighlights = epubHighlights[id].orEmpty(), tocEdits = toc[id].orEmpty())
        }, shelves.values.sorted(), global, library)
    }

    private suspend fun <T> forBooks(table: String, fields: String, mapper: (SQLiteStatement) -> T): Map<String, List<T>> =
        rows(table, "bookId, " + fields, "bookId IN (SELECT id FROM books WHERE availability != 'DELETING')") {
            it.getText(0) to mapper(it)
        }.groupBy({ it.first }, { it.second })

    private suspend fun <T> rows(
        table: String, fields: String, predicate: String = "1", limit: Int = BackupRules.MAX_RECORDS,
        mapper: (SQLiteStatement) -> T,
    ): List<T> {
        val request = currentCoroutineContext()
        val columns = fields.split(", ")
        val byteCosts = columns.joinToString(" + ") { "COALESCE(length(CAST($it AS BLOB)), 0)" }
        return connection.usePrepared("SELECT $fields, ($byteCosts) FROM $table WHERE $predicate LIMIT ?") { statement ->
            statement.bindInt(1, minOf(limit, remainingRows) + 1)
            buildList {
                while (statement.step()) {
                    request.ensureActive()
                    val bytes = statement.getLong(columns.size)
                    BackupRules.capacity(size < limit && remainingRows > 0 && bytes in 0..remainingBytes)
                    remainingRows--
                    remainingBytes -= bytes
                    add(mapper(statement))
                }
            }
        }
    }

    private fun SQLiteStatement.textOrNull(column: Int) = if (isNull(column)) null else getText(column)
    private fun SQLiteStatement.longOrNull(column: Int) = if (isNull(column)) null else getLong(column)
    private fun SQLiteStatement.location(column: Int) =
        BackupLocation(getText(column), getText(column + 1), getInt(column + 2), getText(column + 3))
}
