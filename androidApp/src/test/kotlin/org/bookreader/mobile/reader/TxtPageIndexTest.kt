package org.bookreader.mobile.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class TxtPageIndexTest {
    @Test fun countsMeasuredBoundariesAndMapsStartMiddleAndEndWithoutAnExtraPage() = runBlocking {
        val boundaries = mapOf(0L to 11L, 11L to 24L, 24L to 29L)
        val visited = mutableListOf<Long>()
        val index = TxtPageIndex.build(29) { visited += it; boundaries.getValue(it) }
        assertEquals(listOf(0L, 11L, 24L), visited)
        assertEquals(3, index.total)
        for ((offset, page) in listOf(0L to 1, 10L to 1, 11L to 2, 23L to 2, 24L to 3, 29L to 3))
            assertEquals(page, index.pageAt(offset))
    }

    @Test fun rejectsNonAdvancingRangesAndPropagatesCancellation() = runBlocking {
        try { TxtPageIndex.build(100) { it }; fail("A broken paginator must not loop forever") }
        catch (_: IllegalStateException) { }
        val cancelled = CancellationException("fixture cancellation")
        try { TxtPageIndex.build(100) { throw cancelled }; fail("Cancellation must propagate") }
        catch (actual: CancellationException) { assertSame(cancelled, actual) }
    }

    @Test fun boundedWindowReaderReusesChunkAndPreservesSurrogateBoundaries() = runBlocking {
        val body = buildString {
            repeat(5_000) { index ->
                append("라인").append(index.toString().padStart(5, '0')).append("😀가나다라마바사아자차카타파하\n")
            }
        }
        var sourceCalls = 0
        var maxRequest = 0
        val reader = BoundedTxtWindowReader(body.length.toLong(), { start, count ->
            sourceCalls++
            maxRequest = maxOf(maxRequest, count)
            val end = minOf(body.length, start.toInt() + count)
            val raw = body.substring(start.toInt(), end)
            if (raw.lastOrNull()?.isHighSurrogate() == true) raw.dropLast(1) else raw
        })
        var offset = 0L
        while (offset < body.length) {
            val chunk = reader.read(offset, 1_024)
            assertTrue(chunk.isNotEmpty())
            assertFalse(chunk.first().isLowSurrogate())
            assertFalse(chunk.last().isHighSurrogate())
            assertEquals(body.substring(offset.toInt(), offset.toInt() + chunk.length), chunk)
            var step = minOf(350L, chunk.length.toLong())
            if (offset + step < body.length && body[(offset + step).toInt()].isLowSurrogate()) step++
            offset += step
        }
        assertTrue("Expected bounded chunk size", maxRequest <= BoundedTxtWindowReader.MAX_WINDOW_UNITS)
        val expectedMaxReads = (body.length / BoundedTxtWindowReader.MAX_WINDOW_UNITS) + 3
        assertTrue("Expected <= $expectedMaxReads source reads, got $sourceCalls", sourceCalls <= expectedMaxReads)

        // Slicing directly onto a high surrogate trims 1 UTF-16 code unit without splitting the pair.
        val surrogateText = "가나다😀라"
        val surrogateReader = BoundedTxtWindowReader(surrogateText.length.toLong(), { start, count ->
            surrogateText.substring(start.toInt(), minOf(surrogateText.length, start.toInt() + count))
        }, chunkUnits = 16)
        assertEquals("가나다", surrogateReader.read(0L, 4))
        assertEquals("가나다😀", surrogateReader.read(0L, 5))
    }

    @Test fun serializedPageIndexCancelsPreviousRunBeforeStartingReplacement() = runBlocking {
        val mutex = Mutex()
        val firstEntered = CompletableDeferred<Unit>()
        val allowFirstStep = CompletableDeferred<Unit>()
        val active = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        val first = launch(Dispatchers.Default) {
            mutex.withLock {
                val now = active.incrementAndGet()
                maxConcurrent.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                try {
                    TxtPageIndex.build(20) { offset ->
                        if (offset == 0L) {
                            firstEntered.complete(Unit)
                            allowFirstStep.await()
                        }
                        offset + 10L
                    }
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        firstEntered.await()
        val replacement = launch(Dispatchers.Default) {
            val index = mutex.withLock {
                val now = active.incrementAndGet()
                maxConcurrent.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                try {
                    withContext(Dispatchers.Default) {
                        TxtPageIndex.build(30) { offset -> minOf(30L, offset + 10L) }
                    }
                } finally {
                    active.decrementAndGet()
                }
            }
            assertEquals(3, index.total)
            assertArrayEquals(longArrayOf(0L, 10L, 20L), index.pageStarts())
        }
        first.cancel()
        first.join()
        replacement.join()
        assertTrue(first.isCancelled)
        assertEquals(1, maxConcurrent.get())
    }
}
