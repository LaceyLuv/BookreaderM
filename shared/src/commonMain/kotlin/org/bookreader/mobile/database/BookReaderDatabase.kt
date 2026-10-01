package org.bookreader.mobile.database

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC, id ASC")
    suspend fun listBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findBook(id: String): BookEntity?

    /** ABORT is intentional: import must not replace existing rows or cascade away progress. */
    @Insert
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: String)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId AND contentRevision = :contentRevision")
    suspend fun findProgress(bookId: String, contentRevision: String): ReadingProgressEntity?

    /** Foundation insertion only; Reader saves require the conditional writer in M03/M04. */
    @Insert
    suspend fun insertProgress(progress: ReadingProgressEntity)
}

@Database(entities = [BookEntity::class, ReadingProgressEntity::class], version = 1, exportSchema = true)
@ConstructedBy(BookReaderDatabaseConstructor::class)
abstract class BookReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
}

@Suppress("KotlinNoActualForExpect")
expect object BookReaderDatabaseConstructor : RoomDatabaseConstructor<BookReaderDatabase> {
    override fun initialize(): BookReaderDatabase
}
