package org.bookreader.mobile.comic

import java.io.File
import java.util.Locale
import org.bookreader.mobile.epub.BoundedZip

/** Version 1 orders full path segments without locale, integer conversion, or ZIP record order. */
object ComicNaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val aa = a.split('/'); val bb = b.split('/')
        for (i in 0 until minOf(aa.size, bb.size)) {
            val result = segment(aa[i], bb[i])
            if (result != 0) return result
        }
        return aa.size.compareTo(bb.size).takeIf { it != 0 } ?: a.compareTo(b)
    }
    private fun segment(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            if (a[i] in '0'..'9' && b[j] in '0'..'9') {
                val startA = i; val startB = j
                while (i < a.length && a[i] in '0'..'9') i++
                while (j < b.length && b[j] in '0'..'9') j++
                var significantA = startA; var significantB = startB
                while (significantA < i - 1 && a[significantA] == '0') significantA++
                while (significantB < j - 1 && b[significantB] == '0') significantB++
                val length = (i - significantA).compareTo(j - significantB)
                if (length != 0) return length
                val digits = a.substring(significantA, i).compareTo(b.substring(significantB, j))
                if (digits != 0) return digits
                val rawLength = (i - startA).compareTo(j - startB)
                if (rawLength != 0) return rawLength
            } else {
                val result = a[i].compareTo(b[j])
                if (result != 0) return result
                i++; j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }
}

data class ComicManifest(val sourceSha256: String, val entryKeys: List<String>, val legacyNames: Boolean,
    val sortVersion: Int = 1) {
    init {
        require(sourceSha256.length == 64 && sourceSha256.all { it in '0'..'9' || it in 'a'..'f' })
        require(sortVersion == 1 && entryKeys.isNotEmpty() && entryKeys.size <= 20_000)
    }
    fun indexOf(entryKey: String): Int = entryKeys.indexOf(entryKey)
    companion object {
        fun inspect(file: File, sourceSha256: String, checkCancellation: () -> Unit = {}): ComicManifest {
            val archive = BoundedZip(file, 2L * 1024 * 1024 * 1024, checkCancellation)
            val images = archive.entries.values.filter { entry ->
                checkCancellation()
                val segments = entry.key.split('/')
                segments.none { it.startsWith('.') || it == "__MACOSX" } &&
                    entry.key.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("jpg", "jpeg", "png", "webp")
            }
            require(images.isNotEmpty()) { "No supported static images" }
            return ComicManifest(sourceSha256, images.map { it.key }.sortedWith(ComicNaturalOrder), images.any { it.legacyName })
        }
    }
}
