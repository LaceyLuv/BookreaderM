package org.bookreader.mobile.library

import org.bookreader.mobile.model.Book

enum class ReadingStatus { UNREAD, READING, FINISHED }
enum class LibrarySort { RECENT, ADDED, TITLE, AUTHOR }
enum class LibraryView { GRID, LIST }
enum class LibraryFilter { ALL, UNREAD, READING, FINISHED }

data class LibraryPreferences(val sort: LibrarySort = LibrarySort.RECENT, val view: LibraryView = LibraryView.GRID,
    val filter: LibraryFilter = LibraryFilter.ALL, val shelfId: String? = null)
data class Shelf(val id: String, val name: String)
data class BookDetails(val metadataEdits: Int = 0, val metadataScanned: Boolean = false,
    val readingStatus: ReadingStatus? = null, val coverPreset: Int = 0)
data class LibraryCatalog(val details: Map<String, BookDetails> = emptyMap(), val shelves: List<Shelf> = emptyList(),
    val memberships: Map<String, Set<String>> = emptyMap(), val progress: Map<String, Double> = emptyMap(),
    val preferences: LibraryPreferences = LibraryPreferences()) {
    fun status(book: Book): ReadingStatus = details[book.id]?.readingStatus ?: when {
        (progress[book.id] ?: 0.0) >= 100.0 -> ReadingStatus.FINISHED
        book.lastReadAt != null || progress.containsKey(book.id) -> ReadingStatus.READING
        else -> ReadingStatus.UNREAD
    }
}

sealed interface CoverEdit {
    data object Keep : CoverEdit
    data class Generated(val preset: Int) : CoverEdit
    data class Image(val key: String) : CoverEdit
}
data class BookEdit(val title: String, val author: String?, val readingStatus: ReadingStatus?,
    val shelfIds: Set<String>, val cover: CoverEdit = CoverEdit.Keep)
data class ExtractedBookMetadata(val title: String? = null, val author: String? = null, val coverKey: String? = null)

/** Storage keys are owned, content-addressed thumbnails, never caller-supplied filesystem paths. */
fun isCoverKey(key: String): Boolean = key.length == 70 && key.take(2) in setOf("a-", "u-") && key.endsWith(".jpg") &&
    key.substring(2, 66).all { it in '0'..'9' || it in 'a'..'f' }

fun selectLibraryBooks(books: List<Book>, catalog: LibraryCatalog, query: String,
    preferences: LibraryPreferences = catalog.preferences): List<Book> {
    val needle = query.trim().lowercase()
    val selected = books.filter { book ->
        (needle.isEmpty() || listOfNotNull(book.title, book.author, book.originalDisplayName).any { needle in it.lowercase() }) &&
            (preferences.filter == LibraryFilter.ALL || catalog.status(book).name == preferences.filter.name) &&
            (preferences.shelfId == null || preferences.shelfId in catalog.memberships[book.id].orEmpty())
    }
    val order = when (preferences.sort) {
        LibrarySort.RECENT -> compareByDescending<Book> { it.readOrder ?: Long.MIN_VALUE }.thenByDescending { it.addedAt }
        LibrarySort.ADDED -> compareByDescending<Book> { it.addedAt }
        LibrarySort.TITLE -> compareBy<Book> { it.title.lowercase() }
        LibrarySort.AUTHOR -> compareBy<Book> { it.author.isNullOrBlank() }.thenBy { it.author.orEmpty().lowercase() }.thenBy { it.title.lowercase() }
    }
    return selected.sortedWith(order.thenBy { it.id })
}
