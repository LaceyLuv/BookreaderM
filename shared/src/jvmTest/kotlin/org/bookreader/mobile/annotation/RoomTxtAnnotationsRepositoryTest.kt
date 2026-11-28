package org.bookreader.mobile.annotation

import androidx.room.execSQL
import androidx.room.useWriterConnection
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.*
import org.bookreader.mobile.repository.ProgressSession
import kotlin.test.*

class RoomTxtAnnotationsRepositoryTest {
    private val session = ProgressSession("a","r1",4)
    private val entry = TxtAnnotation("one",TxtQuote(7,12,"문장 😀"),HighlightColor.YELLOW,"내 메모","change1")

    @Test fun selectionsEditsAndDeletesPersistWithoutChangingOtherRawRecords() = fixture { db ->
        val repo=RoomTxtAnnotationsRepository(db)
        val book=db.bookDao().findBook("a");val progress=db.progressDao().findProgress("a","r1")
        val bookmark=db.bookmarkDao().page("a","r1",Long.MAX_VALUE,"z")
        val settings=db.readerSettingsDao().read("a");val toc=db.txtTocDao().edits("a","r1")
        assertEquals(AnnotationWrite.SAVED,repo.save(session,100,entry.id,null,entry))
        assertEquals(listOf(entry),assertIs<TxtAnnotationsRead.Ready>(RoomTxtAnnotationsRepository(db).load("a","r1",100)).entries)
        val edited=entry.copy(color=HighlightColor.PINK,note="수정\n메모",changeId="change2")
        assertEquals(AnnotationWrite.SAVED,repo.save(session,100,entry.id,entry,edited))
        assertEquals(listOf(edited),assertIs<TxtAnnotationsRead.Ready>(repo.load("a","r1",100)).entries)
        assertEquals(AnnotationWrite.SAVED,repo.save(session,100,entry.id,edited,null))
        assertTrue(db.txtAnnotationDao().entries("a","r1").isEmpty())
        assertEquals(book,db.bookDao().findBook("a"));assertEquals(progress,db.progressDao().findProgress("a","r1"))
        assertEquals(bookmark,db.bookmarkDao().page("a","r1",Long.MAX_VALUE,"z"))
        assertEquals(settings,db.readerSettingsDao().read("a"));assertEquals(toc,db.txtTocDao().edits("a","r1"))
    }

