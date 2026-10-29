package org.bookreader.mobile.toc

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class TxtHeadingKind { EPISODE, CHAPTER, PROLOGUE, EPILOGUE }
data class TxtHeading(val offset: Long, val title: String, val kind: TxtHeadingKind)
data class TxtTocScan(val headings: List<TxtHeading>, val scannedUnits: Long, val truncated: Boolean)

/** Derived candidates in the existing canonical UTF-16 coordinates. Never rewrites the source. */
object TxtTocScanner {
    const val VERSION = 1
    const val WINDOW = 16_384
    const val MAX_HEADINGS = 5_000
    const val MAX_TITLE = 120
    private const val MAX_LINE = 256
    private val korean = Regex("^(?:제\\s*)?([0-9]{1,6})\\s*([화장회])(?:[ \\t:：.．\\-—·]+(.+))?$")
    private val english = Regex("^chapter[ \\t]+[0-9]{1,6}(?:[ \\t:：.\\-—]+(.+))?$", RegexOption.IGNORE_CASE)
    private val prologue = Regex("^(?:프롤로그|prologue)(?:[ \\t:：\\-—]+(.+))?$", RegexOption.IGNORE_CASE)
    private val epilogue = Regex("^(?:에필로그|epilogue)(?:[ \\t:：\\-—]+(.+))?$", RegexOption.IGNORE_CASE)

    suspend fun scan(length: Long, read: suspend (Long, Int) -> String): TxtTocScan {
        require(length >= 0)
        val headings = ArrayList<TxtHeading>()
        val line = StringBuilder(MAX_LINE)
        var lineStart = 0L
        var longLine = false
        var offset = 0L
        fun finishLine(): Boolean {
            if (longLine) return true
            val raw = line.toString()
            val title = raw.trim().replace('\t', ' ')
            val kind = recognize(title) ?: return true
            if (headings.size == MAX_HEADINGS) return false
            val leading = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            headings += TxtHeading(lineStart + leading, title, kind)
            return true
        }
        while (offset < length) {
            currentCoroutineContext().ensureActive()
            val count = minOf(WINDOW.toLong(), length - offset).toInt()
            val text = read(offset, count)
            check(text.isNotEmpty() && text.length <= count) { "Invalid canonical window" }
            for ((index, char) in text.withIndex()) {
                if (index % 1024 == 0) currentCoroutineContext().ensureActive()
                if (char == '\n') {
                    if (!finishLine()) return TxtTocScan(headings, offset + index + 1, true)
                    line.clear(); longLine = false; lineStart = offset + index + 1
                } else if (!longLine) {
                    if (line.length == MAX_LINE) { line.clear(); longLine = true }
                    else line.append(char)
                }
            }
            // The canonical reader may shorten a window to avoid splitting a surrogate pair.
            offset += text.length
        }
        currentCoroutineContext().ensureActive()
        val complete = finishLine()
        return TxtTocScan(headings, length, !complete)
    }

    private fun recognize(title: String): TxtHeadingKind? {
        if (title.length !in 1..MAX_TITLE || title.any { it.code < 32 }) return null
        // Prefer a missed candidate that can be added manually to treating prose as a heading.
        if (title.last() in ".!?。！？") return null
        val candidate = if (title.startsWith('[')) {
            val end = title.indexOf(']')
            if (end !in 1..32) return null
            title.substring(1, end) + " " + title.substring(end + 1).trim()
        } else title
        val clean = candidate.trim()
        korean.matchEntire(clean)?.let {
            return if (it.groupValues[2] == "장") TxtHeadingKind.CHAPTER else TxtHeadingKind.EPISODE
        }
        if (english.matches(clean)) return TxtHeadingKind.CHAPTER
        if (prologue.matches(clean)) return TxtHeadingKind.PROLOGUE
        if (epilogue.matches(clean)) return TxtHeadingKind.EPILOGUE
        return null
    }
}
