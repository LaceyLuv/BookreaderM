package org.bookreader.mobile.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedZipTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun zip(vararg entries: Pair<String, String>): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { out -> entries.forEach { (name, text) ->
            out.putNextEntry(ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry()
        } }
    }
    @Test fun boundedReadPreservesUnicodeIdentityAndRejectsActualOverflowAndCancellation() {
        val file = zip("본문/장01.xhtml" to "한글😀".repeat(1000))
        val archive = BoundedZip(file)
        assertEquals("한글😀".repeat(1000), archive.read("본문/장01.xhtml", 16384).toString(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { archive.read("본문/장01.xhtml", 10) }
        var cancel = false
        val cancellable = BoundedZip(file, checkCancellation = { if (cancel) throw InterruptedException() })
        cancel = true
        assertThrows(InterruptedException::class.java) { cancellable.read("본문/장01.xhtml", 16384) }
    }
    @Test fun traversalDuplicateNormalizedNamesAndEncryptedFlagsFailBeforeInflation() {
        for (name in listOf("../secret", "/absolute", "a\\b", "a/%2e%2e/b", "a//b", "C:book"))
            assertThrows(IllegalArgumentException::class.java) { BoundedZip(zip(name to "unsafe")) }
        assertThrows(IllegalArgumentException::class.java) { BoundedZip(zip("é.txt" to "a", "e\u0301.txt" to "b")) }
        val encrypted = zip("book" to "data")
        val bytes = encrypted.readBytes()
        for (i in 0 until bytes.size - 10) if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() && bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte()) bytes[i + 8] = (bytes[i + 8].toInt() or 1).toByte()
        encrypted.writeBytes(bytes)
        assertThrows(IllegalArgumentException::class.java) { BoundedZip(encrypted) }
    }
    @Test fun crcCorruptionAndForgedDeclaredSizeCannotReturnPartialContent() {
        for (field in listOf(16, 24)) {
            val file = zip("body" to "x".repeat(1000)); val bytes = file.readBytes()
            val directory = (0 until bytes.size - 46).first { bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4b.toByte() && bytes[it + 2] == 1.toByte() && bytes[it + 3] == 2.toByte() }
            bytes[directory + field] = (bytes[directory + field].toInt() xor 1).toByte(); file.writeBytes(bytes)
            val archive = BoundedZip(file)
            assertThrows(IllegalArgumentException::class.java) { archive.read("body", 2000) }
        }
    }
}
