package org.bookreader.mobile.database

import androidx.room.*

@Entity(tableName = "bookmarks", foreignKeys = [ForeignKey(entity = BookEntity::class,
    parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["bookId", "contentRevision", "createdAt", "id"])])
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val contentRevision: String,
    val locatorType: String,
    val locatorVersion: Int,
    val locatorJson: String,
    val label: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND contentRevision = :revision AND (createdAt < :beforeTime OR (createdAt = :beforeTime AND id < :beforeId)) ORDER BY createdAt DESC, id DESC LIMIT 51")
    suspend fun page(bookId: String, revision: String, beforeTime: Long, beforeId: String): List<BookmarkEntity>
    @Query("SELECT * FROM books WHERE id = :bookId") suspend fun owner(bookId: String): BookEntity?
    @Insert suspend fun insert(value: BookmarkEntity)
    @Query("DELETE FROM bookmarks WHERE id = :id AND bookId = :bookId")
    suspend fun delete(bookId: String, id: String): Int

    @Transaction suspend fun insertForSession(value: BookmarkEntity, epoch: Long): Boolean {
        val book = owner(value.bookId) ?: return false
        if (book.availability != "READY" || book.currentRevision != value.contentRevision ||
            book.activeSessionEpoch != epoch || book.format.lowercase() != value.locatorType) return false
        insert(value)
        return true
    }
}
