package org.bookreader.mobile.model

enum class BookFormat { TXT, EPUB, COMIC }
enum class BookAvailability { READY, MISSING, CORRUPT, UNSUPPORTED, DELETING }

/** Metadata references a durable private copy. sourceUri is provenance only. */
data class Book(
    val id: String,
    val format: BookFormat,
    val title: String,
    val originalDisplayName: String,
    val managedRelativePath: String,
    val sourceSha256: String,
    val sourceByteSize: Long,
    val addedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val author: String? = null,
    val sourceUri: String? = null,
    val currentRevision: String? = null,
    val encodingId: String? = null,
    val normalizationVersion: Int? = null,
    val availability: BookAvailability = BookAvailability.READY,
    val coverKey: String? = null,
    val lastOpenedAt: Long? = null,
    val lastReadAt: Long? = null,
    val readOrder: Long? = null,
    val activeSessionEpoch: Long = 0,
) {
    init {
        require(id.isNotBlank())
        require(isManagedRelativePath(managedRelativePath)) { "Invalid managed relative path" }
        require(sourceSha256.length == 64 && sourceSha256.all { it in "0123456789abcdef" })
        require(sourceByteSize >= 0)
        require(activeSessionEpoch >= 0)
        require(currentRevision == null || currentRevision.isNotBlank())
        require(availability != BookAvailability.READY || currentRevision != null)
    }
}

/** No absolute paths, URI schemes, platform separators, or traversal components. */
fun isManagedRelativePath(path: String): Boolean =
    path.isNotBlank() && !path.startsWith('/') &&
        path.none { it == '\\' || it == ':' || it.code < 32 } &&
        path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
