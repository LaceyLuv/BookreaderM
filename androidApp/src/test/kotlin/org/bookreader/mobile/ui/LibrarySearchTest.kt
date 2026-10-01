package org.bookreader.mobile.ui

import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchTest {
    private val books = listOf(
        book("1", "한글 제목", "Author", "first.txt"),
        book("2", "Second", null, "literal_%_file.epub"),
    )

    @Test fun searchesTitleAuthorAndOriginalNameIgnoringCase() {
        assertEquals(listOf(books[0]), filterLibraryBooks(books, "한글"))
        assertEquals(listOf(books[0]), filterLibraryBooks(books, "AUTHOR"))
        assertEquals(listOf(books[1]), filterLibraryBooks(books, "FILE.EPUB"))
    }

    @Test fun wildcardCharactersAreLiteralAndEmptyQueryShowsAll() {
        assertEquals(listOf(books[1]), filterLibraryBooks(books, "_%_"))
        assertEquals(books, filterLibraryBooks(books, ""))
        assertEquals(emptyList<Book>(), filterLibraryBooks(books, "missing"))
    }

    private fun book(id: String, title: String, author: String?, originalName: String) = Book(
        id = id, format = BookFormat.TXT, title = title, author = author,
        originalDisplayName = originalName, managedRelativePath = "books/$id/original",
        sourceSha256 = "a".repeat(64), sourceByteSize = 1, currentRevision = "revision-1",
        addedAt = 1, createdAt = 1, updatedAt = 1,
    )
}
