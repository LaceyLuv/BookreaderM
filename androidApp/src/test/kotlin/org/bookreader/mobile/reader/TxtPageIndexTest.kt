package org.bookreader.mobile.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
}
