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

    @Test fun variableWeightRangeDetectsValidFvarWghtAndFailsClosedOnStaticOrMalformedFonts() = temporary { file ->
        assertNull(FontFilePolicy.variableWeightRange(File("src/main/assets/reader-fonts/NanumGothic-Regular.ttf")))
        assertNull(FontFilePolicy.variableWeightRange(File("src/main/assets/reader-fonts/NanumMyeongjo-Regular.ttf")))
        assertNull(FontFilePolicy.variableWeightRange(File("src/debug/assets/font-fixtures/Fixture.otf")))

        // Minimal synthetic SFNT container with one `fvar` table containing a `wdth` axis and a `wght` axis (100..900).
        fun writeFvarFont(axisTag: Int, minWeight: Int, maxWeight: Int, truncateBytes: Int = 0) {
            val tableOffset = 12 + 16
            val fvarLength = 16 + 20 * 2
            val buffer = java.nio.ByteBuffer.allocate(tableOffset + fvarLength).apply {
                putInt(0x00010000) // TrueType sfntVersion
                putShort(1) // numTables
                putShort(16); putShort(0); putShort(0) // searchRange, entrySelector, rangeShift
                putInt(0x66766172) // "fvar"
                putInt(0) // checksum
                putInt(tableOffset)
                putInt(fvarLength)
                // fvar header
                putShort(1); putShort(0) // majorVersion=1, minorVersion=0
                putShort(16); putShort(2) // axesArrayOffset=16, reserved=2
                putShort(2); putShort(20) // axisCount=2, axisSize=20
                putShort(0); putShort(0) // instanceCount=0, instanceSize=0
                // Axis 0: "wdth" 75..125
                putInt(0x77647468); putInt(75 shl 16); putInt(100 shl 16); putInt(125 shl 16); putShort(0); putShort(256)
                // Axis 1: target axis
                putInt(axisTag); putInt(minWeight shl 16); putInt(400 shl 16); putInt(maxWeight shl 16); putShort(0); putShort(257)
            }
            val bytes = buffer.array()
            file.writeBytes(bytes.copyOf(bytes.size - truncateBytes))
        }

        writeFvarFont(axisTag = 0x77676874, minWeight = 100, maxWeight = 900)
        assertEquals(100..900, FontFilePolicy.variableWeightRange(file))

        writeFvarFont(axisTag = 0x736c6e74, minWeight = 100, maxWeight = 900) // "slnt", no "wght"
        assertNull(FontFilePolicy.variableWeightRange(file))

        writeFvarFont(axisTag = 0x77676874, minWeight = 900, maxWeight = 100) // inverted range
        assertNull(FontFilePolicy.variableWeightRange(file))

        writeFvarFont(axisTag = 0x77676874, minWeight = 100, maxWeight = 900, truncateBytes = 10)
        assertNull(FontFilePolicy.variableWeightRange(file))
    }

    private fun temporary(block: (File) -> Unit) {
        val file = Files.createTempFile("font-policy-", ".ttf").toFile()
        try { block(file) } finally { file.delete() }
    }
}
