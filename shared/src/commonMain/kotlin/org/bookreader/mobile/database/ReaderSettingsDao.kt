package org.bookreader.mobile.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(tableName = "reader_global_settings")
data class ReaderGlobalSettingsEntity(
    @PrimaryKey val id: String = "global",
    val schemaVersion: Int,
    val settingsJson: String,
)

@Entity(tableName = "book_reader_preferences", foreignKeys = [ForeignKey(
    entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"],
    onDelete = ForeignKey.CASCADE,
)])
data class BookReaderPreferencesEntity(
    @PrimaryKey val bookId: String,
    val schemaVersion: Int,
    val preferencesJson: String,
)

data class ReaderSettingsRows(val global: ReaderGlobalSettingsEntity?, val book: BookReaderPreferencesEntity?)

@Dao
interface ReaderSettingsDao {
    @Query("SELECT * FROM reader_global_settings WHERE id = 'global'")
    suspend fun global(): ReaderGlobalSettingsEntity?
    @Query("SELECT * FROM book_reader_preferences WHERE bookId = :bookId")
    suspend fun book(bookId: String): BookReaderPreferencesEntity?
    @Query("SELECT availability FROM books WHERE id = :bookId")
    suspend fun availability(bookId: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putGlobal(value: ReaderGlobalSettingsEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putBook(value: BookReaderPreferencesEntity)
    @Query("DELETE FROM book_reader_preferences WHERE bookId = :bookId")
    suspend fun removeBook(bookId: String)

    suspend fun read(bookId: String): ReaderSettingsRows = ReaderSettingsRows(global(), book(bookId))

    /** Compare the exact bytes loaded by the editor; never overwrite a concurrent or unread value. */
    @Transaction suspend fun save(bookId: String, expected: ReaderSettingsRows,
        global: ReaderGlobalSettingsEntity, book: BookReaderPreferencesEntity?): Boolean {
        if (availability(bookId) != "READY" || read(bookId) != expected) return false
        require(global.id == "global" && (book == null || book.bookId == bookId))
        putGlobal(global)
        if (book == null) removeBook(bookId) else putBook(book)
        return true
    }
}
