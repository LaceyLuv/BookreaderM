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

/** Only UTF-16 boundaries are retained (at most 2 MiB), never whole-book text/layouts. */
class TxtPageIndex private constructor(private val starts: LongArray) {
    val total: Int get() = starts.size
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

@Composable
fun txtPageLabel(controller: TxtReaderController, state: TxtReaderState, style: TextStyle, viewport: IntSize): String {
    val resolver = LocalFontFamilyResolver.current
    val font = resolver.resolve(style.fontFamily, fontWeight = style.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal).value
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val length = state.cache.committedLength.takeIf { state.cache.complete } ?: 0L
    val result by produceState<Pair<TxtPageIndex?, Boolean>>(null to false, controller,
        listOf(length, style, viewport, density, direction, font)) {
        value = null to false
        if (length == 0L || viewport.width <= 0 || viewport.height <= 0) return@produceState
        try {
            // Dedicated measurer/engine: counting never evicts the interactive page window.
            val index = withContext(Dispatchers.Default) {
                val measurer = TextMeasurer(resolver, density, direction, cacheSize = 0)
                val engine = TxtPageEngine(length, viewport.height, controller::range) { text ->
                    measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = viewport.width))
                }
                TxtPageIndex.build(length) { engine.next(it).range.end }
            }
            value = index to false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null to true }
    }
    val index = result.first
    return when {
        index != null -> "${index.pageAt(state.stableOffset ?: state.targetOffset)} / ${index.total} 쪽"
        result.second -> "쪽수 계산 불가"
        else -> "쪽수 계산 중"
    }
}
