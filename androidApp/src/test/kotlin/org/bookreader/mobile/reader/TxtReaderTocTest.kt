package org.bookreader.mobile.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.bookreader.mobile.model.*
import org.bookreader.mobile.repository.ProgressSession
import org.bookreader.mobile.toc.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TxtReaderTocTest {
    private val book = Book("a",BookFormat.TXT,"Fixture","a.txt","books/a/original","a".repeat(64),100,1,1,1,
        currentRevision="r1",encodingId="utf-8",normalizationVersion=1)
    private fun ready(length:Int)=TxtReaderState(phase=TxtReaderPhase.READY,cache=TxtCacheSnapshot(committedLength=length.toLong(),complete=true))

    @Test fun cancelledLateScanCannotPublishOrNavigateAndCanBeRetried() = runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val calls=AtomicInteger()
        val body="제1화 시작\n본문\n제2화 다음\n"
        val moves=mutableListOf<Long>()
        val toc=TxtReaderToc(book,owner,{ready(body.length)},{null},{ start,count ->
            if(calls.incrementAndGet()==1) withContext(NonCancellable) { entered.complete(Unit);release.await() }
            body.substring(start.toInt(),minOf(body.length,start.toInt()+count))
        },{moves+=it},null)
        try {
            toc.load();withTimeout(3000){entered.await()};toc.cancelLoad()
            toc.load();withTimeout(3000){toc.state.first{it.ready}}
            release.complete(Unit)
            assertEquals(listOf(0L,body.indexOf("제2화").toLong()),toc.state.value.entries.map{it.offset})
            assertTrue(moves.isEmpty())
            toc.jump(body.indexOf("제2화").toLong());assertEquals(listOf(body.indexOf("제2화").toLong()),moves)
            toc.close();toc.jump(0);assertEquals(1,moves.size)
        } finally { release.complete(Unit);owner.cancel() }
    }

    @Test fun readFailureAndStaleWriteDisableEditsAndSurrogateBoundaryNeverChangesPosition() = runBlocking {
        val owner=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val body="😀\n제1화 시작\n";var current=ready(body.length);var readFailure=false
        val writes=AtomicInteger();val moves=AtomicInteger()
        val access=object:TxtTocAccess {
            override suspend fun load(bookId:String,revision:String,length:Long)=if(readFailure) TxtTocRead.Error else TxtTocRead.Ready(emptyList())
            override suspend fun save(session:ProgressSession,length:Long,offset:Long,expected:TxtTocOverride?,replacement:TxtTocOverride?):TxtTocWrite {
                writes.incrementAndGet();return TxtTocWrite.CONFLICT
            }
        }
        val toc=TxtReaderToc(book,owner,{current},{ProgressSession("a","r1",4)}, {start,count -> body.substring(start.toInt(),minOf(body.length,start.toInt()+count)) },{moves.incrementAndGet()},access)
        try {
            toc.load();withTimeout(3000){toc.state.first{it.ready}}
            toc.renameOrAdd(1,"잘못된 경계");withTimeout(3000){toc.state.first{it.writeError}}
            assertEquals(0,writes.get())
            toc.renameOrAdd(3,"제목 수정");withTimeout(3000){toc.state.first{it.writeError && !it.ready}}
            assertEquals(1,writes.get());toc.exclude(3);assertEquals(1,writes.get())
            readFailure=true;toc.load();withTimeout(3000){toc.state.first{it.readError}}
            assertEquals(1,toc.state.value.entries.size);assertEquals(0,moves.get())
            current=current.copy(phase=TxtReaderPhase.ERROR);toc.clearEdit(3);assertEquals(1,writes.get())
        } finally { owner.cancel() }
    }
}
