package org.bookreader.mobile.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.bookreader.mobile.importing.ImportErrorCode
import org.bookreader.mobile.importing.ImportFailure

@Dao
interface ImportDao {
    @Insert suspend fun insertJob(job: ImportJobEntity)
    @Update suspend fun updateJob(job: ImportJobEntity)
    @Query("SELECT * FROM import_jobs WHERE id = :id") suspend fun findJob(id: String): ImportJobEntity?
    @Query("SELECT * FROM import_jobs ORDER BY createdAt, id") suspend fun listJobs(): List<ImportJobEntity>
    @Query("SELECT * FROM books WHERE sourceSha256 = :hash AND sourceByteSize = :bytes")
    suspend fun findDuplicate(hash: String, bytes: Long): BookEntity?
    @Query("SELECT COUNT(*) FROM books WHERE managedRelativePath = :path") suspend fun references(path: String): Int
    @Query("SELECT * FROM books WHERE id = :id") suspend fun findBook(id: String): BookEntity?
    @Query("SELECT * FROM books WHERE availability = 'DELETING'") suspend fun deletingBooks(): List<BookEntity>
    @Insert suspend fun insertBook(book: BookEntity)
    @Update suspend fun updateBook(book: BookEntity)
    @Query("DELETE FROM books WHERE id = :id AND availability = 'DELETING'") suspend fun finishDelete(id: String)

    @Transaction
    suspend fun commitImport(jobId: String, candidate: BookEntity, now: Long): BookEntity {
        val job = findJob(jobId) ?: throw ImportFailure(ImportErrorCode.INVALID_JOURNAL)
        if (job.state != "FINALIZING") throw ImportFailure(ImportErrorCode.INVALID_JOURNAL)
        val existing = findDuplicate(candidate.sourceSha256, candidate.sourceByteSize)
        val committed = when {
            existing == null -> candidate.also { insertBook(it) }
            existing.availability == "READY" -> existing
            existing.availability == "MISSING" -> existing.copy(
                managedRelativePath = candidate.managedRelativePath,
                availability = "READY",
                currentRevision = existing.currentRevision ?: candidate.currentRevision,
                encodingId = existing.encodingId ?: candidate.encodingId,
                normalizationVersion = existing.normalizationVersion ?: candidate.normalizationVersion,
                updatedAt = now,
            ).also { updateBook(it) }
            existing.availability == "DELETING" -> throw ImportFailure(ImportErrorCode.BOOK_DELETING)
            else -> throw ImportFailure(ImportErrorCode.EXISTING_COPY_UNAVAILABLE)
        }
        updateJob(job.copy(state = "COMMITTED", committedBookId = committed.id, updatedAt = now))
        return committed
    }

    @Transaction
    suspend fun beginDelete(id: String, now: Long): BookEntity? {
        val book = findBook(id) ?: return null
        if (book.availability == "DELETING") return book
        check(book.activeSessionEpoch < Long.MAX_VALUE)
        return book.copy(availability = "DELETING", activeSessionEpoch = book.activeSessionEpoch + 1, updatedAt = now)
            .also { updateBook(it) }
    }
}
