package org.bookreader.mobile.library

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.*
import org.bookreader.mobile.locator.*
import kotlin.test.*

class LibraryCatalogTest {
    @Test fun editsSurviveReopenAndLateExtractionWithoutChangingReadingRecords() = runBlocking {
        val dir = Files.createTempDirectory("bookreader-catalog-")
        val path = dir.resolve("db.sqlite")
        var db = createJvmDatabase(path)
        try {
            val book = book("one"); db.bookDao().insertBook(book)
            val progress = ReadingProgressEntity(book.id, "revision", "txt", 1,
                LocatorCodec().encode(TxtLocator("revision", TxtLocatorPayload(71))), 24.0, 9, 12, 19)
            db.progressDao().insertProgress(progress)
            db.bookmarkDao().insert(BookmarkEntity("mark",book.id,"revision","future",9,"{unchanged}","note",10,11))
            val manager = RoomLibraryManager(db) { 40 }
            manager.createShelf("shelf", "소설")
            manager.edit(book.id, BookEdit("나의 제목", "직접 입력한 작가", ReadingStatus.FINISHED, setOf("shelf"), CoverEdit.Generated(3)))
            manager.applyExtracted(book.id, book.sourceSha256, ExtractedBookMetadata("자동 제목", "자동 작가", "a-"+"b".repeat(64)+".jpg"))
            manager.preferences(LibraryPreferences(LibrarySort.AUTHOR, LibraryView.LIST, LibraryFilter.FINISHED, "shelf"))
            db.close(); db = createJvmDatabase(path)
            val updated = db.bookDao().findBook(book.id)!!
            assertEquals(book.copy(title="나의 제목", author="직접 입력한 작가", updatedAt=40), updated)
            assertEquals(progress, db.progressDao().findProgress(book.id,"revision"))
            assertEquals("{unchanged}", db.bookmarkDao().page(book.id,"revision",Long.MAX_VALUE,"z").single().locatorJson)
            val catalog=RoomLibraryManager(db) { 50 }.load()
            assertEquals(BookDetails(7,true,ReadingStatus.FINISHED,3),catalog.details[book.id])
            assertEquals(setOf("shelf"),catalog.memberships[book.id])
            assertEquals(24.0,catalog.progress[book.id])
            assertEquals(LibraryPreferences(LibrarySort.AUTHOR,LibraryView.LIST,LibraryFilter.FINISHED,"shelf"),catalog.preferences)
        } finally { db.close(); dir.toFile().deleteRecursively() }
    }

    @Test fun extractionUsesCurrentOverridesAndRejectsWrongSourceOrDeletedBook() = withDatabase { db ->
        val book=book("auto"); db.bookDao().insertBook(book)
        val manager=RoomLibraryManager(db) { 40 }
        manager.applyExtracted(book.id,book.sourceSha256,ExtractedBookMetadata("  EPUB 제목  ","작가","a-"+"a".repeat(64)+".jpg"))
        val updated=db.bookDao().findBook(book.id)!!
        assertEquals("EPUB 제목",updated.title);assertEquals("작가",updated.author)
        assertEquals(0,manager.load().details[book.id]!!.metadataEdits)
        assertFailsWith<IllegalStateException> { manager.applyExtracted(book.id,"c".repeat(64),ExtractedBookMetadata("wrong")) }
        assertEquals(updated,db.bookDao().findBook(book.id))
        db.bookDao().deleteBook(book.id)
        assertFailsWith<IllegalArgumentException> { manager.applyExtracted(book.id,book.sourceSha256,ExtractedBookMetadata("late")) }
        assertTrue(manager.load().details.isEmpty())
    }

    @Test fun failedBulkClassificationRollsBackAndDeletingShelfKeepsBooksAndProgress() = withDatabase { db ->
        val first=book("first"); val second=book("second")
        db.bookDao().insertBook(first);db.bookDao().insertBook(second)
        val progress=ReadingProgressEntity(first.id,"revision","future",8,"{future}",24.0,9,12,19)
        db.progressDao().insertProgress(progress)
        val manager=RoomLibraryManager(db) { 40 }
        manager.createShelf("one","Books")
        assertFails { manager.createShelf("duplicate"," books ") }
        assertFails { manager.classify(linkedSetOf(first.id,"absent"),"one",true) }
        assertTrue(manager.load().memberships.isEmpty())
        manager.classify(setOf(first.id,second.id),"one",true)
        manager.preferences(LibraryPreferences(shelfId="one"))
        assertEquals(2,manager.load().memberships.size)
        manager.deleteShelf("one")
        val catalog=manager.load()
        assertTrue(catalog.shelves.isEmpty());assertTrue(catalog.memberships.isEmpty());assertNull(catalog.preferences.shelfId)
        assertEquals(listOf(first,second),db.bookDao().listBooks())
        assertEquals(progress,db.progressDao().findProgress(first.id,"revision"))
    }

