package org.bookreader.mobile.annotation

import org.bookreader.mobile.repository.ProgressSession

enum class HighlightColor { YELLOW, GREEN, BLUE, PINK }
data class TxtQuote(val start: Long, val end: Long, val text: String)
data class TxtAnnotation(val id: String, val quote: TxtQuote, val color: HighlightColor, val note: String, val changeId: String)
data class TxtHighlightSpan(val start: Int, val end: Int, val color: HighlightColor)
sealed interface TxtAnnotationsRead {
    data class Ready(val entries: List<TxtAnnotation>) : TxtAnnotationsRead
    data object Error : TxtAnnotationsRead
}
enum class AnnotationWrite { SAVED, CONFLICT, ERROR }
interface TxtAnnotationsAccess {
    suspend fun load(bookId: String, revision: String, length: Long): TxtAnnotationsRead
    suspend fun save(session: ProgressSession, length: Long, id: String,
        expected: TxtAnnotation?, replacement: TxtAnnotation?): AnnotationWrite
}

/** Coordinates are canonical UTF-16, never a quote search or a rendered page number. */
object TxtAnnotations {
    const val VERSION = 1
    const val MAX_ENTRIES = 500
    const val MAX_QUOTE = 4096
    const val MAX_NOTE = 2000

    fun validUnicode(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (c.isLowSurrogate()) return false
            if (c.isHighSurrogate() && (i == text.length || !text[i++].isLowSurrogate())) return false
        }
        return true
    }
    fun validNote(note: String): Boolean = note.length <= MAX_NOTE && validUnicode(note) &&
        note.none { it.code < 32 && it != '\n' && it != '\t' }

    fun select(base: Long, window: String, first: Int, last: Int, length: Long): TxtQuote? {
        if (window.length > MAX_QUOTE || base < 0 || length < window.length || base > length - window.length ||
            first !in 0..window.length || last !in 0..window.length || first == last || !validUnicode(window)) return null
        val start = minOf(first, last); val end = maxOf(first, last)
        val text = window.substring(start, end)
        if (text.isBlank() || !validUnicode(text)) return null
        return TxtQuote(base + start, base + end, text)
    }
    fun validate(entry: TxtAnnotation, length: Long) {
        require(entry.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && entry.changeId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        val quote = entry.quote
        require(quote.start >= 0 && quote.end > quote.start && quote.end <= length &&
            quote.end - quote.start == quote.text.length.toLong() && quote.text.length <= MAX_QUOTE &&
            quote.text.isNotBlank() && validUnicode(quote.text) && validNote(entry.note))
    }

    /** Clip only the exact stored range, and check its visible source bytes again before painting. */
    fun spans(base: Long, text: String, entries: List<TxtAnnotation>): List<TxtHighlightSpan> {
        require(base >= 0 && base <= Long.MAX_VALUE - text.length && entries.size <= MAX_ENTRIES)
        return entries.mapNotNull { entry ->
            val quote = entry.quote
            if (quote.start < 0 || quote.end <= quote.start) return@mapNotNull null
            val start = maxOf(base, quote.start); val end = minOf(base + text.length, quote.end)
            if (end <= start || quote.end - quote.start != quote.text.length.toLong()) return@mapNotNull null
            val localStart = (start - base).toInt(); val localEnd = (end - base).toInt()
            if (text.substring(localStart, localEnd) != quote.text.substring((start - quote.start).toInt(), (end - quote.start).toInt())) null
            else TxtHighlightSpan(localStart, localEnd, entry.color)
        }
    }
}
