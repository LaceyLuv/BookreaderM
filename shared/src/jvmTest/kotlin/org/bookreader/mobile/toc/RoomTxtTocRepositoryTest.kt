package org.bookreader.mobile.toc

import androidx.room.execSQL
import androidx.room.useWriterConnection
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.*
import org.bookreader.mobile.repository.ProgressSession
import kotlin.test.*

class RoomTxtTocRepositoryTest {
    private val session = ProgressSession("a", "r1", 4)

    @Test fun editsExclusionsAndRestorationPersistWithoutChangingRawReadingRecords() = fixture { db ->
        val repo = RoomTxtTocRepository(db)
        val before = db.bookDao().findBook("a")
        val progress = db.progressDao().findProgress("a", "r1")
        val bookmarks = db.bookmarkDao().page("a", "r1", Long.MAX_VALUE, "z")
        val edit = TxtTocOverride(7, "수정한 회차 😀", false, "edit1")
        assertEquals(TxtTocWrite.SAVED, repo.save(session, 100, 7, null, edit))
        val exclude = TxtTocOverride(20, null, true, "exclude1")
        assertEquals(TxtTocWrite.SAVED, repo.save(session, 100, 20, null, exclude))
        val loaded = assertIs<TxtTocRead.Ready>(RoomTxtTocRepository(db).load("a", "r1", 100))
        assertEquals(listOf(edit, exclude), loaded.edits)
        val automatic = listOf(TxtHeading(7,"제1화",TxtHeadingKind.EPISODE),TxtHeading(20,"제2화",TxtHeadingKind.EPISODE))
        assertEquals(listOf(TxtTocEntry(7,"수정한 회차 😀",true,true)), TxtToc.merge(automatic, loaded.edits))
        assertEquals(TxtTocWrite.SAVED, repo.save(session, 100, 20, exclude, null))
        assertEquals(2, TxtToc.merge(automatic, assertIs<TxtTocRead.Ready>(repo.load("a","r1",100)).edits).size)
        assertEquals(before, db.bookDao().findBook("a"))
        assertEquals(progress, db.progressDao().findProgress("a", "r1"))
        assertEquals(bookmarks, db.bookmarkDao().page("a", "r1", Long.MAX_VALUE, "z"))
        assertEquals(ReaderGlobalSettingsEntity(schemaVersion=9,settingsJson="{future}"), db.readerSettingsDao().read("a").global)
    }

    @Test fun staleSessionRevisionConcurrentEditAndDeletedBookCannotOverwriteData() = fixture { db ->
        val repo = RoomTxtTocRepository(db)
        val edit = TxtTocOverride(7, "내 목차", false, "one")
        assertEquals(TxtTocWrite.SAVED, repo.save(session, 100, 7, null, edit))
        assertEquals(TxtTocWrite.CONFLICT, repo.save(session, 100, 7, null, edit.copy(changeId="late")))
        assertEquals(TxtTocWrite.CONFLICT, repo.save(session.copy(sessionEpoch=3), 100, 7, edit, null))
        db.useWriterConnection { it.execSQL("UPDATE books SET currentRevision='r2', activeSessionEpoch=5 WHERE id='a'") }
        assertEquals(TxtTocWrite.CONFLICT, repo.save(session,100,7,edit,null))
        assertTrue(assertIs<TxtTocRead.Ready>(repo.load("a","r2",100)).edits.isEmpty())
        assertEquals("one",db.txtTocDao().edits("a","r1").single().changeId)
        db.bookDao().deleteBook("a")
        assertEquals(TxtTocWrite.CONFLICT, repo.save(session,100,7,null,edit))
        assertTrue(db.txtTocDao().edits("a","r1").isEmpty())
        assertNull(db.bookDao().findBook("a"))
    }

    @Test fun futureOrMalformedRowsAndInvalidInputsFailClosedWithoutReplacingRecords() = fixture { db ->
        val repo = RoomTxtTocRepository(db)
        val future = TxtTocOverrideEntity("a","r1",8,2,"future",0,"preserve")
        db.txtTocDao().save(future)
        assertEquals(TxtTocRead.Error, repo.load("a","r1",100))
        assertEquals(TxtTocWrite.ERROR,repo.save(session,100,9,null,TxtTocOverride(9,"new",false,"new")))
        assertEquals(listOf(future),db.txtTocDao().edits("a","r1"))
        db.txtTocDao().save(future.copy(schemaVersion=1,excluded=2))
        assertEquals(TxtTocRead.Error,repo.load("a","r1",100))
        db.txtTocDao().delete("a","r1",8)
        for (title in listOf("", " leading", "two\nlines", "x".repeat(121), "\uD800", "\uDC00"))
            assertEquals(TxtTocWrite.ERROR, repo.save(session,100,7,null,TxtTocOverride(7,title,false,"invalid")))
        assertEquals(TxtTocWrite.ERROR,repo.save(session,100,100,null,TxtTocOverride(100,"end",false,"end")))
        assertTrue(db.txtTocDao().edits("a","r1").isEmpty())
    }

    @Test fun editLimitAndStatementFailurePreserveExistingRows() = fixture { db ->
        val repo=RoomTxtTocRepository(db)
        for (i in 0 until TxtToc.MAX_EDITS) db.txtTocDao().save(TxtTocOverrideEntity("a","r1",i.toLong(),1,"제목",0,"id-$i"))
        val before=db.txtTocDao().edits("a","r1")
        assertEquals(TxtTocWrite.ERROR,repo.save(session,2000,1000,null,TxtTocOverride(1000,"초과",false,"extra")))
        assertEquals(before,db.txtTocDao().edits("a","r1"))
        db.useWriterConnection { it.execSQL("CREATE TRIGGER reject_toc BEFORE UPDATE ON txt_toc_overrides BEGIN SELECT RAISE(ABORT, 'fixture failure'); END") }
        val expected=assertIs<TxtTocRead.Ready>(repo.load("a","r1",2000)).edits.first()
        assertEquals(TxtTocWrite.ERROR,repo.save(session,2000,0,expected,expected.copy(title="교체",changeId="new")))
        assertEquals(before,db.txtTocDao().edits("a","r1"))
    }

    private fun fixture(block:suspend (BookReaderDatabase)->Unit)= runBlocking {
        val folder=Files.createTempDirectory("toc-repository-")
        val db=createJvmDatabase(folder.resolve("reader.db"))
        try {
            db.bookDao().insertBook(BookEntity("a","TXT","Fixture","a.txt","books/a/original","a".repeat(64),
                100,1,1,1,currentRevision="r1",encodingId="utf-8",normalizationVersion=1,activeSessionEpoch=4))
            db.progressDao().insertProgress(ReadingProgressEntity("a","r1","future",9,"{untouched}",37.0,4,9,12))
            db.bookmarkDao().insert(BookmarkEntity("mark","a","r1","future",9,"{raw}","keep",1,2))
            db.useWriterConnection { it.execSQL("INSERT INTO reader_global_settings VALUES ('global',9,'{future}')") }
            block(db)
        } finally { db.close();Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
