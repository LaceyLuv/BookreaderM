package org.bookreader.mobile.backup

import kotlinx.serialization.Serializable
import org.bookreader.mobile.annotation.TxtAnnotations
import org.bookreader.mobile.importing.maxImportBytes
import org.bookreader.mobile.model.BookFormat

@Serializable
enum class BackupAssetKind { BOOK, FONT }

@Serializable
data class BackupAssetEntry(
    val kind: BackupAssetKind,
    val path: String,
    val sha256: String,
    val byteSize: Long,
    val format: String? = null,
    val label: String? = null,
)

/** Portable v2 manifest combining v1 reading records with bounded, content-addressed asset metadata. */
@Serializable
data class BackupBundleV2(
    val records: ReadingBackup,
    val assets: List<BackupAssetEntry>,
)

data class BackupExportOptions(
    val includeBooks: Boolean = false,
    val includeFonts: Boolean = false,
) {
    val includesAssets: Boolean get() = includeBooks || includeFonts
}

data class BackupAssetOverview(
    val formatVersion: Int,
    val bookFiles: Int,
    val bookBytes: Long,
    val fontFiles: Int,
    val fontBytes: Long,
) {
    val totalFiles: Int get() = bookFiles + fontFiles
    val totalBytes: Long get() = bookBytes + fontBytes

    companion object {
        val RecordsOnlyV1 = BackupAssetOverview(BackupRules.VERSION, 0, 0L, 0, 0L)
    }
}

object BackupAssetRules {
    const val VERSION_V2 = 2
    const val MAX_ASSET_ENTRIES = BackupRules.MAX_BOOKS + 12
    const val MAX_FONT_COUNT = 12
    const val MIN_FONT_BYTES = 12L
    const val MAX_FONT_BYTES = 20L * 1024 * 1024
    const val MAX_TOTAL_FONT_BYTES = 100L * 1024 * 1024
    const val MAX_BUNDLE_BYTES = 2L * 1024 * 1024 * 1024
    const val REGULAR_FILE_MODE: Byte = 0
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun bookKey(sha256: String, byteSize: Long): String = "$sha256:$byteSize"

    fun canonicalPath(kind: BackupAssetKind, sha256: String): String {
        require(hashPattern.matches(sha256))
        return when (kind) {
            BackupAssetKind.BOOK -> "books/$sha256.bin"
            BackupAssetKind.FONT -> "fonts/$sha256.bin"
        }
    }

    fun validateCanonicalPath(path: String, kind: BackupAssetKind, sha256: String) {
        if (path.isEmpty() || path.startsWith('/') || '\\' in path || ".." in path ||
            path.any { it.code < 32 || it.code == 127 } || !hashPattern.matches(sha256) ||
            path != canonicalPath(kind, sha256)
        ) {
            throw BackupFailure(BackupProblem.FORMAT)
        }
    }

    fun validateBundle(bundle: BackupBundleV2) {
        BackupRules.validate(bundle.records)
        BackupRules.capacity(bundle.assets.size <= MAX_ASSET_ENTRIES)
        val booksByFingerprint = bundle.records.books.associateBy { it.sha256 to it.byteSize }
        val seenPaths = HashSet<String>(bundle.assets.size)
        val seenKeys = HashSet<Pair<BackupAssetKind, String>>(bundle.assets.size)
        var totalBytes = 0L
        var fontCount = 0
        var fontBytes = 0L
        for (entry in bundle.assets) {
            if (!hashPattern.matches(entry.sha256)) throw BackupFailure(BackupProblem.FORMAT)
            validateCanonicalPath(entry.path, entry.kind, entry.sha256)
            if (!seenPaths.add(entry.path) || !seenKeys.add(entry.kind to entry.sha256)) {
                throw BackupFailure(BackupProblem.FORMAT)
            }
            when (entry.kind) {
                BackupAssetKind.BOOK -> {
                    val formatName = entry.format ?: throw BackupFailure(BackupProblem.FORMAT)
                    if (entry.label != null) throw BackupFailure(BackupProblem.FORMAT)
                    val format = try {
                        BookFormat.valueOf(formatName)
                    } catch (_: IllegalArgumentException) {
                        throw BackupFailure(BackupProblem.FORMAT)
                    }
                    if (entry.byteSize < 0) throw BackupFailure(BackupProblem.FORMAT)
                    BackupRules.capacity(entry.byteSize <= maxImportBytes(format))
                    val record = booksByFingerprint[entry.sha256 to entry.byteSize]
                        ?: throw BackupFailure(BackupProblem.FORMAT)
                    if (record.format != format.name) throw BackupFailure(BackupProblem.FORMAT)
                }
                BackupAssetKind.FONT -> {
                    if (entry.format != null) throw BackupFailure(BackupProblem.FORMAT)
                    val label = entry.label ?: throw BackupFailure(BackupProblem.FORMAT)
                    if (label.isBlank() || label.length > 180 || !TxtAnnotations.validUnicode(label) ||
                        label.any { it.code < 32 || it.code == 127 }
                    ) {
                        throw BackupFailure(BackupProblem.FORMAT)
                    }
                    if (entry.byteSize < MIN_FONT_BYTES) throw BackupFailure(BackupProblem.FORMAT)
                    BackupRules.capacity(entry.byteSize <= MAX_FONT_BYTES)
                    fontCount++
                    BackupRules.capacity(fontCount <= MAX_FONT_COUNT)
                    BackupRules.capacity(entry.byteSize <= MAX_TOTAL_FONT_BYTES - fontBytes)
                    fontBytes += entry.byteSize
                }
            }
            BackupRules.capacity(entry.byteSize <= MAX_BUNDLE_BYTES - totalBytes)
            totalBytes += entry.byteSize
        }
    }

    fun overview(bundle: BackupBundleV2): BackupAssetOverview {
        var bookFiles = 0
        var bookBytes = 0L
        var fontFiles = 0
        var fontBytes = 0L
        for (entry in bundle.assets) {
            when (entry.kind) {
                BackupAssetKind.BOOK -> {
                    bookFiles++
                    bookBytes += entry.byteSize
                }
                BackupAssetKind.FONT -> {
                    fontFiles++
                    fontBytes += entry.byteSize
                }
            }
        }
        return BackupAssetOverview(VERSION_V2, bookFiles, bookBytes, fontFiles, fontBytes)
    }
}
