package org.bookreader.mobile.reader

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only UTF-16 boundaries are retained (at most 2 MiB), never whole-book text/layouts. */
class TxtPageIndex private constructor(private val starts: LongArray) {
    val total: Int get() = starts.size
    internal fun pageStarts(): LongArray = starts.copyOf()
    fun pageAt(offset: Long): Int {
        val found = starts.binarySearch(offset)
        return (if (found >= 0) found else -found - 2).coerceIn(0, starts.lastIndex) + 1
    }
    companion object {
        suspend fun build(length: Long, next: suspend (Long) -> Long): TxtPageIndex {
            require(length > 0)
            var starts = LongArray(1024)
            var count = 0
            var offset = 0L
            while (offset < length) {
                currentCoroutineContext().ensureActive()
                check(count < 250_000) { "Page index limit" }
                if (count == starts.size) starts = starts.copyOf(minOf(starts.size * 2, 250_000))
                starts[count++] = offset
                val end = next(offset)
                check(end > offset && end <= length)
                offset = end
                yield()
            }
            return TxtPageIndex(starts.copyOf(count))
        }
    }
}

/** Bounded rolling UTF-16 read window (at most 65,536 code units) for sequential page counting. */
internal class BoundedTxtWindowReader(
    private val length: Long,
    private val source: suspend (Long, Int) -> String,
    private val chunkUnits: Int = MAX_WINDOW_UNITS,
) {
    private var windowStart = 0L
    private var window = ""

    init {
        require(length >= 0)
        require(chunkUnits in 1..MAX_WINDOW_UNITS)
    }

    suspend fun read(start: Long, maxUnits: Int): String {
        require(maxUnits in 1..MAX_WINDOW_UNITS && start in 0..length)
        val needed = minOf(maxUnits.toLong(), length - start).toInt()
        if (needed == 0) return ""
        val windowEnd = windowStart + window.length
        val coversEof = windowEnd == length
        val covered = start >= windowStart && (start + needed <= windowEnd || (coversEof && start < windowEnd))
        if (!covered) {
            val fetch = minOf(maxOf(chunkUnits, needed).toLong(), length - start).toInt()
            windowStart = start
            window = source(start, fetch)
            check(window.isNotEmpty())
        }
        val localStart = (start - windowStart).toInt()
        val available = minOf(needed, window.length - localStart)
        var end = localStart + available
        if (end > localStart && window[end - 1].isHighSurrogate()) end--
        return window.substring(localStart, end)
    }

    companion object {
        const val MAX_WINDOW_UNITS = 65_536
    }
}

@Composable
fun txtPageLabel(controller: TxtReaderController, state: TxtReaderState, style: TextStyle, viewport: IntSize): String {
    val resolver = LocalFontFamilyResolver.current
    val font = resolver.resolve(style.fontFamily, fontWeight = style.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal).value
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val length = state.cache.committedLength.takeIf { state.cache.complete } ?: 0L
    val countMutex = remember(controller) { Mutex() }
    val result by produceState<Pair<TxtPageIndex?, Boolean>>(null to false, controller,
        listOf(length, style, viewport, density, direction, font)) {
        withContext(Dispatchers.Main.immediate) { value = null to false }
        if (length == 0L || viewport.width <= 0 || viewport.height <= 0) return@produceState
        try {
            // Serialize per controller so rapid typography changes cancel cleanly instead of
            // stacking overlapping background measurers, and reuse one bounded sequential window.
            val index = countMutex.withLock {
                currentCoroutineContext().ensureActive()
                withContext(Dispatchers.Default) {
                    val measurer = TextMeasurer(resolver, density, direction, cacheSize = 0)
                    val reader = BoundedTxtWindowReader(length, controller::range)
                    val engine = TxtPageEngine(length, viewport.height, reader::read) { text ->
                        measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = viewport.width))
                    }
                    TxtPageIndex.build(length) { engine.nextRange(it).end }
                }
            }
            withContext(Dispatchers.Main.immediate) { value = index to false }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { withContext(Dispatchers.Main.immediate) { value = null to true } }
    }
    val index = result.first
    return when {
        index != null -> "${index.pageAt(state.stableOffset ?: state.targetOffset)} / ${index.total} 쪽"
        result.second -> "쪽수 계산 불가"
        else -> "쪽수 계산 중"
    }
}
