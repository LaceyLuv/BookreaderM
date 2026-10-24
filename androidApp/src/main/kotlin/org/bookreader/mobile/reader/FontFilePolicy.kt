package org.bookreader.mobile.reader

import java.io.File
import java.io.RandomAccessFile

/** Bound and validate the SFNT directory before handing an imported file to the platform parser. */
object FontFilePolicy {
    const val MAX_BYTES = 20L * 1024 * 1024
    fun validate(file: File) {
        require(file.length() in 12..MAX_BYTES) { "글꼴은 20MB 이하의 TTF·OTF 파일이어야 합니다." }
        RandomAccessFile(file, "r").use { input ->
            val signature = input.readInt()
            require(signature == 0x00010000 || signature == 0x4f54544f) { "지원하지 않는 글꼴 형식입니다. TTF·OTF 파일을 선택하세요." }
            val count = input.readUnsignedShort()
            require(count in 1..128 && 12L + count * 16 <= input.length()) { "손상된 글꼴 파일입니다." }
            input.seek(12)
            val tables = mutableMapOf<Int, LongRange>()
            repeat(count) {
                val tag = input.readInt()
                input.readInt() // Table checksum; bounds are checked here, decoding by the platform.
                val offset = input.readInt().toLong() and 0xffffffffL
                val length = input.readInt().toLong() and 0xffffffffL
                require(offset >= 12L + count * 16 && offset + length <= input.length() && tag !in tables) { "손상된 글꼴 테이블입니다." }
                tables[tag] = offset until offset + length
            }
            val required = listOf("head", "maxp", "cmap", "name", "hhea", "hmtx") +
                if (signature == 0x00010000) listOf("glyf", "loca") else emptyList()
            if (signature == 0x4f54544f) require(listOf(0x43464620, 0x43464632).any { tables[it]?.isEmpty() == false }) { "OTF 윤곽선 테이블이 없습니다." }
            for (tag in required) {
                val key = tag.fold(0) { value, c -> (value shl 8) or c.code }
                require(tables[key]?.isEmpty() == false) { "필수 글꼴 테이블이 없습니다." }
            }
            val regions = tables.values.filterNot { it.isEmpty() }.sortedBy { it.first }
            require(regions.zipWithNext().all { (left, right) -> left.last < right.first }) { "겹치는 글꼴 테이블입니다." }
        }
    }
}
