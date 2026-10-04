package org.bookreader.mobile.importing

import org.bookreader.mobile.model.Book

const val MAX_TXT_BYTES: Long = 256L * 1024 * 1024

data class ImportRequest(
    val sourceUri: String,
    val displayName: String,
    val expectedBytes: Long? = null,
    val encodingId: String? = null,
)

data class FileFingerprint(val sha256: String, val byteSize: Long) {
    init {
        require(sha256.length == 64 && sha256.all { it in "0123456789abcdef" })
        require(byteSize in 0..MAX_TXT_BYTES)
    }
}

data class TxtValidation(
    val encodingId: String,
    val normalizationVersion: Int,
) {
    init {
        require(encodingId.isNotBlank())
        require(normalizationVersion > 0)
    }
}

enum class ImportErrorCode {
    PERMISSION_DENIED, SOURCE_UNAVAILABLE, DISK_FULL, TOO_LARGE, UNSUPPORTED_FORMAT,
    ENCODING_REQUIRED, INVALID_TEXT, FILE_IO, DATABASE_UNAVAILABLE, INTERRUPTED,
    INVALID_JOURNAL, EXISTING_COPY_UNAVAILABLE, BOOK_DELETING,
}

class ImportFailure(val code: ImportErrorCode) : Exception(code.name)

sealed interface ImportResult {
    data class Success(val book: Book, val duplicate: Boolean) : ImportResult
    data class Failure(val code: ImportErrorCode) : ImportResult
    data class EncodingRequired(
        val jobId: String,
        val stagingPath: String,
        val displayName: String,
        val code: ImportErrorCode = ImportErrorCode.ENCODING_REQUIRED,
    ) : ImportResult
}

enum class ImportPhase { COPYING, VALIDATING, FINALIZING }
data class ImportProgress(val phase: ImportPhase, val bytesCopied: Long, val expectedBytes: Long?)

/** Implementations own/close source streams, use bounded I/O, and resolve paths beneath private storage. */
interface ManagedImportFiles {
    suspend fun copySource(
        request: ImportRequest,
        stagingPath: String,
        maxBytes: Long,
        onBytes: suspend (Long) -> Unit,
    ): FileFingerprint

    /** Strict streaming validation, never replacement decoding. Revision hashes source+encoding+policy. */
    suspend fun validateTxt(relativePath: String, sourceSha256: String, encodingId: String?): TxtValidation
    suspend fun fingerprint(relativePath: String, maxBytes: Long): FileFingerprint?
    /** Atomic same-filesystem move; never overwrite an existing destination. */
    suspend fun promote(stagingPath: String, finalRelativePath: String)
    /** Remove only this owned file (plus empty owned parents), never source URIs or recursive trees. */
    suspend fun remove(relativePath: String)
}

enum class ImportCheckpoint { JOURNALED, COPYING, COPIED, VALIDATED, FINALIZING, PROMOTED, COMMITTED, DELETING, FILE_REMOVED }

/** Tests throw an Error to model process death. Production uses the no-op default. */
fun interface ImportFaultHook { suspend fun reached(checkpoint: ImportCheckpoint) }

sealed interface DeleteResult {
    data object Deleted : DeleteResult
    data object Missing : DeleteResult
    data class Failure(val code: ImportErrorCode) : DeleteResult
}
