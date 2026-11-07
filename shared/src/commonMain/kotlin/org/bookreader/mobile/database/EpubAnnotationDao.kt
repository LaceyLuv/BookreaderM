package org.bookreader.mobile.database

import androidx.room.*

@Entity(tableName="epub_annotations", primaryKeys=["bookId","contentRevision","id"],
    foreignKeys=[ForeignKey(entity=BookEntity::class,parentColumns=["id"],childColumns=["bookId"],onDelete=ForeignKey.CASCADE)])
data class EpubAnnotationEntity(val bookId:String,val contentRevision:String,val id:String,val schemaVersion:Int,
    val anchorJson:String,val locatorJson:String,val color:String,val note:String,val changeId:String)
@Dao interface EpubAnnotationDao {
    @Query("SELECT EXISTS(SELECT 1 FROM epub_annotations WHERE bookId=:bookId AND contentRevision=:revision AND (length(CAST(anchorJson AS BLOB))>65536 OR length(CAST(locatorJson AS BLOB))>65536 OR length(CAST(note AS BLOB))>8000 OR length(CAST(id AS BLOB))>512 OR length(CAST(changeId AS BLOB))>512 OR length(CAST(color AS BLOB))>128))")
    suspend fun hasOversizedFields(bookId:String,revision:String):Boolean
    @Query("SELECT * FROM epub_annotations WHERE bookId=:bookId AND contentRevision=:revision ORDER BY id LIMIT 501")
    suspend fun entries(bookId:String,revision:String):List<EpubAnnotationEntity>
    @Upsert suspend fun save(entry:EpubAnnotationEntity)
    @Query("DELETE FROM epub_annotations WHERE bookId=:bookId AND contentRevision=:revision AND id=:id")
    suspend fun delete(bookId:String,revision:String,id:String)
}
