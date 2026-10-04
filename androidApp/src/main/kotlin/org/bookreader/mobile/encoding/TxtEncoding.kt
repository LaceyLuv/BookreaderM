package org.bookreader.mobile.encoding

import java.nio.charset.Charset

/** Persist these IDs, never vendor-specific charset aliases. */
enum class TxtEncoding(val id: String, private vararg val charsetAliases: String) {
    UTF8("utf-8", "UTF-8"),
    UTF16LE("utf-16le", "UTF-16LE"),
    UTF16BE("utf-16be", "UTF-16BE"),
    CP949("cp949", "x-windows-949", "MS949", "windows-949", "CP949"),
    EUC_KR("euc-kr", "EUC-KR");

    val isSupported: Boolean get() = charsetAliases.any(Charset::isSupported)

    internal fun charset(): Charset = charsetAliases.firstOrNull(Charset::isSupported)
        ?.let(Charset::forName)
        ?: throw TxtDecodingException(TxtDecodeFailure.UNSUPPORTED_ENCODING)

    companion object {
        fun fromId(id: String): TxtEncoding? = entries.firstOrNull { it.id == id }
    }
}

enum class TxtDecodeFailure {
    MALFORMED_INPUT, UNSUPPORTED_ENCODING, BOM_MISMATCH, BINARY_CONTENT,
    SOURCE_TOO_LARGE, TIME_BUDGET_EXCEEDED,
}

/** Contains no book text or personal source URI. Offset is in source bytes, not canonical units. */
class TxtDecodingException(
    val code: TxtDecodeFailure,
    val byteOffset: Long? = null,
) : java.io.IOException("TXT ${code.name}" + (byteOffset?.let { " at byte $it" } ?: ""))

data class TxtDecodeLimits(
    val maxSourceBytes: Long = MAX_TXT_SOURCE_BYTES,
    val ioBufferBytes: Int = 64 * 1024,
    val textChunkUtf16Units: Int = 16 * 1024,
    val maxDurationMillis: Long = 60_000,
) {
    init {
        require(maxSourceBytes in 1..MAX_TXT_SOURCE_BYTES)
        require(ioBufferBytes in 4..64 * 1024)
        require(textChunkUtf16Units in 2..16 * 1024)
        require(maxDurationMillis > 0)
    }
}

const val MAX_TXT_SOURCE_BYTES: Long = 256L * 1024 * 1024
const val TXT_NORMALIZATION_VERSION: Int = 1

data class TxtValidation(
    val encoding: TxtEncoding,
    val sourceByteCount: Long,
    val canonicalUtf16Length: Long,
    val normalizationVersion: Int = TXT_NORMALIZATION_VERSION,
)

data class TxtEncodingPreview(
    val encoding: TxtEncoding,
    val text: String?,
    val failure: TxtDecodeFailure?,
    /** A preview never validates a suffix that was not inspected. */
    val completeSource: Boolean,
    val textTruncated: Boolean,
)

data class TxtEncodingProbe(
    val inspectedSourceBytes: Int,
    val completeSource: Boolean,
    val bomEncoding: TxtEncoding?,
    val previews: List<TxtEncodingPreview>,
)
