package org.bookreader.mobile.encoding

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Strict, bounded synchronous decoder. Call on an IO dispatcher, close the input in the caller,
 * and pass the owning job's ensureActive as cancellationCheck. Partial output is provisional:
 * publish a canonical cache only after this function returns successfully.
 */
object TxtDecoder {
    fun validate(
        input: InputStream,
        encoding: TxtEncoding? = null,
        limits: TxtDecodeLimits = TxtDecodeLimits(),
        cancellationCheck: () -> Unit = {},
    ): TxtValidation = decode(input, encoding, limits, cancellationCheck) { }

    /** Canonical chunks never split a UTF-16 surrogate pair. */
    fun decode(
        input: InputStream,
        encoding: TxtEncoding? = null,
        limits: TxtDecodeLimits = TxtDecodeLimits(),
        cancellationCheck: () -> Unit = {},
        onChunk: (String) -> Unit,
    ): TxtValidation = decodeInternal(input, encoding, limits, cancellationCheck, true, onChunk)

    /**
     * Bounded previews for explicit user selection, not an encoding detector. BOM-less UTF-16
     * and legacy encodings remain manual choices even when their preview is syntactically valid.
     * A prefix ending inside a character withholds that character, without replacing it.
     */
    fun probe(
        input: InputStream,
        maxPreviewSourceBytes: Int = 64 * 1024,
        maxPreviewUtf16Units: Int = 4 * 1024,
        cancellationCheck: () -> Unit = {},
    ): TxtEncodingProbe {
        require(maxPreviewSourceBytes in 16..64 * 1024)
        require(maxPreviewUtf16Units in 2..16 * 1024)
        val sample = ByteArray(maxPreviewSourceBytes)
        var size = 0
        val guard = DecodeGuard(60_000, cancellationCheck)
        while (size < sample.size) {
            guard.check()
            val count = readProgress(input, sample, size, sample.size - size)
            guard.check()
            if (count == -1) break
            size += count
        }
        guard.check()
        val complete = size < sample.size || input.read() == -1
        guard.check()
        val bom = bomEncoding(sample, size)
        val previews = TxtEncoding.entries.map { encoding ->
            guard.check()
            val text = StringBuilder(maxPreviewUtf16Units)
            var truncated = false
            try {
                decodeInternal(
                    ByteArrayInputStream(sample, 0, size), encoding,
                    TxtDecodeLimits(), { guard.check() }, complete,
                ) { chunk ->
                    if (!truncated) {
                        var count = minOf(maxPreviewUtf16Units - text.length, chunk.length)
                        if (count > 0 && count < chunk.length && chunk[count - 1].isHighSurrogate()) count--
                        text.append(chunk, 0, count)
                        if (count < chunk.length) truncated = true
                    }
                }
                TxtEncodingPreview(encoding, text.toString(), null, complete, truncated)
            } catch (failure: TxtDecodingException) {
                TxtEncodingPreview(encoding, null, failure.code, complete, false)
            }
        }
        return TxtEncodingProbe(size, complete, bom, previews)
    }

    private fun decodeInternal(
        input: InputStream,
        requestedEncoding: TxtEncoding?,
        limits: TxtDecodeLimits,
        cancellationCheck: () -> Unit,
        completeInput: Boolean,
        onChunk: (String) -> Unit,
    ): TxtValidation {
        val guard = DecodeGuard(limits.maxDurationMillis, cancellationCheck)
        val prefix = ByteArray(16)
        var prefixSize = 0
        while (prefixSize < prefix.size) {
            guard.check()
            val countToRead = minOf(prefix.size - prefixSize, (limits.maxSourceBytes - prefixSize + 1).toInt())
            val count = readProgress(input, prefix, prefixSize, countToRead)
            guard.check()
            if (count == -1) break
            prefixSize += count
            if (prefixSize.toLong() > limits.maxSourceBytes) {
                throw TxtDecodingException(TxtDecodeFailure.SOURCE_TOO_LARGE)
            }
        }
        if (isBinaryHeader(prefix, prefixSize)) {
            throw TxtDecodingException(TxtDecodeFailure.BINARY_CONTENT, 0)
        }
        if (startsWith(prefix, prefixSize, 0xff, 0xfe, 0, 0) ||
            startsWith(prefix, prefixSize, 0, 0, 0xfe, 0xff)) {
            throw TxtDecodingException(TxtDecodeFailure.UNSUPPORTED_ENCODING, 0)
        }
        val bom = bomEncoding(prefix, prefixSize)
        if (bom != null && requestedEncoding != null && bom != requestedEncoding) {
            throw TxtDecodingException(TxtDecodeFailure.BOM_MISMATCH, 0)
        }
        val encoding = requestedEncoding ?: bom ?: TxtEncoding.UTF8
        val decoder = encoding.charset().newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        // Extra bytes hold an incomplete multibyte sequence across reads.
        val bytes = ByteBuffer.allocate(limits.ioBufferBytes + 16)
        bytes.put(prefix, 0, prefixSize)
        val chars = CharBuffer.allocate(limits.textChunkUtf16Units)
        val canonical = CanonicalChunks(limits.textChunkUtf16Units, onChunk, guard)
        var byteCount = prefixSize.toLong()
        var eof = prefixSize < prefix.size
        while (true) {
            guard.check()
            bytes.flip()
            while (true) {
                guard.check()
                val result = decoder.decode(bytes, chars, eof && completeInput)
                chars.flip()
                canonical.accept(chars)
                chars.clear()
                if (result.isError) {
                    throw TxtDecodingException(
                        TxtDecodeFailure.MALFORMED_INPUT,
                        byteCount - bytes.remaining(),
                    )
                }
                if (!result.isOverflow) break
            }
            bytes.compact()
            if (eof) break
            // Read one byte beyond the limit to distinguish an exact-limit source from excess.
            val countToRead = minOf(
                limits.ioBufferBytes.toLong(), limits.maxSourceBytes - byteCount + 1,
            ).toInt()
            val count = readProgress(input, bytes.array(), bytes.position(), countToRead)
            guard.check()
            if (count == -1) {
                eof = true
            } else {
                byteCount += count
                if (byteCount > limits.maxSourceBytes) {
                    throw TxtDecodingException(TxtDecodeFailure.SOURCE_TOO_LARGE)
                }
                bytes.position(bytes.position() + count)
            }
        }
        if (completeInput) {
            while (true) {
                guard.check()
                val result = decoder.flush(chars)
                chars.flip()
                canonical.accept(chars)
                chars.clear()
                if (result.isError) throw TxtDecodingException(TxtDecodeFailure.MALFORMED_INPUT)
                if (!result.isOverflow) break
            }
        }
        canonical.finish(completeInput)
        guard.check()
        return TxtValidation(encoding, byteCount, canonical.length)
    }

