package org.bookreader.mobile.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.bookreader.mobile.annotation.*
import org.bookreader.mobile.model.*
import org.bookreader.mobile.repository.ProgressSession
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TxtReaderAnnotationsTest {
    private val book=Book("a",BookFormat.TXT,"Fixture","a.txt","books/a/original","a".repeat(64),100,1,1,1,
        currentRevision="r1",encodingId="utf-8",normalizationVersion=1)
    private val session=ProgressSession("a","r1",4)
    private fun ready(length:Int)=TxtReaderState(phase=TxtReaderPhase.READY,cache=TxtCacheSnapshot(committedLength=length.toLong(),complete=true))

    @Test fun cancelledLateLoadAndMismatchedQuoteCannotPaintOrJumpToAnotherOccurrence()=runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val calls=AtomicInteger();val moves=AtomicInteger()
        val body="wrong target";val entry=TxtAnnotation("one",TxtQuote(0,6,"target"),HighlightColor.YELLOW,"","a")
        val access=object:TxtAnnotationsAccess {
            override suspend fun load(bookId:String,revision:String,length:Long):TxtAnnotationsRead {
                if(calls.incrementAndGet()==1) withContext(NonCancellable) {entered.complete(Unit);release.await()}
                return TxtAnnotationsRead.Ready(listOf(entry))
            }
            override suspend fun save(session:ProgressSession,length:Long,id:String,expected:TxtAnnotation?,replacement:TxtAnnotation?)=error("must not write")
        }
        val annotations=TxtReaderAnnotations(book,owner,{ready(body.length)},{session},{start,count->body.substring(start.toInt(),start.toInt()+count)},
            {moves.incrementAndGet()},access)
        try {
            annotations.load();withTimeout(3000){entered.await()};annotations.reset();release.complete(Unit)
            annotations.load();withTimeout(3000){annotations.state.first{it.readError}}
            assertTrue(annotations.state.value.entries.isEmpty());assertFalse(annotations.state.value.ready)
            annotations.jump(entry);assertEquals(0,moves.get())
            annotations.close();assertTrue(annotations.state.value.entries.isEmpty())
        } finally {release.complete(Unit);owner.cancel()}
    }

    @Test fun cancelledWindowSplitSurrogateChangedSourceAndStaleWritesCannotSaveOrMoveProgress()=runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Default);var body="😀 same same";val moves=AtomicInteger();val writes=AtomicInteger()
        val access=object:TxtAnnotationsAccess {
            override suspend fun load(bookId:String,revision:String,length:Long)=TxtAnnotationsRead.Ready(emptyList())
            override suspend fun save(session:ProgressSession,length:Long,id:String,expected:TxtAnnotation?,replacement:TxtAnnotation?):AnnotationWrite {
                writes.incrementAndGet();return AnnotationWrite.CONFLICT
            }
        }
        val annotations=TxtReaderAnnotations(book,owner,{ready(body.length)},{session},
            {start,count->body.substring(start.toInt(),minOf(body.length,start.toInt()+count))},{moves.incrementAndGet()},access)
        try {
            annotations.load();withTimeout(3000){annotations.state.first{it.ready}}
            annotations.beginSelection();val window=withTimeout(3000){annotations.state.first{it.window!=null}.window!!}
            annotations.add(window,1,2,HighlightColor.BLUE,"");assertEquals(0,writes.get())
            annotations.cancelSelection();annotations.add(window,0,2,HighlightColor.BLUE,"");assertEquals(0,writes.get())
            annotations.beginSelection();val fresh=withTimeout(3000){annotations.state.first{it.window!=null}.window!!}
            body="xx same same"
            annotations.add(fresh,0,2,HighlightColor.BLUE,"");withTimeout(3000){annotations.state.first{it.writeError}}
            assertEquals(0,writes.get());assertTrue(annotations.state.value.entries.isEmpty())
            annotations.cancelSelection();annotations.load(force=true);withTimeout(3000){annotations.state.first{it.ready}}
            annotations.beginSelection();val current=withTimeout(3000){annotations.state.first{it.window!=null}.window!!}
            annotations.add(current,3,7,HighlightColor.BLUE,"memo");withTimeout(3000){annotations.state.first{it.writeError && !it.ready}}
            assertEquals(1,writes.get());assertEquals(0,moves.get());assertEquals(0L,annotations.state.value.savedVersion)
            annotations.close();annotations.add(current,3,7,HighlightColor.BLUE,"");assertEquals(1,writes.get())
        } finally {owner.cancel()}
    }
}
