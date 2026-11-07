package org.bookreader.mobile.annotation

import androidx.room.execSQL
import androidx.room.useWriterConnection
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.*
import org.bookreader.mobile.repository.ProgressSession
import kotlin.test.*

class RoomEpubAnnotationsRepositoryTest {
    private val session=ProgressSession("a","r1",4)
    @Test fun annotationCrudKeepsEveryExistingReadingRecordByteExact()=fixture{db->
        val repo=RoomEpubAnnotationsRepository(db);val entry=epubEntry()
        val before=snapshot(db)
        assertEquals(AnnotationWrite.SAVED,repo.save(session,entry.id,null,entry))
        assertEquals(listOf(entry),assertIs<EpubAnnotationsRead.Ready>(repo.load("a","r1")).entries)
        val edited=entry.copy(color=HighlightColor.PINK,note="수정 😀",changeId="changed")
        assertEquals(AnnotationWrite.SAVED,repo.save(session,entry.id,entry,edited))
        assertEquals(listOf(edited),assertIs<EpubAnnotationsRead.Ready>(RoomEpubAnnotationsRepository(db).load("a","r1")).entries)
        assertEquals(AnnotationWrite.SAVED,repo.save(session,entry.id,edited,null));assertEquals(before,snapshot(db))
    }
    @Test fun staleSessionsConcurrentEditsRevisionChangesAndDeletedOwnersRejectWrites()=fixture{db->
        val repo=RoomEpubAnnotationsRepository(db);val entry=epubEntry()
        assertEquals(AnnotationWrite.SAVED,repo.save(session,entry.id,null,entry))
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session.copy(sessionEpoch=3),entry.id,entry,null))
        val edited=entry.copy(changeId="new",note="new")
        assertEquals(AnnotationWrite.SAVED,repo.save(session,entry.id,entry,edited))
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session,entry.id,entry,null))
        db.useWriterConnection{it.execSQL("UPDATE books SET currentRevision='r2',activeSessionEpoch=5 WHERE id='a'")}
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session,entry.id,edited,null));assertEquals(EpubAnnotationsRead.Error,repo.load("a","r1"))
        assertTrue(assertIs<EpubAnnotationsRead.Ready>(repo.load("a","r2")).entries.isEmpty())
        assertEquals("new",db.epubAnnotationDao().entries("a","r1").single().changeId)
        db.bookDao().deleteBook("a");assertEquals(AnnotationWrite.CONFLICT,repo.save(session,entry.id,null,entry))
    }
    @Test fun futureMalformedAndEmbeddedNulOversizeRowsAreNeverRewritten()=fixture{db->
        val repo=RoomEpubAnnotationsRepository(db);val entry=epubEntry()
        val raw=EpubAnnotationEntity("a","r1","one",1,EpubAnnotations.json.encodeToString(EpubAnchor.serializer(),entry.anchor),entry.locatorJson,"YELLOW","keep","old")
        val before=snapshot(db)
        for(bad in listOf(raw.copy(schemaVersion=2),raw.copy(color="FUTURE"),raw.copy(anchorJson="{bad}"),raw.copy(locatorJson="{}"),
            raw.copy(note="x".repeat(2001)),raw.copy(anchorJson="\u0000"+"x".repeat(65536)),raw.copy(locatorJson="\u0000"+"x".repeat(65536)),raw.copy(note="\u0000"+"x".repeat(8000)))) {
            db.epubAnnotationDao().save(bad)
            assertEquals(EpubAnnotationsRead.Error,repo.load("a","r1"))
            assertEquals(AnnotationWrite.ERROR,repo.save(session,"two",null,epubEntry("two")))
            assertEquals(listOf(bad),db.epubAnnotationDao().entries("a","r1"));assertEquals(before,snapshot(db))
        }
    }
    @Test fun entryLimitAndAbortedSqlKeepExistingAnnotations()=fixture{db->
        val repo=RoomEpubAnnotationsRepository(db)
        for(i in 0 until 500) assertEquals(AnnotationWrite.SAVED,repo.save(session,"id-$i",null,epubEntry("id-$i")))
        val before=db.epubAnnotationDao().entries("a","r1")
        assertEquals(AnnotationWrite.ERROR,repo.save(session,"more",null,epubEntry("more")))
        val entry=assertIs<EpubAnnotationsRead.Ready>(repo.load("a","r1")).entries.first()
        db.useWriterConnection{it.execSQL("CREATE TRIGGER reject_epub_annotation BEFORE UPDATE ON epub_annotations BEGIN SELECT RAISE(ABORT,'fixture failure'); END")}
        assertEquals(AnnotationWrite.ERROR,repo.save(session,entry.id,entry,entry.copy(note="new",changeId="new")))
        assertEquals(before,db.epubAnnotationDao().entries("a","r1"))
    }
    private suspend fun snapshot(db:BookReaderDatabase):List<Any?> = listOf(db.bookDao().findBook("a"),db.progressDao().findProgress("a","r1"),
        db.bookmarkDao().page("a","r1",Long.MAX_VALUE,"z"),db.readerSettingsDao().read("a"),db.txtAnnotationDao().entries("a","r1"),db.txtTocDao().edits("a","r1"))
    private fun fixture(block:suspend(BookReaderDatabase)->Unit)=runBlocking {
        val folder=Files.createTempDirectory("epub-annotations-");val db=createJvmDatabase(folder.resolve("reader.db"))
        try {
            db.bookDao().insertBook(BookEntity("a","EPUB","Fixture","a.epub","books/a/original","a".repeat(64),100,1,1,1,currentRevision="r1",activeSessionEpoch=4))
            db.progressDao().insertProgress(ReadingProgressEntity("a","r1","future",9,"{untouched}",37.0,4,9,12))
            db.bookmarkDao().insert(BookmarkEntity("mark","a","r1","future",9,"{raw}","keep",1,2))
            db.readerSettingsDao().putGlobal(ReaderGlobalSettingsEntity(schemaVersion=9,settingsJson="{future}"))
            db.txtTocDao().save(TxtTocOverrideEntity("a","r1",1,2,"future",3,"preserve"))
            db.txtAnnotationDao().save(TxtAnnotationEntity("a","r1","old",9,0,3,"raw","FUTURE","note","change"))
            block(db)
        } finally {db.close();Files.walk(folder).use{it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}}
    }
}