    private class CanonicalChunks(
        private val maximum: Int,
        private val output: (String) -> Unit,
        private val guard: DecodeGuard,
    ) {
        private val chunk = StringBuilder(maximum)
        private var first = true
        private var afterCr = false
        private var highSurrogate: Char? = null
        var length = 0L
            private set

        fun accept(chars: CharBuffer) {
            while (chars.hasRemaining()) {
                val char = chars.get()
                if (first) {
                    first = false
                    if (char == '\uFEFF') continue
                }
                val high = highSurrogate
                if (high != null) {
                    if (!char.isLowSurrogate()) throw TxtDecodingException(TxtDecodeFailure.MALFORMED_INPUT)
                    appendPair(high, char)
                    highSurrogate = null
                    afterCr = false
                    continue
                }
                if (char.isHighSurrogate()) {
                    highSurrogate = char
                    continue
                }
                if (char.isLowSurrogate()) throw TxtDecodingException(TxtDecodeFailure.MALFORMED_INPUT)
                if (char == '\u0000' || (char < ' ' && char !in ALLOWED_CONTROLS) || char == '\u007F') {
                    throw TxtDecodingException(TxtDecodeFailure.BINARY_CONTENT)
                }
                if (char == '\n' && afterCr) {
                    afterCr = false
                    continue
                }
                afterCr = char == '\r'
                append(if (afterCr) '\n' else char)
            }
        }

        private fun append(char: Char) {
            if (chunk.length == maximum) emit()
            chunk.append(char)
            length++
        }

        private fun appendPair(high: Char, low: Char) {
            if (chunk.length + 2 > maximum) emit()
            chunk.append(high).append(low)
            length += 2
        }

        private fun emit() {
            if (chunk.isEmpty()) return
            guard.check()
            output(chunk.toString())
            guard.check()
            chunk.setLength(0)
        }

        fun finish(completeInput: Boolean) {
            if (completeInput && highSurrogate != null) throw TxtDecodingException(TxtDecodeFailure.MALFORMED_INPUT)
            emit()
        }
    }

    private class DecodeGuard(durationMillis: Long, private val cancellationCheck: () -> Unit) {
        private val startNanos = System.nanoTime()
        private val durationNanos = durationMillis.coerceAtMost(Long.MAX_VALUE / 1_000_000) * 1_000_000
        fun check() {
            cancellationCheck()
            if (System.nanoTime() - startNanos > durationNanos) {
                throw TxtDecodingException(TxtDecodeFailure.TIME_BUDGET_EXCEEDED)
            }
        }
    }

    private fun readProgress(input: InputStream, target: ByteArray, offset: Int, length: Int): Int {
        val count = input.read(target, offset, length)
        if (count != 0) return count
        val byte = input.read()
        if (byte == -1) return -1
        target[offset] = byte.toByte()
        return 1
    }

    private fun bomEncoding(bytes: ByteArray, size: Int): TxtEncoding? = when {
        startsWith(bytes, size, 0xff, 0xfe, 0, 0) || startsWith(bytes, size, 0, 0, 0xfe, 0xff) -> null
        startsWith(bytes, size, 0xef, 0xbb, 0xbf) -> TxtEncoding.UTF8
        startsWith(bytes, size, 0xff, 0xfe) -> TxtEncoding.UTF16LE
        startsWith(bytes, size, 0xfe, 0xff) -> TxtEncoding.UTF16BE
        else -> null
    }

    private fun startsWith(bytes: ByteArray, size: Int, vararg signature: Int): Boolean =
        size >= signature.size && signature.indices.all { bytes[it].toInt() and 0xff == signature[it] }

    private fun isBinaryHeader(bytes: ByteArray, size: Int): Boolean =
        startsWith(bytes, size, 0x50, 0x4b, 0x03, 0x04) || // ZIP/EPUB
            startsWith(bytes, size, 0x50, 0x4b, 0x05, 0x06) || // Empty ZIP
            startsWith(bytes, size, 0x89, 0x50, 0x4e, 0x47) ||
            startsWith(bytes, size, 0xff, 0xd8, 0xff) ||
            startsWith(bytes, size, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61) ||
            startsWith(bytes, size, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61) ||
            startsWith(bytes, size, 0x25, 0x50, 0x44, 0x46, 0x2d) ||
            startsWith(bytes, size, 0x7f, 0x45, 0x4c, 0x46) ||
            startsWith(bytes, size, 0x4d, 0x5a, 0x90, 0x00)

    private val ALLOWED_CONTROLS = charArrayOf('\t', '\n', '\u000B', '\u000C', '\r')
}
