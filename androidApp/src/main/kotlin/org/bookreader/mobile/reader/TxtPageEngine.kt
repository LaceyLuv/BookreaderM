package org.bookreader.mobile.reader

import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class TxtPageRange(val start: Long, val end: Long) {
    init { require(start >= 0 && end > start) }
}
data class TxtMeasuredPage(val range: TxtPageRange, val text: String, val lines: Int)

/** One engine per complete layout key. Only 128 local boundaries and one bounded window are retained. */
class TxtPageEngine(
    private val length: Long,
    private val heightPx: Int,
    private val read: suspend (Long, Int) -> String,
    private val measure: (String) -> TextLayoutResult,
) {
    private val ranges = object : LinkedHashMap<Long, TxtPageRange>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TxtPageRange>?) = size > 128
    }

    suspend fun next(start: Long): TxtMeasuredPage {
        require(start in 0 until length && heightPx > 0)
        ranges[start]?.let { range -> return render(range) }
        var budget = 16_384
        while (true) {
            currentCoroutineContext().ensureActive()
            val text = read(start, budget)
            check(text.isNotEmpty())
            val layout = measure(text)
            val eof = start + text.length == length
            var lines = 0
            for (line in 0 until layout.lineCount) {
                // The last line of a partial window may continue in the next read.
                if (!eof && line == layout.lineCount - 1 && !text.endsWith('\n')) break
                if (layout.getLineEnd(line) <= 0) continue
                if (layout.getLineBottom(line) > heightPx && lines > 0) break
                lines = line + 1
                if (layout.getLineBottom(line) > heightPx) break
            }
            if (lines > 0 && (eof || layout.getLineBottom(lines - 1) >= heightPx ||
                    lines < layout.lineCount - 1 || budget == 65_536)) {
                val end = start + layout.getLineEnd(lines - 1, visibleEnd = false)
                check(end > start)
                val range = TxtPageRange(start, end)
                ranges[start] = range
                return render(range)
            }
            check(budget < 65_536) { "Text layout exceeds bounded page window" }
            budget *= 2
        }
    }

    suspend fun previous(end: Long): TxtMeasuredPage {
        require(end in 1..length)
        ranges.values.firstOrNull { it.end == end }?.let { return render(it) }
        // A large jump starts a new local run. Measure only a bounded suffix for its predecessor.
        var start = (end - 16_384).coerceAtLeast(0)
        var text = try { read(start, (end - start).toInt()) }
        catch (_: IllegalArgumentException) { start++; read(start, (end - start).toInt()) }
        check(text.isNotEmpty() && start + text.length == end)
        val layout = measure(text)
        var last = layout.lineCount - 1
        while (last > 0 && layout.getLineStart(last) == text.length) last--
        var first = last
        while (first > 0 && layout.getLineBottom(last) - layout.getLineTop(first - 1) <= heightPx) first--
        var offset = layout.getLineStart(first)
        // Re-measure the suffix with the exact same origin/style used for display.
        while (true) {
            currentCoroutineContext().ensureActive()
            val suffix = text.substring(offset)
            val measured = measure(suffix)
            var finalLine = measured.lineCount - 1
            while (finalLine > 0 && measured.getLineStart(finalLine) == suffix.length) finalLine--
            if (measured.getLineBottom(finalLine) <= heightPx || finalLine == 0) break
            offset += measured.getLineStart(1)
        }
        val range = TxtPageRange(start + offset, end)
        ranges[range.start] = range
        return render(range)
    }

    private suspend fun render(range: TxtPageRange): TxtMeasuredPage {
        val text = read(range.start, (range.end - range.start).toInt())
        check(text.length.toLong() == range.end - range.start)
        val layout = measure(text)
        var lines = layout.lineCount
        while (lines > 1 && layout.getLineStart(lines - 1) == text.length) lines--
        return TxtMeasuredPage(range, text, lines)
    }
}