    @Test fun invalidEditsLeaveAllMetadataAndMembershipsUntouched() = withDatabase { db ->
        val book=book("one");db.bookDao().insertBook(book)
        val manager=RoomLibraryManager(db) { 40 }
        for (edit in listOf(BookEdit("",null,null,emptySet()),BookEdit("changed",null,null,setOf("absent")),
            BookEdit("changed",null,null,emptySet(),CoverEdit.Image("../../private")),
            BookEdit("changed",null,null,emptySet(),CoverEdit.Generated(9)))) {
            assertFails { manager.edit(book.id,edit) }
            assertEquals(book,db.bookDao().findBook(book.id));assertEquals(LibraryCatalog(),manager.load())
        }
    }

    @Test fun catalogSortSearchStatusAndShelfFiltersAreStableAndRevisionScoped() = withDatabase { db ->
        val a=book("a").copy(title="Beta",author="Writer",readOrder=2,lastReadAt=4)
        val b=book("b").copy(title="Alpha",author=null,readOrder=1,lastReadAt=3)
        val c=book("c").copy(title="Gamma",author="Another")
        listOf(a,b,c).forEach { db.bookDao().insertBook(it) }
        db.progressDao().insertProgress(ReadingProgressEntity(c.id,"old-revision","future",8,"{future}",100.0,8,2,3))
        val catalog=RoomLibraryManager(db) { 40 }.load()
        assertEquals(ReadingStatus.UNREAD,catalog.status(c.toBook()))
        val books=listOf(c,b,a).map { it.toBook() }
        fun selected(sort: LibrarySort,filter: LibraryFilter=LibraryFilter.ALL) = selectLibraryBooks(books,catalog,"",LibraryPreferences(sort=sort,filter=filter)).map { it.id }
        assertEquals(listOf("a","b","c"),selected(LibrarySort.RECENT))
        assertEquals(listOf("b","a","c"),selected(LibrarySort.TITLE))
        assertEquals(listOf("c","a","b"),selected(LibrarySort.AUTHOR))
        assertEquals(listOf("a","b"),selected(LibrarySort.RECENT,LibraryFilter.READING))
        assertEquals(listOf("a"),selectLibraryBooks(books,catalog,"writer").map { it.id })
        val manager=RoomLibraryManager(db) { 40 };manager.createShelf("s","Shelf");manager.classify(setOf("b"),"s",true)
        assertEquals(listOf("b"),selectLibraryBooks(books,manager.load(),"",LibraryPreferences(shelfId="s")).map { it.id })
    }

    @Test fun unknownCatalogValuesCannotBeSilentlyRewrittenByEditingOrExtraction() = withDatabase { db ->
        val book=book("future");db.bookDao().insertBook(book)
        val manager=RoomLibraryManager(db) { 40 }
        for (row in listOf(LibraryBookDetailsEntity(book.id,metadataEdits=16),
            LibraryBookDetailsEntity(book.id,readingStatus="FUTURE"),LibraryBookDetailsEntity(book.id,coverPreset=20))) {
            db.libraryDao().saveDetails(row)
            assertFails { manager.record(book.id) };assertFails { manager.load() }
            assertFails { manager.edit(book.id,BookEdit("changed",null,null,emptySet())) }
            assertFails { manager.applyExtracted(book.id,book.sourceSha256,ExtractedBookMetadata("changed")) }
            assertEquals(row,db.libraryDao().detailsFor(book.id))
            assertEquals(book,db.bookDao().findBook(book.id))
        }
    }

    private fun book(id: String)=BookEntity(id,"TXT","Original","$id.txt","books/$id/original",
        java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) },123,
        5,6,7,currentRevision="revision",encodingId="utf-8",normalizationVersion=1,activeSessionEpoch=9)
    private fun withDatabase(block: suspend (BookReaderDatabase) -> Unit) = runBlocking {
        val dir=Files.createTempDirectory("bookreader-catalog-");val db=createJvmDatabase(dir.resolve("db.sqlite"))
        try { block(db) } finally { db.close();dir.toFile().deleteRecursively() }
    }
}
