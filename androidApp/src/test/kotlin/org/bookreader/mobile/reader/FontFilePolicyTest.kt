package org.bookreader.mobile.reader

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class FontFilePolicyTest {
    @Test fun bundledKoreanFontsHaveValidBoundedSfntTables() {
        for (name in listOf("NanumGothic-Regular.ttf", "NanumMyeongjo-Regular.ttf"))
            FontFilePolicy.validate(File("src/main/assets/reader-fonts/$name"))
        FontFilePolicy.validate(File("src/debug/assets/font-fixtures/Fixture.otf"))
    }
    @Test fun rejectsDisguisedTruncatedAndOversizedFonts() = temporary { file ->
        for (bytes in listOf("not a font file".toByteArray(), byteArrayOf(0, 1, 0, 0))) {
            file.writeBytes(bytes)
            assertThrows(IllegalArgumentException::class.java) { FontFilePolicy.validate(file) }
        }
        RandomAccessFile(file, "rw").use { it.setLength(FontFilePolicy.MAX_BYTES + 1) }
        assertThrows(IllegalArgumentException::class.java) { FontFilePolicy.validate(file) }
    }
    @Test fun rejectsEscapingOverlappingAndDuplicateTableDirectories() = temporary { file ->
        val original = File("src/main/assets/reader-fonts/NanumGothic-Regular.ttf").readBytes()
        for (mutation in 0..3) {
            file.writeBytes(original)
            RandomAccessFile(file, "rw").use { input -> when (mutation) {
                0 -> { input.seek(20); input.writeInt(Int.MAX_VALUE) }
                1 -> { input.seek(12); val tag = input.readInt(); input.seek(28); input.writeInt(tag) }
                2 -> { input.seek(4); input.writeShort(129) }
                3 -> { input.seek(20); val start = input.readInt(); input.seek(36); input.writeInt(start) }
            } }
            assertThrows(IllegalArgumentException::class.java) { FontFilePolicy.validate(file) }
        }
    }
    private fun temporary(block: (File) -> Unit) {
        val file = Files.createTempFile("font-policy-", ".ttf").toFile()
        try { block(file) } finally { file.delete() }
    }
}
