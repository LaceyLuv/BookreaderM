package org.bookreader.mobile.epub

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubResourceBudgetTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun archive(vararg entries: Pair<String, Int>): BoundedZip {
        val file = temporary.newFile()
        val chunk = ByteArray(16 * 1024) { 'a'.code.toByte() }
        ZipOutputStream(file.outputStream()).use { output ->
            for ((name, size) in entries) {
                output.putNextEntry(ZipEntry(name))
                var remaining = size
                while (remaining > 0) {
                    val length = minOf(remaining, chunk.size)
                    output.write(chunk, 0, length); remaining -= length
                }
                output.closeEntry()
            }
        }
        return BoundedZip(file)
    }

    @Test fun compressedChaptersCannotMultiplyTotalMarkupPastBudget() {
        val mib = 1024 * 1024
        val oversized = archive("one.xhtml" to 12 * mib, "two.xhtml" to 12 * mib, "three.xhtml" to 12 * mib)
        // Each entry individually passes the 16 MiB markup limit; their sum must fail before inflation.
        assertThrows(IllegalArgumentException::class.java) { EpubResourceBudget(oversized) }
        val images = EpubResourceBudget(archive("one.xhtml" to 12 * mib, "page.png" to 36 * mib))
        images.readingOrder(listOf("one.xhtml"))
        assertThrows(IllegalArgumentException::class.java) { images.readingOrder(List(3) { "one.xhtml" }) }
    }

    @Test fun protectedOutputAndTinyRepeatedSpineAreAlsoBounded() {
        val budget = EpubResourceBudget(archive("one.xhtml" to 100))
        budget.readingOrder(List(2048) { "one.xhtml" })
        assertThrows(IllegalArgumentException::class.java) { budget.readingOrder(List(2049) { "one.xhtml" }) }
        assertThrows(IllegalArgumentException::class.java) { budget.protectedMarkup("one.xhtml", 32 * 1024 * 1024 + 1) }
    }
}
