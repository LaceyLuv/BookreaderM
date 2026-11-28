package org.bookreader.mobile.database

import androidx.room.*

@Entity(tableName = "txt_toc_overrides", primaryKeys = ["bookId", "contentRevision", "offset"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)])
data class TxtTocOverrideEntity(
    val bookId: String, val contentRevision: String, val offset: Long, val schemaVersion: Int,
    val title: String?, val excluded: Int, val changeId: String,
)

@Dao
interface TxtTocDao {
    @Query("SELECT * FROM txt_toc_overrides WHERE bookId = :bookId AND contentRevision = :revision ORDER BY offset LIMIT 1001")
    suspend fun edits(bookId: String, revision: String): List<TxtTocOverrideEntity>
    @Upsert suspend fun save(edit: TxtTocOverrideEntity)
    @Query("DELETE FROM txt_toc_overrides WHERE bookId = :bookId AND contentRevision = :revision AND offset = :offset")
    suspend fun delete(bookId: String, revision: String, offset: Long)
}
