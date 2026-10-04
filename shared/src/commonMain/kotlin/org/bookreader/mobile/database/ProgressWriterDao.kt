package org.bookreader.mobile.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ProgressWriterDao {
    @Query("SELECT * FROM books WHERE id = :id") suspend fun findBook(id: String): BookEntity?
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId AND contentRevision = :revision")
    suspend fun findProgress(bookId: String, revision: String): ReadingProgressEntity?
    @Query("SELECT value FROM app_metadata WHERE `key` = 'read_order'") suspend fun readOrder(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setMetadata(value: AppMetadataEntity)
    @Update suspend fun updateBook(book: BookEntity)
    @Insert suspend fun insertProgress(progress: ReadingProgressEntity)
    @Update suspend fun updateProgress(progress: ReadingProgressEntity)

    @Transaction
    suspend fun activate(bookId: String, revision: String, expected: ReadingProgressEntity?, now: Long): Long? {
        val book = findBook(bookId) ?: return null
        if (book.availability != "READY" || book.currentRevision != revision || findProgress(bookId, revision) != expected) return null
        val order = readOrder() ?: 0L
        check(order < Long.MAX_VALUE && book.activeSessionEpoch < Long.MAX_VALUE)
        val epoch = book.activeSessionEpoch + 1
        updateBook(book.copy(activeSessionEpoch = epoch, lastReadAt = now, readOrder = order + 1, updatedAt = now))
        setMetadata(AppMetadataEntity("read_order", order + 1))
        return epoch
    }

    @Transaction
    suspend fun commit(progress: ReadingProgressEntity): Boolean {
        val book = findBook(progress.bookId) ?: return false
        if (book.availability != "READY" || book.currentRevision != progress.contentRevision || book.activeSessionEpoch != progress.writerSessionEpoch) return false
        val previous = findProgress(progress.bookId, progress.contentRevision)
        if (previous != null && (previous.writerSessionEpoch > progress.writerSessionEpoch ||
            (previous.writerSessionEpoch == progress.writerSessionEpoch && previous.writerSequence >= progress.writerSequence))) return false
        if (previous == null) insertProgress(progress) else updateProgress(progress)
        return true
    }
}
