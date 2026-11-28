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
    // SQLite TEXT length stops at NUL; BLOB length also bounds bytes after an embedded NUL.
    // Four bytes per permitted UTF-16 unit is a conservative bound for either DB encoding.
    @Query("SELECT EXISTS(SELECT 1 FROM txt_annotations WHERE bookId = :bookId AND contentRevision = :revision AND (length(quote) > 4096 OR length(note) > 2000 OR length(id) > 128 OR length(changeId) > 128 OR length(color) > 32 OR length(CAST(quote AS BLOB)) > 16384 OR length(CAST(note AS BLOB)) > 8000 OR length(CAST(id AS BLOB)) > 512 OR length(CAST(changeId AS BLOB)) > 512 OR length(CAST(color AS BLOB)) > 128))")
    suspend fun hasOversizedFields(bookId: String, revision: String): Boolean
    @Query("SELECT * FROM txt_annotations WHERE bookId = :bookId AND contentRevision = :revision ORDER BY startOffset, id LIMIT 501")
    suspend fun entries(bookId: String, revision: String): List<TxtAnnotationEntity>
    @Upsert suspend fun save(entry: TxtAnnotationEntity)
    @Query("DELETE FROM txt_annotations WHERE bookId = :bookId AND contentRevision = :revision AND id = :id")
    suspend fun delete(bookId: String, revision: String, id: String)
}
