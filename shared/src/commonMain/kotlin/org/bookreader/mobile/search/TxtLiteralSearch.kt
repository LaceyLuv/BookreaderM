package org.bookreader.mobile.search

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class TxtSearchBatch(val offsets: List<Long>, val nextOffset: Long?, val scannedUntil: Long)

/** Literal UTF-16 search. KMP carries the query prefix across bounded windows without losing overlaps. */
object TxtLiteralSearch {
    const val MAX_QUERY = 256
    const val BATCH_SIZE = 50
    const val WINDOW = 16_384

    suspend fun find(query: String, ignoreAsciiCase: Boolean, length: Long, start: Long = 0,
        read: suspend (Long, Int) -> String): TxtSearchBatch {
        require(query.isNotEmpty() && query.length <= MAX_QUERY)
        require(start in 0..length)
        // A query cannot name half of a Unicode scalar. No normalization or expanding case fold is used.
        var position = 0
        while (position < query.length) {
            val char = query[position++]
            require(!char.isLowSurrogate())
            if (char.isHighSurrogate()) { require(position < query.length && query[position].isLowSurrogate()); position++ }
        }
        fun fold(char: Char): Char = if (ignoreAsciiCase && char in 'A'..'Z') (char.code + 32).toChar() else char
        val pattern = query.map(::fold)
        val prefix = IntArray(pattern.size)
        for (i in 1 until pattern.size) {
            var previous = prefix[i - 1]
            while (previous > 0 && pattern[i] != pattern[previous]) previous = prefix[previous - 1]
            if (pattern[i] == pattern[previous]) previous++
            prefix[i] = previous
        }
        val found = ArrayList<Long>(BATCH_SIZE)
        var offset = start
        var matched = 0
        while (offset < length) {
            currentCoroutineContext().ensureActive()
            val text = read(offset, minOf(WINDOW.toLong(), length - offset).toInt())
            check(text.isNotEmpty() && text.length <= WINDOW && offset + text.length <= length)
            for ((index, raw) in text.withIndex()) {
                if (index % 1024 == 0) currentCoroutineContext().ensureActive()
                val char = fold(raw)
                while (matched > 0 && char != pattern[matched]) matched = prefix[matched - 1]
                if (char == pattern[matched]) matched++
                if (matched == pattern.size) {
                    val hit = offset + index + 1 - pattern.size
                    found += hit
                    matched = prefix[matched - 1]
                    if (found.size == BATCH_SIZE) {
                        val next = hit + if (query.first().isHighSurrogate()) 2 else 1
                        return TxtSearchBatch(found, next.takeIf { it <= length - query.length }, offset + index + 1)
                    }
                }
            }
            offset += text.length
        }
        return TxtSearchBatch(found, null, length)
    }
}
