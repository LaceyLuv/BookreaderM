package org.bookreader.mobile.epub

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** Validate bounded central/local records before inflation. Never extract names onto the filesystem. */
class BoundedZip(private val file: File, maxSourceBytes: Long = 512L * 1024 * 1024,
    private val checkCancellation: () -> Unit = {}) {
    data class Entry(val key: String, val size: Long, val compressedSize: Long, val crc: Long,
        val method: Int, val dataOffset: Long, val legacyName: Boolean)
    val entries: Map<String, Entry>
    private val sourceLength = file.length()
    init {
        require(sourceLength in 22..maxSourceBytes) { "Archive source limit" }
        entries = RandomAccessFile(file, "r").use { input ->
            val tailSize = minOf(sourceLength, 65_557).toInt()
            val tail = ByteArray(tailSize)
            input.seek(sourceLength - tailSize); input.readFully(tail)
            val eocd = (tail.size - 22 downTo 0).firstOrNull {
                u32(tail, it) == 0x06054b50L && it + 22 + u16(tail, it + 20) == tail.size
            } ?: error("Missing archive directory")
            require(u16(tail, eocd + 4) == 0 && u16(tail, eocd + 6) == 0) { "Split archive unsupported" }
            val count = u16(tail, eocd + 10)
            require(count in 1..20_000 && u16(tail, eocd + 8) == count) { "Entry count limit" }
            val directorySize = u32(tail, eocd + 12)
            val directoryOffset = u32(tail, eocd + 16)
            require(directorySize <= 64L * 1024 * 1024 && directoryOffset + directorySize == sourceLength - tailSize + eocd) { "Directory bounds" }
            val result = linkedMapOf<String, Entry>()
            val paths = mutableSetOf<String>()
            val intervals = mutableListOf<Pair<Long, Long>>()
            var cursor = directoryOffset
            var total = 0L
            repeat(count) {
                checkCancellation()
                require(cursor + 46 <= directoryOffset + directorySize)
                input.seek(cursor)
                val header = ByteArray(46); input.readFully(header)
                require(u32(header, 0) == 0x02014b50L) { "Invalid directory record" }
                val flags = u16(header, 8)
                require(flags and (1 or 64 or 8192) == 0) { "Encrypted archive unsupported" }
                val method = u16(header, 10)
                require(method == 0 || method == 8) { "Compression unsupported" }
                val compressed = u32(header, 20); val size = u32(header, 24)
                val nameSize = u16(header, 28); val extraSize = u16(header, 30); val commentSize = u16(header, 32)
                val localOffset = u32(header, 42)
                require(compressed != 0xffffffffL && size != 0xffffffffL && localOffset != 0xffffffffL && u16(header, 34) == 0) { "ZIP64 or split archive unsupported" }
                require(nameSize in 1..4096 && size <= 64L * 1024 * 1024) { "Resource limit" }
                cursor += 46L + nameSize + extraSize + commentSize
                require(cursor <= directoryOffset + directorySize)
                val rawName = ByteArray(nameSize); input.readFully(rawName)
                val utf8 = flags and 2048 != 0
                val name = (if (utf8) Charsets.UTF_8 else if (rawName.all { it >= 0 }) Charsets.US_ASCII else charset("CP437")).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(rawName)).toString()
                val directory = name.endsWith('/')
                val key = validateKey(name.removeSuffix("/"))
                require(paths.add(Normalizer.normalize(key, Normalizer.Form.NFC))) { "Duplicate archive path" }
                val unixType = (u32(header, 38) ushr 16).toInt() and 0xf000
                require(unixType == 0 || unixType == 0x8000 || directory && unixType == 0x4000) { "Special file unsupported" }
                require(localOffset + 30 <= directoryOffset)
                input.seek(localOffset)
                val local = ByteArray(30); input.readFully(local)
                require(u32(local, 0) == 0x04034b50L && u16(local, 6) == flags && u16(local, 8) == method && u16(local, 26) == nameSize) { "Local header mismatch" }
                val localName = ByteArray(nameSize); input.readFully(localName)
                require(rawName.contentEquals(localName)) { "Local name mismatch" }
                if (flags and 8 == 0) require(u32(local, 14) == u32(header, 16) && u32(local, 18) == compressed && u32(local, 22) == size) { "Local size mismatch" }
                val dataOffset = localOffset + 30 + nameSize + u16(local, 28)
                require(dataOffset + compressed <= directoryOffset) { "Compressed range outside archive" }
                intervals += localOffset to dataOffset + compressed
                total += size
                require(total <= 4L * 1024 * 1024 * 1024) { "Logical archive size limit" }
                if (directory) require(size == 0L) else result[key] = Entry(key, size, compressed, u32(header, 16), method, dataOffset, !utf8 && rawName.any { it < 0 })
            }
            require(cursor == directoryOffset + directorySize)
            val files = result.keys.map { Normalizer.normalize(it, Normalizer.Form.NFC) }.toSet()
            for (path in paths) {
                val parts = path.split('/')
                for (depth in 1 until parts.size) require(parts.take(depth).joinToString("/") !in files) { "File/directory collision" }
            }
            intervals.sortedBy { it.first }.zipWithNext().forEach { (a, b) -> require(a.second <= b.first) { "Overlapping archive entries" } }
            result
        }
    }

    fun read(key: String, maxBytes: Int, cancellationCheck: () -> Unit = {}): ByteArray {
        val entry = requireNotNull(entries[key]) { "Missing archive entry" }
        val output = ByteArrayOutputStream(minOf(entry.size, 64 * 1024).toInt())
        copyTo(key, output, maxBytes, cancellationCheck)
        return output.toByteArray()
    }

    /** The caller owns the sink and must discard it if validation or cancellation fails. */
    fun copyTo(key: String, output: OutputStream, maxBytes: Int, cancellationCheck: () -> Unit = {}): Long {
        val entry = requireNotNull(entries[key]) { "Missing archive entry" }
        require(maxBytes in 1..64 * 1024 * 1024 && entry.size <= maxBytes && file.length() == sourceLength) { "Resource read limit" }
        val start = System.nanoTime()
        return RandomAccessFile(file, "r").use { input ->
            input.seek(entry.dataOffset)
            var remaining = entry.compressedSize
            val compressed = object : InputStream() {
                override fun read(): Int = if (remaining == 0L) -1 else input.read().also { if (it >= 0) remaining-- }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    if (remaining == 0L) return -1
                    return input.read(bytes, offset, minOf(length.toLong(), remaining).toInt()).also { if (it > 0) remaining -= it }
                }
            }
            val inflater = if (entry.method == 8) Inflater(true) else null
            try {
                val stream = inflater?.let { InflaterInputStream(compressed, it, 16 * 1024) } ?: compressed
                var written = 0L
                val crc = CRC32(); val buffer = ByteArray(16 * 1024)
                while (true) {
                    checkCancellation(); cancellationCheck()
                    require(System.nanoTime() - start < 30_000_000_000L) { "Resource time limit" }
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(written + count <= minOf(maxBytes.toLong(), entry.size)) { "Actual resource size limit" }
                    output.write(buffer, 0, count); crc.update(buffer, 0, count); written += count
                }
                require(written == entry.size && crc.value == entry.crc) { "Resource size or CRC mismatch" }
                if (inflater != null) require(inflater.finished() && inflater.bytesRead == entry.compressedSize) { "Invalid compressed stream" }
                written
            } finally { inflater?.end() }
        }
    }

    companion object {
        fun validateKey(key: String): String {
            require(key.isNotEmpty() && key.length <= 1024 && !key.startsWith('/') &&
                key.none { it == '\\' || it == ':' || it == '%' || it == '?' || it == '#' || it.code < 32 || it.code == 127 }) { "Unsafe archive path" }
            val parts = key.split('/')
            require(parts.size <= 32 && parts.none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe archive path" }
            return key
        }
        private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
        private fun u32(b: ByteArray, p: Int) = u16(b, p).toLong() or (u16(b, p + 2).toLong() shl 16)
    }
}
