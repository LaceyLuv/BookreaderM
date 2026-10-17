package org.bookreader.mobile.epub

import org.junit.Assert.*
import org.junit.Test

class LiteralMatchCursorTest {
    @Test fun manyOverlappingMatchesRemainPagedAndKeepUtf16Ranges() {
        val text = "😀" + "a".repeat(10000) + "한글😀"
        val cursor = LiteralMatchCursor(text, "aaa")
        var count = 0; var last = -1
        while (!cursor.exhausted) {
            val batch = cursor.next {}
            assertTrue(batch.size <= 50)
            for (range in batch) { assertEquals("aaa", text.substring(range)); assertTrue(range.first > last); last = range.first; count++ }
        }
        assertEquals(9998, count)
        assertEquals(listOf(10002..10005), LiteralMatchCursor(text, "한글😀").next {})
    }
    @Test fun absentRepeatedPrefixSearchChecksCancellationWithinBoundedWindows() {
        val cursor = LiteralMatchCursor("a".repeat(2 * 1024 * 1024), "a".repeat(255) + "b")
        var calls = 0
        assertThrows(InterruptedException::class.java) { cursor.next { if (++calls == 3) throw InterruptedException() } }
        assertEquals(3, calls)
    }
}