    @Test fun concurrentEditsStaleEpochRevisionAndDeletedOwnersCannotOverwriteSelections() = fixture { db ->
        val repo=RoomTxtAnnotationsRepository(db)
        assertEquals(AnnotationWrite.SAVED,repo.save(session,100,entry.id,null,entry))
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session,100,entry.id,null,entry.copy(changeId="late")))
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session.copy(sessionEpoch=3),100,entry.id,entry,null))
        val edited=entry.copy(changeId="fresh",note="new")
        assertEquals(AnnotationWrite.SAVED,repo.save(session,100,entry.id,entry,edited))
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session,100,entry.id,entry,null))
        db.useWriterConnection { it.execSQL("UPDATE books SET currentRevision='r2', activeSessionEpoch=5 WHERE id='a'") }
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session,100,entry.id,edited,null))
        assertEquals(TxtAnnotationsRead.Error,repo.load("a","r1",100))
        assertTrue(assertIs<TxtAnnotationsRead.Ready>(repo.load("a","r2",100)).entries.isEmpty())
        assertEquals("fresh",db.txtAnnotationDao().entries("a","r1").single().changeId)
        db.bookDao().deleteBook("a")
        assertEquals(AnnotationWrite.CONFLICT,repo.save(session,100,entry.id,null,entry))
        assertTrue(db.txtAnnotationDao().entries("a","r1").isEmpty())
    }

    @Test fun unknownVersionsColorsAndMalformedRangesBlockReplacementWithoutDataLoss() = fixture { db ->
        val repo=RoomTxtAnnotationsRepository(db)
        val raw=TxtAnnotationEntity("a","r1","future",2,0,3,"abc","YELLOW","keep","old")
        for (bad in listOf(raw,raw.copy(schemaVersion=1,color="FUTURE"),raw.copy(schemaVersion=1,endOffset=4),
            raw.copy(schemaVersion=1,note="x".repeat(2001)),raw.copy(schemaVersion=1,quote="x".repeat(4097),endOffset=4097))) {
            db.txtAnnotationDao().save(bad)
            assertEquals(TxtAnnotationsRead.Error,repo.load("a","r1",100))
            assertEquals(AnnotationWrite.ERROR,repo.save(session,100,entry.id,null,entry))
            assertEquals(listOf(bad),db.txtAnnotationDao().entries("a","r1"))
        }
        // Binding a lone surrogate to native SQLite does not preserve those UTF-16 units.
        // Validate through the production write boundary BEFORE binding, not by a lossy raw fixture.
        val before = db.txtAnnotationDao().entries("a","r1")
        assertEquals(AnnotationWrite.ERROR,repo.save(session,100,entry.id,null,
            entry.copy(quote=TxtQuote(0,3,"\uD800xx"))))
        assertEquals(before,db.txtAnnotationDao().entries("a","r1"))
        // SQLite length(TEXT) stops at NUL. Detect oversized raw fields before materializing rows.
        for (bad in listOf(
            raw.copy(schemaVersion=1,quote="\u0000"+"x".repeat(16384)),
            raw.copy(schemaVersion=1,note="\u0000"+"x".repeat(8000)),
            raw.copy(schemaVersion=1,changeId="\u0000"+"x".repeat(512)),
            raw.copy(schemaVersion=1,color="\u0000"+"x".repeat(128)),
        )) {
            db.txtAnnotationDao().save(bad)
            assertTrue(db.txtAnnotationDao().hasOversizedFields("a","r1"))
            assertEquals(TxtAnnotationsRead.Error,repo.load("a","r1",100))
            assertEquals(AnnotationWrite.ERROR,repo.save(session,100,entry.id,null,entry))
            assertEquals(listOf(bad),db.txtAnnotationDao().entries("a","r1"))
        }
    }

    @Test fun entryLimitAndNativeStatementFailureRollBackAnnotationChanges() = fixture { db ->
        val repo=RoomTxtAnnotationsRepository(db)
        for (i in 0 until TxtAnnotations.MAX_ENTRIES) db.txtAnnotationDao().save(
            TxtAnnotationEntity("a","r1","id-$i",1,0,1,"x","GREEN","","change-$i"))
        val before=db.txtAnnotationDao().entries("a","r1")
        assertEquals(AnnotationWrite.ERROR,repo.save(session,100,entry.id,null,entry))
        assertEquals(before,db.txtAnnotationDao().entries("a","r1"))
        val expected=assertIs<TxtAnnotationsRead.Ready>(repo.load("a","r1",100)).entries.first()
        db.useWriterConnection { it.execSQL("CREATE TRIGGER reject_annotation BEFORE UPDATE ON txt_annotations BEGIN SELECT RAISE(ABORT, 'fixture failure'); END") }
        assertEquals(AnnotationWrite.ERROR,repo.save(session,100,expected.id,expected,expected.copy(note="changed",changeId="other")))
        assertEquals(before,db.txtAnnotationDao().entries("a","r1"))
    }

    private fun fixture(block:suspend (BookReaderDatabase)->Unit)=runBlocking {
        val folder=Files.createTempDirectory("annotations-");val db=createJvmDatabase(folder.resolve("reader.db"))
        try {
            db.bookDao().insertBook(BookEntity("a","TXT","Fixture","a.txt","books/a/original","a".repeat(64),100,1,1,1,
                currentRevision="r1",encodingId="utf-8",normalizationVersion=1,activeSessionEpoch=4))
            db.progressDao().insertProgress(ReadingProgressEntity("a","r1","future",9,"{untouched}",37.0,4,9,12))
            db.bookmarkDao().insert(BookmarkEntity("mark","a","r1","future",9,"{raw}","keep",1,2))
            db.readerSettingsDao().putGlobal(ReaderGlobalSettingsEntity(schemaVersion=9,settingsJson="{future}"))
            db.txtTocDao().save(TxtTocOverrideEntity("a","r1",1,2,"future",3,"preserve"))
            block(db)
        } finally { db.close();Files.walk(folder).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
