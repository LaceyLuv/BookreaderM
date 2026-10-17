package org.bookreader.mobile.repository

import androidx.room.execSQL
import androidx.room.useWriterConnection
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.*
import org.bookreader.mobile.locator.*
import kotlin.test.*

class RoomBookmarkRepositoryTest {
    @Test fun bookmarksPersistCanonicalLocatorsWithBoundedPagesAndProtectOtherBooks() = fixture { db ->
        val repository = RoomBookmarkRepository(db)
        val session = ProgressSession("a", "r1", 1)
        for (i in 0..54) assertEquals(BookmarkWriteResult.COMMITTED,
            repository.add("mark-$i", session, TxtLocator("r1", TxtLocatorPayload(i.toLong())), "own label $i", i.toLong()))
        val first = assertIs<BookmarkReadResult.Ready>(repository.load("a", "r1"))
        assertEquals(50, first.bookmarks.size)
        val second = assertIs<BookmarkReadResult.Ready>(repository.load("a", "r1", first.next))
        assertEquals(5, second.bookmarks.size)
        assertNull(second.next)
        assertEquals(55, (first.bookmarks + second.bookmarks).map { it.id }.toSet().size)
        assertEquals(BookmarkWriteResult.COMMITTED, repository.delete("other", first.bookmarks.first().id))
        assertEquals(first, repository.load("a", "r1"))
        assertNull(db.progressDao().findProgress("a", "r1"))
        assertEquals(1L, db.bookDao().findBook("a")!!.activeSessionEpoch)
    }

    @Test fun oldRevisionAndSessionWritesAreRejectedAndDeletionCannotRecreateBookmarks() = fixture { db ->
        val repository = RoomBookmarkRepository(db)
        val session = ProgressSession("a", "r1", 1)
        assertEquals(BookmarkWriteResult.COMMITTED, repository.add("old", session, TxtLocator("r1", TxtLocatorPayload(12)), null, 1))
        db.useWriterConnection { it.execSQL("UPDATE books SET currentRevision = 'r2', activeSessionEpoch = 2 WHERE id = 'a'") }
        assertEquals(BookmarkWriteResult.REJECTED_STALE, repository.add("late", session, TxtLocator("r1", TxtLocatorPayload(13)), null, 2))
        assertEquals(1, assertIs<BookmarkReadResult.Ready>(repository.load("a", "r1")).bookmarks.size)
        assertTrue(assertIs<BookmarkReadResult.Ready>(repository.load("a", "r2")).bookmarks.isEmpty())
        db.useWriterConnection { it.execSQL("DELETE FROM books WHERE id = 'a'") }
        assertEquals(BookmarkWriteResult.REJECTED_STALE, repository.add("late", session, TxtLocator("r1", TxtLocatorPayload(13)), null, 2))
        assertTrue(assertIs<BookmarkReadResult.Ready>(repository.load("a", "r1")).bookmarks.isEmpty())
        assertNull(db.bookDao().findBook("a"))
    }

    @Test fun malformedAndQueryFailurePreserveOriginalBookmarkAndProgressRows() = fixture { db ->
        val original = BookmarkEntity("broken", "a", "r1", "txt", 1, "{bad", "preserve", 1, 1)
        db.bookmarkDao().insert(original)
        val repository = RoomBookmarkRepository(db)
        assertEquals(BookmarkReadResult.Error, repository.load("a", "r1"))
        assertEquals(original, db.bookmarkDao().page("a", "r1", Long.MAX_VALUE, "z").single())
        db.useWriterConnection { it.execSQL("ALTER TABLE bookmarks RENAME COLUMN bookId TO unavailable_book") }
        try { assertEquals(BookmarkReadResult.Error, repository.load("a", "r1")) }
        finally { db.useWriterConnection { it.execSQL("ALTER TABLE bookmarks RENAME COLUMN unavailable_book TO bookId") } }
        assertEquals(original, db.bookmarkDao().page("a", "r1", Long.MAX_VALUE, "z").single())
        assertNull(db.progressDao().findProgress("a", "r1"))
    }

    private fun fixture(block: suspend (BookReaderDatabase) -> Unit) = runBlocking {
        val folder = Files.createTempDirectory("bookmark-repository-")
        val db = createJvmDatabase(folder.resolve("reader.db"))
        try {
            db.bookDao().insertBook(BookEntity("a", "TXT", "Own fixture", "a.txt", "books/a/original", "a".repeat(64),
                100, 1, 1, 1, currentRevision = "r1", encodingId = "utf-8", normalizationVersion = 1, activeSessionEpoch = 1))
            block(db)
        } finally { db.close(); Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
