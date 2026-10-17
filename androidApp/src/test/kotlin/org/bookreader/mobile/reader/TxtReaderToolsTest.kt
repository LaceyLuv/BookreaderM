package org.bookreader.mobile.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.bookreader.mobile.locator.*
import org.bookreader.mobile.model.*
import org.bookreader.mobile.repository.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TxtReaderToolsTest {
    private val book = Book("a", BookFormat.TXT, "Own fixture", "a.txt", "books/a/original", "a".repeat(64),
        10, 1, 1, 1, currentRevision = "r1", encodingId = "utf-8", normalizationVersion = 1)

    @Test fun lateSearchCannotReplaceNewQueryAndResultStorageIsBounded() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val body = "a".repeat(2_000) + "b"
        val tools = TxtReaderTools(book, owner, { ready(body.length) }, { null }, { start, count ->
            if (reads.incrementAndGet() == 1) withContext(NonCancellable) { entered.complete(Unit); release.await() }
            body.substring(start.toInt(), minOf(body.length, start.toInt() + count))
        }, {}, null)
        try {
            tools.search("a", false)
            withTimeout(3_000) { entered.await() }
            tools.search("b", false)
            withTimeout(3_000) { tools.state.first { it.searchComplete } }
            release.complete(Unit)
            assertEquals("b", tools.state.value.query)
            assertEquals(listOf(2_000L), tools.state.value.hits.map { it.offset })
            tools.search("a", false)
            repeat(20) { index ->
                withTimeout(3_000) { tools.state.first { !it.searching && it.hits.size == (index + 1) * 50 } }
                tools.moreSearch()
            }
            assertEquals(1000, tools.state.value.hits.size)
            assertTrue(tools.state.value.searchCapped)
            assertFalse(tools.state.value.searchComplete)
        } finally { release.complete(Unit); owner.cancel() }
    }

    @Test fun searchReturnIsOneUseAndKeepsTheOriginalAnchorAcrossSeveralHits() {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var current = ready(100).copy(stableOffset = 10)
        val moves = mutableListOf<Long>()
        val tools = TxtReaderTools(book, owner, { current }, { null }, { _, _ -> "" }, {
            moves += it; current = current.copy(stableOffset = it)
        }, null)
        try {
            tools.jumpFromSearch(30); tools.jumpFromSearch(50)
            assertEquals(10L, tools.state.value.returnOffset)
            tools.returnFromSearch(); tools.returnFromSearch()
            assertEquals(listOf(30L, 50L, 10L), moves)
            assertNull(tools.state.value.returnOffset)
        } finally { owner.cancel() }
    }

    @Test fun bookmarkReadFailureKeepsVisibleRecordsAndReaderErrorBlocksMutations() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val mark = Bookmark("mark", "a", TxtLocator("r1", TxtLocatorPayload(10)), "preserve", 1, 1)
        var current = ready(100)
        var fail = false
        val writes = AtomicInteger()
        val access = object : ReaderBookmarkAccess {
            override suspend fun load(bookId: String, revision: String, cursor: BookmarkCursor?) =
                if (fail) BookmarkReadResult.Error else BookmarkReadResult.Ready(listOf(mark), null)
            override suspend fun add(id: String, session: ProgressSession, locator: TxtLocator, label: String?, now: Long): BookmarkWriteResult {
                writes.incrementAndGet(); return BookmarkWriteResult.COMMITTED
            }
            override suspend fun delete(bookId: String, id: String): BookmarkWriteResult {
                writes.incrementAndGet(); return BookmarkWriteResult.COMMITTED
            }
        }
        val tools = TxtReaderTools(book, owner, { current }, { ProgressSession("a", "r1", 1) }, { _, _ -> "" }, {}, access)
        try {
            tools.loadBookmarks()
            withTimeout(3_000) { tools.state.first { it.bookmarks.size == 1 } }
            fail = true; tools.loadBookmarks()
            withTimeout(3_000) { tools.state.first { it.bookmarkReadError } }
            assertEquals(listOf(mark), tools.state.value.bookmarks)
            current = current.copy(phase = TxtReaderPhase.ERROR)
            tools.addBookmark("no"); tools.deleteBookmark(mark.id)
            assertEquals(0, writes.get())
        } finally { owner.cancel() }
    }

    private fun ready(length: Int) = TxtReaderState(phase = TxtReaderPhase.READY,
        cache = TxtCacheSnapshot(committedLength = length.toLong(), complete = true))
}
