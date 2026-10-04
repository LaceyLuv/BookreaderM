package org.bookreader.mobile.encoding

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TxtDecoderTest {
    @Test fun bomDetectionSurvivesOneByteReadsAndPreservesInteriorBomAndWhitespace() {
        val source = "\uFEFF  한글\r\n😀\r끝\n\uFEFF\n\n "
        val expected = "  한글\n😀\n끝\n\uFEFF\n\n "
        for ((encoding, charset) in listOf(
            TxtEncoding.UTF8 to Charsets.UTF_8,
            TxtEncoding.UTF16LE to Charsets.UTF_16LE,
            TxtEncoding.UTF16BE to Charsets.UTF_16BE,
        )) {
            val (result, text) = decode(ShortReads(source.toByteArray(charset)))
            assertEquals(encoding, result.encoding)
            assertEquals(expected, text)
            assertEquals(expected.length.toLong(), result.canonicalUtf16Length)
            assertEquals(source.toByteArray(charset).size.toLong(), result.sourceByteCount)
            assertEquals(1, result.normalizationVersion)
        }
    }

    @Test fun normalizationIsIndependentOfByteAndTextChunkBoundaries() {
        val source = "x".repeat(15) + "😀\r\n A\rB\n\r\n한글\uFEFFe\u0301\t\u000B\u000C" + "😀z".repeat(40)
        val expected = source.replace("\r\n", "\n").replace('\r', '\n')
        for (encoding in listOf(TxtEncoding.UTF8, TxtEncoding.UTF16LE, TxtEncoding.UTF16BE)) {
            for (readSize in 1..7) {
                for (chunkSize in 2..7) {
                    val chunks = mutableListOf<String>()
                    val bytes = source.toByteArray(encoding.charset())
                    val result = TxtDecoder.decode(
                        ShortReads(bytes, readSize), encoding,
                        TxtDecodeLimits(ioBufferBytes = 4, textChunkUtf16Units = chunkSize),
                    ) { chunk ->
                        assertTrue(chunk.length <= chunkSize)
                        assertFalse(chunk.first().isLowSurrogate())
                        assertFalse(chunk.last().isHighSurrogate())
                        chunks += chunk
                    }
                    assertEquals(expected, chunks.joinToString(""))
                    assertEquals(expected.length.toLong(), result.canonicalUtf16Length)
                }
            }
        }
    }

    @Test fun cp949ExtensionIsNotEucKrAndNeverSelectedAutomatically() {
        val extension = byteArrayOf(0x81.toByte(), 0x41)
        assertTrue(TxtEncoding.CP949.isSupported)
        assertTrue(TxtEncoding.EUC_KR.isSupported)
        assertEquals("갂", decode(ByteArrayInputStream(extension), TxtEncoding.CP949).second)
        expectFailure(TxtDecodeFailure.MALFORMED_INPUT) { TxtDecoder.validate(ByteArrayInputStream(extension), TxtEncoding.EUC_KR) }
        expectFailure(TxtDecodeFailure.MALFORMED_INPUT) { TxtDecoder.validate(ByteArrayInputStream(extension)) }
        val common = "한글 각\r\n".toByteArray(TxtEncoding.EUC_KR.charset())
        assertEquals("한글 각\n", decode(ByteArrayInputStream(common), TxtEncoding.CP949).second)
        assertEquals("한글 각\n", decode(ByteArrayInputStream(common), TxtEncoding.EUC_KR).second)
    }

    @Test fun eucKrGrammarCarriesPairedBytesAndRejectsCp949ExtensionRangesAtExactOffsets() {
        // The lead byte is at position 15, so its trail arrives after the 16-byte BOM/header prefix.
        val ascii = "a".repeat(15).toByteArray()
        val korean = "한글 각\r\n".toByteArray(TxtEncoding.EUC_KR.charset())
        val canonical = StringBuilder()
        val valid = TxtDecoder.decode(
            ShortReads(ascii + korean), TxtEncoding.EUC_KR,
            TxtDecodeLimits(ioBufferBytes = 4, textChunkUtf16Units = 2),
        ) { canonical.append(it) }
        assertEquals("a".repeat(15) + "한글 각\n", canonical.toString())
        assertEquals(canonical.length.toLong(), valid.canonicalUtf16Length)

        for (invalid in listOf(
            byteArrayOf(0x81.toByte(), 0x41), // CP949 extension lead
            byteArrayOf(0xa1.toByte(), 0x41), // CP949 extension trail
            byteArrayOf(0xa1.toByte()), // dangling EUC-KR lead
            byteArrayOf(0x80.toByte()), byteArrayOf(0xff.toByte()),
            byteArrayOf(0x8e.toByte(), 0xa1.toByte()), // EUC-JP SS2 is not EUC-KR
        )) {
            val failure = expectFailure(TxtDecodeFailure.MALFORMED_INPUT) {
                TxtDecoder.validate(ShortReads(ascii + invalid), TxtEncoding.EUC_KR,
                    TxtDecodeLimits(ioBufferBytes = 4))
            }
            assertEquals(15L, failure.byteOffset)
        }
        // A prefix cut between a valid lead/trail does not invent a replacement or claim EOF.
        val preview = TxtDecoder.probe(ByteArrayInputStream(ascii + korean), maxPreviewSourceBytes = 16)
            .previews.single { it.encoding == TxtEncoding.EUC_KR }
        assertEquals("a".repeat(15), preview.text)
        assertEquals(null, preview.failure)
        assertFalse(preview.completeSource)
    }

    @Test fun bomlessUtf16RequiresAnExplicitChoice() {
        for (encoding in listOf(TxtEncoding.UTF16LE, TxtEncoding.UTF16BE)) {
            val source = "A한글\r\n😀".toByteArray(encoding.charset())
            expectFailure(TxtDecodeFailure.BINARY_CONTENT) { TxtDecoder.validate(ByteArrayInputStream(source)) }
            assertEquals("A한글\n😀", decode(ShortReads(source), encoding).second)
        }
        assertEquals(TxtEncoding.UTF8, TxtDecoder.validate(ByteArrayInputStream("ASCII".toByteArray())).encoding)
    }

    @Test fun malformedUtf8AndTruncatedUtf16NeverUseReplacementCharacters() {
        val badUtf8 = listOf(
            byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
            byteArrayOf(0xe2.toByte(), 0x28, 0xa1.toByte()),
            byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()),
            byteArrayOf(0xf5.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte()),
            byteArrayOf(0xf0.toByte(), 0x9f.toByte(), 0x98.toByte()),
        )
        for (bytes in badUtf8) expectFailure(TxtDecodeFailure.MALFORMED_INPUT) {
            TxtDecoder.validate(ShortReads(bytes), TxtEncoding.UTF8)
        }
        for (encoding in listOf(TxtEncoding.UTF16LE, TxtEncoding.UTF16BE)) {
            for (source in listOf("\uD83D", "\uDC00", "\uD83Dx")) {
                // CharsetEncoder would replace the invalid surrogate: construct code units directly.
                val bytes = source.flatMap { char ->
                    val low = char.code.toByte()
                    val high = (char.code ushr 8).toByte()
                    if (encoding == TxtEncoding.UTF16LE) listOf(low, high) else listOf(high, low)
                }.toByteArray()
                expectFailure(TxtDecodeFailure.MALFORMED_INPUT) { TxtDecoder.validate(ShortReads(bytes), encoding) }
            }
            expectFailure(TxtDecodeFailure.MALFORMED_INPUT) { TxtDecoder.validate(ShortReads(byteArrayOf(0x41)), encoding) }
        }
        // A real encoded U+FFFD is valid text; rejecting it would conflate content with replacement.
        assertEquals("�", decode(ByteArrayInputStream("�".toByteArray())).second)
    }

    @Test fun completeValidationFindsAnErrorAfterAValidPreview() {
        val source = ByteArray(80 * 1024) { 'a'.code.toByte() } + byteArrayOf(0xff.toByte())
        val preview = TxtDecoder.probe(ByteArrayInputStream(source))
        assertFalse(preview.completeSource)
        assertEquals(64 * 1024, preview.inspectedSourceBytes)
        assertEquals(null, preview.previews.single { it.encoding == TxtEncoding.UTF8 }.failure)
        val failure = expectFailure(TxtDecodeFailure.MALFORMED_INPUT) { TxtDecoder.validate(ShortReads(source, 17)) }
        assertEquals(80L * 1024, failure.byteOffset)
    }

    @Test fun previewWithholdsACutOffMultibyteCharacterAndNeverSplitsASurrogate() {
        val prefix = "x".repeat(15).toByteArray() + "😀more".toByteArray()
        val preview = TxtDecoder.probe(ByteArrayInputStream(prefix), maxPreviewSourceBytes = 16)
            .previews.single { it.encoding == TxtEncoding.UTF8 }
        assertEquals("x".repeat(15), preview.text)
        assertEquals(null, preview.failure)
        assertFalse(preview.completeSource)
        val shortText = TxtDecoder.probe(ByteArrayInputStream("a😀later".toByteArray()), maxPreviewUtf16Units = 2)
            .previews.single { it.encoding == TxtEncoding.UTF8 }
        assertEquals("a", shortText.text)
        assertTrue(shortText.textTruncated)
    }

    @Test fun explicitMismatchedBomAndUtf32AreRejected() {
        expectFailure(TxtDecodeFailure.BOM_MISMATCH) {
            TxtDecoder.validate(ByteArrayInputStream("\uFEFFA".toByteArray()), TxtEncoding.CP949)
        }
        for (bytes in listOf(byteArrayOf(-1, -2, 0, 0, 0x41, 0, 0, 0), byteArrayOf(0, 0, -2, -1, 0, 0, 0, 0x41))) {
            expectFailure(TxtDecodeFailure.UNSUPPORTED_ENCODING) { TxtDecoder.validate(ShortReads(bytes)) }
            assertEquals(null, TxtDecoder.probe(ByteArrayInputStream(bytes)).bomEncoding)
        }
    }

    @Test fun binaryMaskedAsTxtIsRejectedButCommonTextControlsArePreserved() {
        for (bytes in listOf(
            byteArrayOf(0x50, 0x4b, 0x03, 0x04), "%PDF-1.7".toByteArray(),
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47), "GIF89a".toByteArray(),
            byteArrayOf(0), byteArrayOf(0x01), byteArrayOf(0x7f),
        )) expectFailure(TxtDecodeFailure.BINARY_CONTENT) { TxtDecoder.validate(ShortReads(bytes)) }
        val text = "tab\tline\nform\u000Cvertical\u000B"
        assertEquals(text, decode(ByteArrayInputStream(text.toByteArray())).second)
    }

    @Test fun sourceLimitUsesActualBytesAndReadsAtMostOneByteBeyondLimit() {
        val atLimit = ShortReads(ByteArray(32) { 0x41 }, 3)
        assertEquals(32L, TxtDecoder.validate(atLimit, limits = TxtDecodeLimits(maxSourceBytes = 32)).sourceByteCount)
        val overLimit = ShortReads(ByteArray(100) { 0x41 }, 3)
        expectFailure(TxtDecodeFailure.SOURCE_TOO_LARGE) {
            TxtDecoder.validate(overLimit, limits = TxtDecodeLimits(maxSourceBytes = 32))
        }
        assertEquals(33, overLimit.bytesRead)
        val tinyLimit = ShortReads(ByteArray(100) { 0x41 }, 16)
        expectFailure(TxtDecodeFailure.SOURCE_TOO_LARGE) {
            TxtDecoder.validate(tinyLimit, limits = TxtDecodeLimits(maxSourceBytes = 1))
        }
        assertEquals(2, tinyLimit.bytesRead)
    }

    @Test fun validationDoesNotAccumulateTheBodyAndHandlesZeroLengthReadResults() {
        val total = 5L * 1024 * 1024
        val source = object : InputStream() {
            var remaining = total
            var zero = true
            override fun read(): Int = if (remaining-- > 0) 'a'.code else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (zero) { zero = false; return 0 }
                if (remaining == 0L) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                bytes.fill('a'.code.toByte(), offset, offset + count)
                remaining -= count
                return count
            }
        }
        var maximumChunk = 0
        var emitted = 0L
        val result = TxtDecoder.decode(source) { chunk ->
            maximumChunk = maxOf(maximumChunk, chunk.length)
            emitted += chunk.length
        }
        assertTrue(maximumChunk <= 16 * 1024)
        assertEquals(total, emitted)
        assertEquals(total, result.sourceByteCount)
        assertEquals(total, result.canonicalUtf16Length)
    }

    @Test fun cancellationAndBudgetFailuresPropagateWithoutClosingCallerOwnedStream() {
        val source = ShortReads("abc".toByteArray())
        try {
            TxtDecoder.validate(source, cancellationCheck = { throw CancellationException("test") })
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, source.bytesRead)
            assertFalse(source.closed)
        }
        TxtDecoder.validate(source)
        assertFalse(source.closed)
        val delayed = object : ByteArrayInputStream("body".toByteArray()) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                Thread.sleep(5)
                return super.read(bytes, offset, length)
            }
        }
        expectFailure(TxtDecodeFailure.TIME_BUDGET_EXCEEDED) {
            TxtDecoder.validate(delayed, limits = TxtDecodeLimits(maxDurationMillis = 1))
        }
    }

    @Test fun encodingIdsAndEmptyTextContractAreStable() {
        assertEquals(listOf("utf-8", "utf-16le", "utf-16be", "cp949", "euc-kr"), TxtEncoding.entries.map { it.id })
        for (encoding in TxtEncoding.entries) assertEquals(encoding, TxtEncoding.fromId(encoding.id))
        assertEquals(null, TxtEncoding.fromId("MS949"))
        val result = TxtDecoder.validate(ByteArrayInputStream(byteArrayOf()))
        assertEquals(0L, result.sourceByteCount)
        assertEquals(0L, result.canonicalUtf16Length)
    }

    private fun decode(input: InputStream, encoding: TxtEncoding? = null): Pair<TxtValidation, String> {
        val output = StringBuilder()
        val result = TxtDecoder.decode(input, encoding, onChunk = output::append)
        return result to output.toString()
    }

    private fun expectFailure(code: TxtDecodeFailure, block: () -> Unit): TxtDecodingException {
        try { block() } catch (failure: TxtDecodingException) {
            assertEquals(code, failure.code)
            return failure
        }
        fail("Expected $code")
        throw AssertionError("Unreachable")
    }

    private class ShortReads(bytes: ByteArray, private val readSize: Int = 1) : ByteArrayInputStream(bytes) {
        var bytesRead = 0
        var closed = false
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            super.read(bytes, offset, minOf(length, readSize)).also { if (it > 0) bytesRead += it }
        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }
        override fun close() { closed = true; super.close() }
    }
}
