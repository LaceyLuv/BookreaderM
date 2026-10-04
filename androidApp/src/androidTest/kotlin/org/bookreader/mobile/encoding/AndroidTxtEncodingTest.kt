package org.bookreader.mobile.encoding

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against Android's actual Charset implementation, not the host JDK's charset tables. */
@RunWith(AndroidJUnit4::class)
class AndroidTxtEncodingTest {
    @Test fun androidCp949ExtensionFixtureIsDistinctFromEucKr() {
        assertTrue("Android must support a strict CP949 decoder", TxtEncoding.CP949.isSupported)
        assertTrue(TxtEncoding.EUC_KR.isSupported)
        // Self-authored fixtures/encoding/cp949-extension.txt: U+AC02 uses CP949 bytes 81 41.
        val fixture = "CP949: ".toByteArray() + byteArrayOf(0x81.toByte(), 0x41, 0x0d, 0x0a)
        val text = StringBuilder()
        val validated = TxtDecoder.decode(ByteArrayInputStream(fixture), TxtEncoding.CP949) { text.append(it) }
        assertEquals("CP949: 갂\n", text.toString())
        assertEquals("cp949", validated.encoding.id)
        assertEquals(1, validated.normalizationVersion)
        for (encoding in listOf(TxtEncoding.EUC_KR, TxtEncoding.UTF8)) {
            try {
                TxtDecoder.validate(ByteArrayInputStream(fixture), encoding)
                fail("$encoding must reject CP949 extension bytes")
            } catch (failure: TxtDecodingException) {
                assertEquals(TxtDecodeFailure.MALFORMED_INPUT, failure.code)
            }
        }
    }

    @Test fun androidBomAndBoundaryNormalizationAreCanonical() {
        val text = "\uFEFF 한글\r\n😀\r\uFEFF끝 "
        for (encoding in listOf(TxtEncoding.UTF8, TxtEncoding.UTF16LE, TxtEncoding.UTF16BE)) {
            val fixture = text.toByteArray(encoding.charset())
            val input = object : ByteArrayInputStream(fixture) {
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int = super.read(bytes, offset, minOf(length, 1))
            }
            val canonical = StringBuilder()
            val result = TxtDecoder.decode(input, limits = TxtDecodeLimits(ioBufferBytes = 4, textChunkUtf16Units = 2)) {
                assertTrue(it.length <= 2)
                assertTrue(!it.first().isLowSurrogate() && !it.last().isHighSurrogate())
                canonical.append(it)
            }
            assertEquals(encoding, result.encoding)
            assertEquals(" 한글\n😀\n\uFEFF끝 ", canonical.toString())
            assertEquals(canonical.length.toLong(), result.canonicalUtf16Length)
        }
    }

    @Test fun androidStrictDecodeRejectsLateIncompleteCharacters() {
        for ((encoding, trailing) in listOf(
            TxtEncoding.UTF8 to byteArrayOf(0xf0.toByte(), 0x9f.toByte(), 0x98.toByte()),
            TxtEncoding.UTF16LE to byteArrayOf(0x3d, 0xd8.toByte()),
            TxtEncoding.UTF16BE to byteArrayOf(0xd8.toByte(), 0x3d),
        )) {
            val fixture = "valid\r\n".repeat(4096).toByteArray(encoding.charset()) + trailing
            try {
                TxtDecoder.validate(ByteArrayInputStream(fixture), encoding)
                fail("Trailing incomplete $encoding must fail")
            } catch (failure: TxtDecodingException) {
                assertEquals(TxtDecodeFailure.MALFORMED_INPUT, failure.code)
            }
        }
    }
}
