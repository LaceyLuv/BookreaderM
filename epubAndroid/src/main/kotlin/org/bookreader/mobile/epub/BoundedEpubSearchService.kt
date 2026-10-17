package org.bookreader.mobile.epub

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.*
import org.readium.r2.shared.publication.services.search.*
import org.readium.r2.shared.util.DebugError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.Container
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.content.DefaultResourceContentExtractorFactory

/** Literal KMP cursor: bounded result pages, no regex or whole-chapter match array. */
internal class LiteralMatchCursor(private val text: String, private val query: String) {
    init {
        require(query.isNotEmpty() && query.length <= 256)
        for (index in query.indices) {
            if (query[index].isHighSurrogate()) require(index + 1 < query.length && query[index + 1].isLowSurrogate())
            if (query[index].isLowSurrogate()) require(index > 0 && query[index - 1].isHighSurrogate())
        }
    }
    private val failure = IntArray(query.length).also { table ->
        var matched = 0
        for (i in 1 until query.length) {
            while (matched > 0 && query[i] != query[matched]) matched = table[matched - 1]
            if (query[i] == query[matched]) matched++
            table[i] = matched
        }
    }
    private var offset = 0
    private var matched = 0
    val exhausted: Boolean get() = offset == text.length
    fun next(check: () -> Unit): List<IntRange> {
        val ranges = ArrayList<IntRange>(50)
        while (offset < text.length && ranges.size < 50) {
            if (offset % 16384 == 0) check()
            val char = text[offset++]
            while (matched > 0 && char != query[matched]) matched = failure[matched - 1]
            if (char == query[matched]) matched++
            if (matched == query.length) {
                ranges += (offset - query.length) until offset
                matched = failure[matched - 1]
            }
        }
        check()
        return ranges
    }
}

/** Public Readium SearchService adapter. Unsupported huge chapters fail explicitly before extraction. */
@OptIn(ExperimentalReadiumApi::class)
class BoundedEpubSearchService(private val manifest: Manifest, private val container: Container<Resource>) : SearchService {
    override val options = SearchService.Options(caseSensitive = true, diacriticSensitive = true,
        wholeWord = false, exact = true, regularExpression = false)
    override suspend fun search(query: String, options: SearchService.Options?): SearchIterator {
        require(query.isNotBlank() && query.length <= 256)
        require(options?.regularExpression != true && options?.wholeWord != true && options?.caseSensitive != false && options?.diacriticSensitive != false && options?.exact != false)
        return Cursor(query)
    }
    private inner class Cursor(private val query: String) : SearchIterator {
        private val closed = AtomicBoolean(false)
        private var resourceIndex = -1
        private var matches: LiteralMatchCursor? = null
        private var text = ""
        private var base: Locator? = null
        private var emitted = 0
        private var ended = false
        private var elapsedNanos = 0L
        override val resultCount: Int? get() = emitted.takeIf { ended }
        override fun close() { closed.set(true); matches = null; text = ""; base = null }
        override suspend fun next(): SearchTry<LocatorCollection?> = withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            val context = currentCoroutineContext()
            fun checkpoint() {
                context.ensureActive()
                check(!closed.get()) { "Search closed" }
                check(elapsedNanos + System.nanoTime() - start < 30_000_000_000L) { "Search time limit reached" }
            }
            try {
                checkpoint()
                if (ended) return@withContext Try.success(null)
                if (emitted >= 1000) error("Search result limit reached")
                while (true) {
                    checkpoint()
                    val cursor = matches
                    if (cursor != null) {
                        val ranges = cursor.next(::checkpoint)
                        if (ranges.isNotEmpty()) {
                            val locators = ranges.take(1000 - emitted).map { range ->
                                val original = requireNotNull(base)
                                fun boundary(index: Int): Int = if (index in 1 until text.length && text[index].isLowSurrogate() && text[index - 1].isHighSurrogate()) index - 1 else index
                                original.copy(locations = original.locations.copy(progression = range.first.toDouble() / text.length),
                                    text = Locator.Text(highlight = text.substring(range),
                                        before = text.substring(boundary((range.first - 160).coerceAtLeast(0)), range.first),
                                        after = text.substring(range.last + 1, boundary((range.last + 161).coerceAtMost(text.length)))))
                            }
                            emitted += locators.size
                            return@withContext Try.success(LocatorCollection(locators = locators))
                        }
                    }
                    matches = null; text = ""; base = null
                    resourceIndex++
                    if (resourceIndex == manifest.readingOrder.size) { ended = true; return@withContext Try.success(null) }
                    val link = manifest.readingOrder[resourceIndex]
                    val resource = container[link.url()] ?: error("Missing search resource")
                    try {
                        val length = resource.length().getOrElse { error("Unreadable search resource") }
                        require(length <= 2 * 1024 * 1024) { "Search chapter limit reached" }
                        val extractor = DefaultResourceContentExtractorFactory().createExtractor(resource, requireNotNull(link.mediaType))
                            ?: error("Unsupported search resource")
                        text = extractor.extractText(resource).getOrElse { error("Unreadable search text") }
                        require(text.length <= 2 * 1024 * 1024)
                        base = requireNotNull(manifest.locatorFromLink(link))
                        matches = LiteralMatchCursor(text, query)
                    } finally { resource.close() }
                }
                @Suppress("UNREACHABLE_CODE") Try.success(null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Try.failure(SearchError.Engine(DebugError("Search input, resource, or work limit"))) }
            finally { elapsedNanos += System.nanoTime() - start }
        }
    }
}
