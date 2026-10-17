package org.bookreader.mobile.comic

import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicManifestTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun zip(names: List<String>): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { out -> names.forEach { name ->
            out.putNextEntry(ZipEntry(name)); out.write(byteArrayOf(1)); out.closeEntry()
        } }
    }
    @Test fun reorderedArchivesAndLocalesKeepFullPathIdentityAndNaturalNumbers() {
        val names = listOf("한글/10.png", "한글/2.png", "日本/01.jpg", "日本/1.jpg", "日本/001.jpg",
            "other/1.jpg", "A/999999999999999999999999999999.webp", "A/1000000000000000000000000000000.webp",
            "__MACOSX/book.png", ".hidden.png", "folder/.metadata/page.jpg", "notes.txt")
        val expected = listOf("A/999999999999999999999999999999.webp", "A/1000000000000000000000000000000.webp",
            "other/1.jpg", "日本/1.jpg", "日本/01.jpg", "日本/001.jpg", "한글/2.png", "한글/10.png")
        val initial = Locale.getDefault()
        try {
            for (locale in listOf(Locale.KOREAN, Locale.JAPANESE, Locale.forLanguageTag("tr-TR"))) {
                Locale.setDefault(locale)
                for (order in listOf(names, names.reversed())) {
                    val manifest = ComicManifest.inspect(zip(order), "a".repeat(64))
                    assertEquals(expected, manifest.entryKeys)
                    assertEquals(3, manifest.indexOf("日本/1.jpg"))
                    assertEquals(-1, manifest.indexOf("missing.jpg"))
                }
            }
        } finally { Locale.setDefault(initial) }
    }
    @Test fun unsupportedArchivesAndCancelledInspectionNeverProduceManifest() {
        assertThrows(IllegalArgumentException::class.java) { ComicManifest.inspect(zip(listOf("nested.zip", "notes.txt")), "a".repeat(64)) }
        assertThrows(InterruptedException::class.java) { ComicManifest.inspect(zip(listOf("1.png")), "a".repeat(64)) { throw InterruptedException() } }
    }
}
