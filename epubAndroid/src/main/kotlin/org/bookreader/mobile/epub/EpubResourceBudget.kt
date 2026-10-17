package org.bookreader.mobile.epub

import java.util.Locale

/** Bound total parser work and the bytes used by Readium's eager positions service. */
internal class EpubResourceBudget(archive: BoundedZip) {
    private val sizes = archive.entries.values.filter {
        it.key.substringAfterLast('.', "").lowercase(Locale.ROOT) in MARKUP
    }.associate { it.key to it.size }.toMutableMap()

    private var total = sizes.values.sum()
    init { require(total <= MAX_TOTAL) { "EPUB total markup limit" } }

    fun protectedMarkup(key: String, size: Int) {
        val previous = requireNotNull(sizes[key])
        total += size - previous
        sizes[key] = size.toLong()
        require(total <= MAX_TOTAL) { "EPUB protected markup limit" }
    }

    fun readingOrder(keys: List<String>) {
        require(keys.size in 1..2048) { "EPUB reading order limit" }
        // Repeated spine references also cost positions; count every occurrence.
        require(keys.sumOf { requireNotNull(sizes[it]) } <= MAX_TOTAL) { "EPUB positions input limit" }
    }

    companion object {
        private const val MAX_TOTAL = 32L * 1024 * 1024
        private val MARKUP = setOf("xml", "opf", "ncx", "html", "xhtml", "htm", "svg", "css")
    }
}
