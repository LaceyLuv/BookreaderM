package org.bookreader.mobile.search

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.*

class TxtLiteralSearchTest {
    @Test fun fragmentBoundaryOverlappingAndAstralQueriesKeepCanonicalOffsets() = runBlocking {
        val body = "x".repeat(16_383) + "가나다😀" + "aaaaa" + "😀😀😀"
        for (query in listOf("가나다😀", "aaa", "😀😀")) {
            val expected = body.indices.filter { body.startsWith(query, it) }.map(Int::toLong)
            val result = TxtLiteralSearch.find(query, false, body.length.toLong()) { start, count ->
                body.substring(start.toInt(), minOf(start.toInt() + count, body.length))
            }
            assertEquals(expected, result.offsets)
            assertNull(result.nextOffset)
        }
    }

    @Test fun batchesPreserveOverlapsAndOnlyAsciiCaseIsFolded() = runBlocking {
        val body = "Aa".repeat(200) + " İi ßSS 한글"
        val all = mutableListOf<Long>()
        var cursor: Long? = 0
        while (cursor != null) {
            val batch = TxtLiteralSearch.find("aa", true, body.length.toLong(), cursor) { start, count ->
                body.substring(start.toInt(), minOf(start.toInt() + count, body.length))
            }
            assertTrue(batch.offsets.size <= 50)
            all += batch.offsets
            cursor = batch.nextOffset
        }
        assertEquals((0L..398L).toList(), all)
        for ((query, expected) in listOf("i" to listOf(402L), "ss" to listOf(405L))) {
            assertEquals(expected, TxtLiteralSearch.find(query, true, body.length.toLong()) { start, count ->
                body.substring(start.toInt(), minOf(start.toInt() + count, body.length))
            }.offsets)
        }
    }

    @Test fun invalidQueriesAndCancelledScanNeverReadUnboundedBody() = runBlocking {
        for (query in listOf("", "x".repeat(257), "\uD83D", "\uDE00")) {
            assertFailsWith<IllegalArgumentException> { TxtLiteralSearch.find(query, false, 100) { _, _ -> error("No read") } }
        }
        val job = Job()
        var reads = 0
        assertFailsWith<CancellationException> {
            withContext(job) {
                TxtLiteralSearch.find("missing", false, 1_048_576) { _, count ->
                    assertTrue(count <= 16_384)
                    reads++
                    job.cancel()
                    "x".repeat(count)
                }
            }
        }
        assertEquals(1, reads)
    }
}
