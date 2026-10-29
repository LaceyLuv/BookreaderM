package org.bookreader.mobile.library

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.database.*

/** Additive catalog data; failures roll back without resetting books or reading records. */
class RoomLibraryManager(private val database: BookReaderDatabase, private val now: () -> Long) {
    private val dao get() = database.libraryDao()

    suspend fun record(id: String): Pair<org.bookreader.mobile.model.Book, BookDetails>? = database.useReaderConnection { connection -> connection.deferredTransaction {
        val book=database.bookDao().findBook(id) ?: return@deferredTransaction null
        val row=(dao.detailsFor(id) ?: LibraryBookDetailsEntity(id)).also(::validateDetails)
        book.toBook() to BookDetails(row.metadataEdits,row.metadataScanned,row.readingStatus?.let(ReadingStatus::valueOf),row.coverPreset)
    } }

    suspend fun load(): LibraryCatalog = database.useReaderConnection { connection -> connection.deferredTransaction {
        currentCoroutineContext().ensureActive()
        val details = dao.details().associate { row ->
            validateDetails(row)
            row.bookId to BookDetails(row.metadataEdits, row.metadataScanned, row.readingStatus?.let(ReadingStatus::valueOf), row.coverPreset)
        }
        val shelves = dao.shelves().map { require(it.name.length in 1..80); Shelf(it.id, it.name) }
        val prefs = dao.preferences()?.let { LibraryPreferences(LibrarySort.valueOf(it.sort), LibraryView.valueOf(it.view),
            LibraryFilter.valueOf(it.filter), it.shelfId) } ?: LibraryPreferences()
        val progress = dao.progress().mapNotNull { row -> row.percent?.let { require(it.isFinite() && it in 0.0..100.0); row.bookId to it } }.toMap()
        LibraryCatalog(details, shelves, dao.memberships().groupBy({ it.bookId }, { it.shelfId }).mapValues { it.value.toSet() }, progress, prefs)
    } }

    suspend fun edit(bookId: String, edit: BookEdit) = write {
        val book = editable(bookId)
        val title = edit.title.trim(); val author = edit.author?.trim()?.takeIf { it.isNotEmpty() }
        require(title.length in 1..300 && title.none { it.code < 32 } && (author?.length ?: 0) <= 200 && author.orEmpty().none { it.code < 32 })
        require(edit.shelfIds.size <= 32 && dao.shelves().map { it.id }.containsAll(edit.shelfIds))
        val before = (dao.detailsFor(bookId) ?: LibraryBookDetailsEntity(bookId)).also(::validateDetails)
        var key = book.coverKey; var preset = before.coverPreset; var edits = before.metadataEdits or 3
        when (val cover = edit.cover) {
            CoverEdit.Keep -> Unit
            is CoverEdit.Generated -> { require(cover.preset in 0..5); key = null; preset = cover.preset; edits = edits or 4 }
            is CoverEdit.Image -> { require(isCoverKey(cover.key) && cover.key.startsWith("u-")); key = cover.key; edits = edits or 4 }
        }
        check(dao.updateMetadata(bookId, title, author, key, now()) == 1)
        dao.saveDetails(before.copy(metadataEdits = edits, readingStatus = edit.readingStatus?.name, coverPreset = preset))
        dao.clearMemberships(bookId)
        edit.shelfIds.forEach { dao.insertMembership(ShelfMembershipEntity(bookId, it)) }
    }

    /** Re-read edits inside the writer transaction: a delayed extractor cannot overwrite a user's save. */
    suspend fun applyExtracted(bookId: String, expectedHash: String, metadata: ExtractedBookMetadata) = write {
        val book = editable(bookId)
        check(book.sourceSha256 == expectedHash)
        val before = (dao.detailsFor(bookId) ?: LibraryBookDetailsEntity(bookId)).also(::validateDetails)
        fun text(value: String?, max: Int) = value?.filter { it.code >= 32 }?.trim()?.take(max)?.takeIf { it.isNotEmpty() }
        val title = if (before.metadataEdits and 1 == 0) text(metadata.title, 300) ?: book.title else book.title
        val author = if (before.metadataEdits and 2 == 0) text(metadata.author, 200) ?: book.author else book.author
        val key = if (before.metadataEdits and 4 == 0) metadata.coverKey?.also { require(isCoverKey(it) && it.startsWith("a-")) } ?: book.coverKey else book.coverKey
        if(title != book.title || author != book.author || key != book.coverKey)
            check(dao.updateMetadata(bookId, title, author, key, now()) == 1)
        dao.saveDetails(before.copy(metadataScanned = true))
    }

    suspend fun createShelf(id: String, name: String) = write {
        require(id.isNotBlank() && id.length <= 128 && dao.shelves().size < 200)
        val clean = shelfName(name)
        dao.insertShelf(ShelfEntity(id, clean, clean.lowercase()))
    }
    suspend fun renameShelf(id: String, name: String) = write {
        val clean = shelfName(name); check(dao.renameShelf(id, clean, clean.lowercase()) == 1)
    }
    suspend fun deleteShelf(id: String) = write { dao.clearShelfFilter(id); dao.deleteShelf(id) }

    suspend fun classify(bookIds: Set<String>, shelfId: String, add: Boolean) = write {
        require(bookIds.isNotEmpty() && bookIds.size <= 200 && dao.shelves().any { it.id == shelfId })
        for (id in bookIds) {
            editable(id)
            val memberships = dao.membershipsFor(id)
            if (add && shelfId !in memberships) {
                require(memberships.size < 32); dao.insertMembership(ShelfMembershipEntity(id, shelfId))
            } else if (!add) dao.removeMembership(id, shelfId)
        }
    }
    suspend fun preferences(value: LibraryPreferences) = write {
        require(value.shelfId == null || dao.shelves().any { it.id == value.shelfId })
        dao.savePreferences(LibraryPreferencesEntity(sort = value.sort.name, view = value.view.name, filter = value.filter.name, shelfId = value.shelfId))
    }

    private fun validateDetails(row: LibraryBookDetailsEntity) {
        require(row.metadataEdits in 0..7 && row.coverPreset in 0..5)
        require(row.readingStatus == null || ReadingStatus.entries.any { it.name == row.readingStatus })
    }
    private fun shelfName(value: String): String = value.trim().also { require(it.length in 1..80 && it.none { c -> c.code < 32 }) }
    private suspend fun editable(id: String): BookEntity = requireNotNull(database.bookDao().findBook(id)).also { check(it.availability != "DELETING") }
    private suspend fun write(action: suspend () -> Unit) {
        currentCoroutineContext().ensureActive()
        database.useWriterConnection { connection -> connection.immediateTransaction { action() } }
    }
}
