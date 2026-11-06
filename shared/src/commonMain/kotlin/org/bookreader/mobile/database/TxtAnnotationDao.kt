package org.bookreader.mobile.database

import androidx.room.*

@Entity(tableName = "txt_annotations", primaryKeys = ["bookId", "contentRevision", "id"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class TxtAnnotationEntity(
    val bookId: String, val contentRevision: String, val id: String, val schemaVersion: Int,
    val startOffset: Long, val endOffset: Long, val quote: String, val color: String, val note: String, val changeId: String,
)

@Dao
interface TxtAnnotationDao {
    /** Bound even corrupt/future rows before materializing their text in application memory. */
    @Query("SELECT EXISTS(SELECT 1 FROM txt_annotations WHERE bookId = :bookId AND contentRevision = :revision AND (length(quote) > 4096 OR length(note) > 2000 OR length(id) > 128 OR length(changeId) > 128 OR length(color) > 32))")
    suspend fun hasOversizedFields(bookId: String, revision: String): Boolean
    @Query("SELECT * FROM txt_annotations WHERE bookId = :bookId AND contentRevision = :revision ORDER BY startOffset, id LIMIT 501")
    suspend fun entries(bookId: String, revision: String): List<TxtAnnotationEntity>
    @Upsert suspend fun save(entry: TxtAnnotationEntity)
    @Query("DELETE FROM txt_annotations WHERE bookId = :bookId AND contentRevision = :revision AND id = :id")
    suspend fun delete(bookId: String, revision: String, id: String)
}
