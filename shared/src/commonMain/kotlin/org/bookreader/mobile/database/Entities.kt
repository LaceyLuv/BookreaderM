package org.bookreader.mobile.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.model.BookFormat

@Entity(tableName = "books", indices = [Index(value = ["sourceSha256", "sourceByteSize"], unique = true)])
data class BookEntity(
    @PrimaryKey val id: String,
    val format: String,
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
    val availability: String = BookAvailability.READY.name,
    val coverKey: String? = null,
    val lastOpenedAt: Long? = null,
    val lastReadAt: Long? = null,
    val readOrder: Long? = null,
    val activeSessionEpoch: Long = 0,
) {
    fun toBook(): Book = Book(
        id = id, format = BookFormat.valueOf(format), title = title,
        originalDisplayName = originalDisplayName, managedRelativePath = managedRelativePath,
        sourceSha256 = sourceSha256, sourceByteSize = sourceByteSize, addedAt = addedAt,
        createdAt = createdAt, updatedAt = updatedAt, author = author, sourceUri = sourceUri,
        currentRevision = currentRevision, encodingId = encodingId,
        normalizationVersion = normalizationVersion, availability = BookAvailability.valueOf(availability),
        coverKey = coverKey, lastOpenedAt = lastOpenedAt, lastReadAt = lastReadAt,
        readOrder = readOrder, activeSessionEpoch = activeSessionEpoch,
    )
}

@Entity(
    tableName = "reading_progress",
    primaryKeys = ["bookId", "contentRevision"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["bookId"])],
)
data class ReadingProgressEntity(
    val bookId: String,
    val contentRevision: String,
    val locatorType: String,
    val locatorVersion: Int,
    val locatorJson: String,
    val percent: Double?,
    val writerSessionEpoch: Long,
    val writerSequence: Long,
    val updatedAt: Long,
)
