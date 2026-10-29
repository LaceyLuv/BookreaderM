package org.bookreader.mobile.toc

import org.bookreader.mobile.repository.ProgressSession

/** A user edit, separate from the reproducible automatic index and all reading records. */
data class TxtTocOverride(val offset: Long, val title: String?, val excluded: Boolean, val changeId: String)
data class TxtTocEntry(val offset: Long, val title: String, val edited: Boolean, val automatic: Boolean)
sealed interface TxtTocRead {
    data class Ready(val edits: List<TxtTocOverride>) : TxtTocRead
    data object Error : TxtTocRead
}
enum class TxtTocWrite { SAVED, CONFLICT, ERROR }
interface TxtTocAccess {
    suspend fun load(bookId: String, revision: String, length: Long): TxtTocRead
    suspend fun save(session: ProgressSession, length: Long, offset: Long,
        expected: TxtTocOverride?, replacement: TxtTocOverride?): TxtTocWrite
}

object TxtToc {
    const val MAX_EDITS = 1_000
    fun validTitle(value: String): Boolean {
        if (value.length !in 1..TxtTocScanner.MAX_TITLE || value != value.trim() || value.any { it.code < 32 }) return false
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isLowSurrogate()) return false
            if (c.isHighSurrogate() && (i == value.length || !value[i++].isLowSurrogate())) return false
        }
        return true
    }
    fun validate(edit: TxtTocOverride, length: Long) {
        require(edit.offset >= 0 && edit.offset < length && edit.changeId.length in 1..128)
        require(if (edit.excluded) edit.title == null else edit.title?.let(::validTitle) == true)
    }
    fun merge(headings: List<TxtHeading>, edits: List<TxtTocOverride>): List<TxtTocEntry> {
        require(headings.size <= TxtTocScanner.MAX_HEADINGS && edits.size <= MAX_EDITS)
        val entries = headings.associate { it.offset to TxtTocEntry(it.offset, it.title, false, true) }.toMutableMap()
        for (edit in edits) {
            if (edit.excluded) entries.remove(edit.offset)
            else entries[edit.offset] = TxtTocEntry(edit.offset, requireNotNull(edit.title), true, entries[edit.offset]?.automatic == true)
        }
        return entries.values.sortedBy { it.offset }
    }
}
