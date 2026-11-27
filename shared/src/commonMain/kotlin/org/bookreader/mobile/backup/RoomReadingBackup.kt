package org.bookreader.mobile.backup

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.annotation.TxtAnnotations
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.BookReaderPreferencesEntity
import org.bookreader.mobile.database.BookmarkEntity
import org.bookreader.mobile.database.EpubAnnotationEntity
import org.bookreader.mobile.database.LibraryBookDetailsEntity
import org.bookreader.mobile.database.LibraryPreferencesEntity
import org.bookreader.mobile.database.ReaderGlobalSettingsEntity
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.ShelfEntity
import org.bookreader.mobile.database.ShelfMembershipEntity
import org.bookreader.mobile.database.TxtAnnotationEntity
import org.bookreader.mobile.database.TxtTocOverrideEntity
import org.bookreader.mobile.importing.withManagedBookOperations
import org.bookreader.mobile.toc.TxtToc

enum class BackupRestoreMode { NEW_BOOKS_ONLY, ADD_MISSING_RECORDS }

data class BackupOverview(
    val createdAt: Long,
    val newBooks: Int,
    val matchedBooks: Int,
    val unavailableMatches: Int,
    val shelves: Int,
    val progress: Int,
    val bookmarks: Int,
    val highlights: Int,
    val tocEdits: Int,
    val hasSettings: Boolean,
)

data class BackupRestoreResult(val newBooks: Int, val existingBooksWithAdditions: Int, val addedShelves: Int, val addedRecords: Int)

internal data class BackupTarget(val id: String, val format: String, val revision: String?, val availability: String) {
    fun isCompatible(book: BackupBook) = format == book.format && availability != "DELETING"
}

class PreparedReadingBackup internal constructor(
    val overview: BackupOverview,
    internal val snapshot: ReadingBackup,
    internal val targets: List<BackupTarget?>,
)

