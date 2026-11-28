package org.bookreader.mobile.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Restore only inserts missing rows. Ordinary reader update/epoch APIs remain separate. */
@Dao
interface BackupInsertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun bookmark(value: BookmarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun membership(value: ShelfMembershipEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun txtHighlight(value: TxtAnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun epubHighlight(value: EpubAnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun toc(value: TxtTocOverrideEntity): Long

    @Query("SELECT COUNT(*) FROM txt_annotations WHERE bookId = :bookId AND contentRevision = :revision")
    suspend fun txtHighlights(bookId: String, revision: String): Int

    @Query("SELECT COUNT(*) FROM epub_annotations WHERE bookId = :bookId AND contentRevision = :revision")
    suspend fun epubHighlights(bookId: String, revision: String): Int

    @Query("SELECT COUNT(*) FROM txt_toc_overrides WHERE bookId = :bookId AND contentRevision = :revision")
    suspend fun tocEdits(bookId: String, revision: String): Int
}
