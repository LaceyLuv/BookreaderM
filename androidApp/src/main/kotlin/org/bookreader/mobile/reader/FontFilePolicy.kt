package org.bookreader.mobile.reader

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.ceil
import kotlin.math.floor

/** Bound and validate the SFNT directory before handing an imported file to the platform parser. */
object FontFilePolicy {
    const val MAX_BYTES = 20L * 1024 * 1024
    private const val TAG_FVAR = 0x66766172 // "fvar"
    private const val TAG_WGHT = 0x77676874 // "wght"
    private const val MAX_FVAR_AXES = 64

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

    /**
     * Reads the bounded SFNT `fvar` table and returns the supported `wght` axis range (1..1000)
     * for OpenType variable fonts, or `null` for static fonts or malformed tables.
     */
    fun variableWeightRange(file: File): IntRange? = runCatching {
        if (!file.isFile || file.length() !in 12..MAX_BYTES) return null
        RandomAccessFile(file, "r").use { input ->
            val signature = input.readInt()
            if (signature != 0x00010000 && signature != 0x4f54544f) return null
            val count = input.readUnsignedShort()
            if (count !in 1..128 || 12L + count * 16 > input.length()) return null
            input.seek(12)
            var fvarOffset = -1L
            var fvarLength = 0L
            repeat(count) {
                val tag = input.readInt()
                input.readInt()
                val offset = input.readInt().toLong() and 0xffffffffL
                val length = input.readInt().toLong() and 0xffffffffL
                if (offset < 12L + count * 16 || offset + length > input.length()) return null
                if (tag == TAG_FVAR) {
                    if (fvarOffset >= 0L) return null
                    fvarOffset = offset
                    fvarLength = length
                }
            }
            if (fvarOffset < 0L || fvarLength < 16L) return null
            input.seek(fvarOffset)
            val major = input.readUnsignedShort()
            input.readUnsignedShort() // minorVersion
            val axesArrayOffset = input.readUnsignedShort()
            input.readUnsignedShort() // reserved
            val axisCount = input.readUnsignedShort()
            val axisSize = input.readUnsignedShort()
            if (major != 1 || axesArrayOffset < 16 || axisCount !in 1..MAX_FVAR_AXES || axisSize < 20) return null
            val axesEnd = axesArrayOffset.toLong() + axisCount.toLong() * axisSize.toLong()
            if (axesEnd > fvarLength) return null
            for (index in 0 until axisCount) {
                input.seek(fvarOffset + axesArrayOffset + index.toLong() * axisSize)
                val axisTag = input.readInt()
                val minFixed = input.readInt()
                input.readInt() // defaultValue
                val maxFixed = input.readInt()
                if (axisTag == TAG_WGHT) {
                    val minVal = minFixed / 65536.0
                    val maxVal = maxFixed / 65536.0
                    if (!minVal.isFinite() || !maxVal.isFinite() || minVal < 1.0 || maxVal > 1000.0 || minVal >= maxVal) {
                        return null
                    }
                    val minWeight = ceil(minVal).toInt().coerceIn(1, 1000)
                    val maxWeight = floor(maxVal).toInt().coerceIn(1, 1000)
                    return if (minWeight < maxWeight) minWeight..maxWeight else null
                }
            }
            null
        }
    }.getOrNull()
}