/** The entire merge is one SQLite transaction. It never deletes rows or opens managed files. */
class RoomReadingBackup(
    private val database: BookReaderDatabase,
    private val newId: () -> String,
    private val now: () -> Long,
) {
    suspend fun snapshot(): ReadingBackup {
        val snapshot = database.useReaderConnection { connection ->
            connection.deferredTransaction { BackupSnapshotReader(this).read(now()) }
        }
        BackupRules.validate(snapshot)
        return snapshot
    }

    suspend fun preview(snapshot: ReadingBackup): PreparedReadingBackup {
        BackupRules.validate(snapshot)
        val matched = database.useReaderConnection { connection -> connection.deferredTransaction { targets(snapshot) } }
        val compatible = snapshot.books.zip(matched).count { (book, target) -> target?.isCompatible(book) == true }
        val new = matched.count { it == null }
        return PreparedReadingBackup(BackupOverview(snapshot.createdAt, new, compatible, matched.size - new - compatible,
            snapshot.shelves.size, snapshot.books.sumOf { it.progress.size }, snapshot.books.sumOf { it.bookmarks.size },
            snapshot.books.sumOf { it.txtHighlights.size + it.epubHighlights.size }, snapshot.books.sumOf { it.tocEdits.size },
            snapshot.readerSettings != null || snapshot.librarySettings != null || snapshot.books.any { it.preferences != null }),
            snapshot, matched)
    }

    suspend fun restore(prepared: PreparedReadingBackup, mode: BackupRestoreMode): BackupRestoreResult = withManagedBookOperations {
        restoreLocked(prepared, mode, emptyMap())
    }

    internal suspend fun restoreLocked(
        prepared: PreparedReadingBackup,
        mode: BackupRestoreMode,
        plannedBookIds: Map<String, String>,
    ): BackupRestoreResult {
        val request = currentCoroutineContext()
        request.ensureActive()
        return database.useWriterConnection { connection -> connection.immediateTransaction {
            val snapshot = prepared.snapshot
            val sameTargets = targets(snapshot)
            if (sameTargets != prepared.targets) throw BackupFailure(BackupProblem.CHANGED_LIBRARY)
            val library = database.libraryDao()
            val shelves = library.shelves().associateBy { it.nameKey }.toMutableMap()
            var addedShelves = 0
            for (name in snapshot.shelves) {
                request.ensureActive()
                val key = name.lowercase()
                if (key in shelves) continue
                BackupRules.capacity(shelves.size < BackupRules.MAX_SHELVES)
                val shelf = ShelfEntity(checkedId(), name, key)
                library.insertShelf(shelf)
                shelves[key] = shelf
                addedShelves++
            }
            var newBooks = 0
            var updatedBooks = 0
            var addedRecords = restoreGlobalSettings(snapshot, shelves)
            for ((book, target) in snapshot.books.zip(sameTargets)) {
                request.ensureActive()
                if (target != null && (!target.isCompatible(book) || mode == BackupRestoreMode.NEW_BOOKS_ONLY)) continue
                val id = target?.id ?: plannedOrNewId(plannedBookIds, book).also { id ->
                    database.bookDao().insertBook(book.asMissingBook(id, now()))
                    newBooks++
                }
                val added = restoreBook(book, id, target == null, shelves)
                addedRecords += added
                if (target != null && added > 0) updatedBooks++
            }
            request.ensureActive()
            BackupRestoreResult(newBooks, updatedBooks, addedShelves, addedRecords)
        } }
    }

    private fun plannedOrNewId(plannedBookIds: Map<String, String>, book: BackupBook): String {
        val planned = plannedBookIds[BackupAssetRules.bookKey(book.sha256, book.byteSize)]
        if (planned != null) {
            require(bookIdPattern.matches(planned))
            return planned
        }
        return checkedId()
    }

    private suspend fun targets(snapshot: ReadingBackup): List<BackupTarget?> {
        val request = currentCoroutineContext()
        return snapshot.books.map { book ->
            request.ensureActive()
            database.importDao().findDuplicate(book.sha256, book.byteSize)?.let {
                BackupTarget(it.id, it.format, it.currentRevision, it.availability)
            }
        }
    }

    private suspend fun restoreGlobalSettings(snapshot: ReadingBackup, shelves: Map<String, ShelfEntity>): Int {
        var count = 0
        val reader = database.readerSettingsDao()
        val library = database.libraryDao()
        snapshot.readerSettings?.let {
            if (reader.global() == null) {
                reader.putGlobal(ReaderGlobalSettingsEntity(schemaVersion = it.version, settingsJson = it.json))
                count++
            }
        }
        snapshot.librarySettings?.let {
            if (library.preferences() == null) {
                library.savePreferences(LibraryPreferencesEntity(sort = it.sort, view = it.view, filter = it.filter,
                    shelfId = it.shelf?.let { name -> shelves.getValue(name.lowercase()).id }))
                count++
            }
        }
        return count
    }

    private suspend fun restoreBook(book: BackupBook, id: String, newBook: Boolean, shelves: Map<String, ShelfEntity>): Int {
        val request = currentCoroutineContext()
        val dao = database.backupInsertDao()
        val library = database.libraryDao()
        val settings = database.readerSettingsDao()
        var count = 0
        if (newBook) book.details?.let {
            // Auto artwork can be regenerated after the owner relinks the original.
            library.saveDetails(LibraryBookDetailsEntity(id, it.metadataEdits, false, it.readingStatus, it.coverPreset))
            count++
        }
        book.preferences?.let {
            if (settings.book(id) == null) {
                settings.putBook(BookReaderPreferencesEntity(id, it.version, it.json))
                count++
            }
        }
        for (name in book.shelves) {
            request.ensureActive()
            if (dao.membership(ShelfMembershipEntity(id, shelves.getValue(name.lowercase()).id)) != -1L) count++
        }
        if (book.shelves.isNotEmpty()) BackupRules.capacity(library.membershipsFor(id).size <= BackupRules.MAX_MEMBERSHIPS)
        for (entry in book.progress) {
            request.ensureActive()
            val location = entry.location
            val progress = database.progressDao()
            if (progress.findProgress(id, location.revision) == null) {
                // Writer identity belongs to this device. The next ready session uses its own epoch.
                progress.insertProgress(ReadingProgressEntity(id, location.revision, location.type, location.version,
                    location.json, entry.percent, 0, 0, entry.updatedAt))
                count++
            }
        }
        for (entry in book.bookmarks) {
            request.ensureActive()
            val location = entry.location
            if (dao.bookmark(BookmarkEntity(entry.id, id, location.revision, location.type, location.version,
                    location.json, entry.label, entry.createdAt, entry.updatedAt)) != -1L) count++
        }
        for (entry in book.txtHighlights) {
            request.ensureActive()
            if (dao.txtHighlight(TxtAnnotationEntity(id, entry.revision, entry.id, entry.version, entry.start, entry.end,
                    entry.quote, entry.color, entry.note, entry.changeId)) != -1L) count++
        }
        for (entry in book.epubHighlights) {
            request.ensureActive()
            if (dao.epubHighlight(EpubAnnotationEntity(id, entry.revision, entry.id, entry.version, entry.anchorJson,
                    entry.locatorJson, entry.color, entry.note, entry.changeId)) != -1L) count++
        }
        for (entry in book.tocEdits) {
            request.ensureActive()
            if (dao.toc(TxtTocOverrideEntity(id, entry.revision, entry.offset, entry.version, entry.title,
                    if (entry.excluded) 1 else 0, entry.changeId)) != -1L) count++
        }
        for (revision in book.txtHighlights.map { it.revision }.toSet())
            BackupRules.capacity(dao.txtHighlights(id, revision) <= TxtAnnotations.MAX_ENTRIES)
        for (revision in book.epubHighlights.map { it.revision }.toSet())
            BackupRules.capacity(dao.epubHighlights(id, revision) <= TxtAnnotations.MAX_ENTRIES)
        for (revision in book.tocEdits.map { it.revision }.toSet())
            BackupRules.capacity(dao.tocEdits(id, revision) <= TxtToc.MAX_EDITS)
        return count
    }

    private fun BackupBook.asMissingBook(id: String, now: Long) = BookEntity(
        id = id, format = format, title = title, originalDisplayName = displayName,
        managedRelativePath = "books/$id/original", sourceSha256 = sha256, sourceByteSize = byteSize,
        addedAt = addedAt, createdAt = addedAt, updatedAt = now, author = author, currentRevision = revision,
        encodingId = encoding, normalizationVersion = normalizationVersion, availability = "MISSING", lastReadAt = lastReadAt,
    )

    private fun checkedId(): String = newId().also { require(bookIdPattern.matches(it)) }

    private companion object {
        val bookIdPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
