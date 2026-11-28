package org.bookreader.mobile.reader

import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TxtPageRange(val start: Long, val end: Long) {
    init { require(start >= 0 && end > start) }
}
data class TxtMeasuredPage(
    val range: TxtPageRange,
    val text: String,
    val lines: Int,
    val overflowsVertical: Boolean = false,
)

/** One engine per complete layout key. Only 128 local boundaries and 8 measured pages are retained. */
class TxtPageEngine(
    private val length: Long,
    private val heightPx: Int,
    private val read: suspend (Long, Int) -> String,
    private val measure: (String) -> TextLayoutResult,
) {
    private var adaptiveBudget = true
    private var hintBudget = MIN_BUDGET
    private val computeMutex = Mutex()
    private val cacheLock = Any()

    internal constructor(
        length: Long,
        heightPx: Int,
        read: suspend (Long, Int) -> String,
        adaptiveBudget: Boolean,
        measure: (String) -> TextLayoutResult,
    ) : this(length, heightPx, read, measure) {
        this.adaptiveBudget = adaptiveBudget
        this.hintBudget = if (adaptiveBudget) MIN_BUDGET else INITIAL_MAX_BUDGET
    }

    private val ranges = object : LinkedHashMap<Long, TxtPageRange>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TxtPageRange>?) = size > 128
    }

    private val pages = object : LinkedHashMap<Long, TxtMeasuredPage>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, TxtMeasuredPage>?) = size > MAX_CACHED_PAGES
    }

    /** Synchronous lock-safe lookup for zero-latency cached page turns on the UI thread. */
    fun peekPage(start: Long): TxtMeasuredPage? = synchronized(cacheLock) { pages[start] }

    /** Synchronous lock-safe lookup for a prefetched predecessor page ending at [end]. */
    fun peekPrevious(end: Long): TxtMeasuredPage? = synchronized(cacheLock) {
        pages.values.firstOrNull { it.range.end == end }
    }

    suspend fun next(start: Long): TxtMeasuredPage {
        peekPage(start)?.let { return it }
        return computeMutex.withLock {
            peekPage(start)?.let { return@withLock it }
            val (range, slice) = nextRangeWithSlice(start)
            val measured = if (slice != null) renderWithText(range, slice) else render(range)
            synchronized(cacheLock) { pages[range.start] = measured }
            measured
        }
    }

    suspend fun nextRange(start: Long): TxtPageRange = computeMutex.withLock {
        nextRangeWithSlice(start).first
    }

    private suspend fun nextRangeWithSlice(start: Long): Pair<TxtPageRange, String?> {
        require(start in 0 until length && heightPx > 0)
        synchronized(cacheLock) { ranges[start] }?.let { return it to null }
        // An arbitrary jump creates a new local run. Keeping overlapping runs could
        // give the same end two different predecessors and make Back drift.
        synchronized(cacheLock) {
            if (ranges.isNotEmpty() && ranges.values.none { it.end == start }) {
                ranges.clear()
                pages.clear()
            }
        }
        var budget = if (adaptiveBudget) hintBudget else INITIAL_MAX_BUDGET
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
                    lines < layout.lineCount - 1 || budget == MAX_BUDGET)) {
                val endOffset = layout.getLineEnd(lines - 1, visibleEnd = false)
                val end = start + endOffset
                check(end > start)
                val range = TxtPageRange(start, end)
                synchronized(cacheLock) { ranges[start] = range }
                hintBudget = nextPowerOfTwoBudget((end - start).toInt())
                return range to text.substring(0, endOffset)
            }
            check(budget < MAX_BUDGET) { "Text layout exceeds bounded page window" }
            budget *= 2
        }
    }

    private fun nextPowerOfTwoBudget(pageChars: Int): Int {
        val target = (pageChars.coerceAtLeast(1) * 2).coerceIn(MIN_BUDGET, INITIAL_MAX_BUDGET)
        var budget = MIN_BUDGET
        while (budget < target && budget < INITIAL_MAX_BUDGET) budget *= 2
        return budget
    }

    suspend fun previous(end: Long): TxtMeasuredPage {
        peekPrevious(end)?.let { return it }
        return computeMutex.withLock {
            peekPrevious(end)?.let { return@withLock it }
            require(end in 1..length)
            val cachedRange = synchronized(cacheLock) { ranges.values.firstOrNull { it.end == end } }
            if (cachedRange != null) {
                val measured = render(cachedRange)
                synchronized(cacheLock) { pages[cachedRange.start] = measured }
                return@withLock measured
            }
            synchronized(cacheLock) {
                if (ranges.isNotEmpty() && end !in ranges) {
                    ranges.clear()
                    pages.clear()
                }
            }
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
            var suffix: String
            var measuredLayout: TextLayoutResult
            while (true) {
                currentCoroutineContext().ensureActive()
                suffix = text.substring(offset)
                measuredLayout = measure(suffix)
                var finalLine = measuredLayout.lineCount - 1
                while (finalLine > 0 && measuredLayout.getLineStart(finalLine) == suffix.length) finalLine--
                if (measuredLayout.getLineBottom(finalLine) <= heightPx || finalLine == 0) break
                offset += measuredLayout.getLineStart(1)
            }
            val range = TxtPageRange(start + offset, end)
            val page = buildMeasuredPage(range, suffix, measuredLayout)
            synchronized(cacheLock) {
                ranges[range.start] = range
                pages[range.start] = page
            }
            page
        }
    }

    /** Warms up to two pages ahead and one page behind the currently visible page. */
    suspend fun prefetchAround(current: TxtMeasuredPage) {
        currentCoroutineContext().ensureActive()
        if (current.range.end < length) {
            val nextOne = next(current.range.end)
            currentCoroutineContext().ensureActive()
            if (nextOne.range.end < length) {
                next(nextOne.range.end)
            }
        }
        currentCoroutineContext().ensureActive()
        if (current.range.start > 0) {
            previous(current.range.start)
        }
    }

    private suspend fun render(range: TxtPageRange): TxtMeasuredPage {
        val text = read(range.start, (range.end - range.start).toInt())
        check(text.length.toLong() == range.end - range.start)
        return renderWithText(range, text)
    }

    private fun renderWithText(range: TxtPageRange, text: String): TxtMeasuredPage {
        check(text.length.toLong() == range.end - range.start)
        return buildMeasuredPage(range, text, measure(text))
    }

    private fun buildMeasuredPage(range: TxtPageRange, text: String, layout: TextLayoutResult): TxtMeasuredPage {
        var lines = layout.lineCount
        while (lines > 1 && layout.getLineStart(lines - 1) == text.length) lines--
        val overflows = lines > 0 && layout.getLineBottom(lines - 1) > heightPx
        return TxtMeasuredPage(range, text, lines, overflows)
    }

    companion object {
        private const val MIN_BUDGET = 1_024
        private const val INITIAL_MAX_BUDGET = 16_384
        private const val MAX_BUDGET = 65_536
        private const val MAX_CACHED_PAGES = 8
    }
}
