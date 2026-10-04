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
    @Query("SELECT * FROM books WHERE availability != 'DELETING' ORDER BY addedAt DESC, id ASC")
    suspend fun listBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findBook(id: String): BookEntity?

    /** ABORT is intentional: import must not replace existing rows or cascade away progress. */
    @Insert
    suspend fun insertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBook(id: String)

    /** Delayed reader failure callbacks must provide the epoch captured before opening. */
    @Query("UPDATE books SET availability = :availability, activeSessionEpoch = activeSessionEpoch + 1, updatedAt = :now WHERE id = :id AND currentRevision = :revision AND availability = 'READY' AND :availability IN ('MISSING', 'CORRUPT', 'UNSUPPORTED') AND (:expectedEpoch IS NULL OR activeSessionEpoch = :expectedEpoch) AND activeSessionEpoch < 9223372036854775807")
    suspend fun markAvailability(id: String, revision: String, availability: String, now: Long, expectedEpoch: Long? = null): Int

    /** Only after the managed original's full SHA/size and restored display have been verified. */
    @Query("UPDATE books SET availability = 'READY', updatedAt = :now WHERE id = :id AND currentRevision = :revision AND sourceSha256 = :hash AND sourceByteSize = :bytes AND availability IN ('MISSING', 'CORRUPT')")
    suspend fun restoreVerifiedAvailability(id: String, revision: String, hash: String, bytes: Long, now: Long): Int
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId AND contentRevision = :contentRevision")
    suspend fun findProgress(bookId: String, contentRevision: String): ReadingProgressEntity?

    /** Foundation insertion only; Reader saves require the conditional writer in M03/M04. */
    @Insert
    suspend fun insertProgress(progress: ReadingProgressEntity)
}

@Database(entities = [BookEntity::class, ReadingProgressEntity::class, ImportJobEntity::class, AppMetadataEntity::class], version = 2, exportSchema = true)
@ConstructedBy(BookReaderDatabaseConstructor::class)
abstract class BookReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun progressDao(): ProgressDao
    abstract fun importDao(): ImportDao
    abstract fun progressWriterDao(): ProgressWriterDao
}

@Suppress("KotlinNoActualForExpect")
expect object BookReaderDatabaseConstructor : RoomDatabaseConstructor<BookReaderDatabase> {
    override fun initialize(): BookReaderDatabase
}
