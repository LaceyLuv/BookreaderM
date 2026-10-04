package org.bookreader.mobile.importing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.ImportJobEntity
import org.bookreader.mobile.model.isManagedRelativePath

// All import/recovery/deletion instances in this process share file ownership and serialization.
private val managedOperations = Mutex()

class ImportCoordinator(
    private val database: BookReaderDatabase,
    private val files: ManagedImportFiles,
    private val newId: () -> String,
    private val now: () -> Long,
    private val faultHook: ImportFaultHook = ImportFaultHook { },
) {
    private val dao get() = database.importDao()

    suspend fun importTxt(request: ImportRequest, onProgress: suspend (ImportProgress) -> Unit = {}): ImportResult = managedOperations.withLock {
        currentCoroutineContext().ensureActive()
        if (request.expectedBytes != null && request.expectedBytes > MAX_TXT_BYTES) return@withLock ImportResult.Failure(ImportErrorCode.TOO_LARGE)
        if (request.expectedBytes != null && request.expectedBytes < 0) return@withLock ImportResult.Failure(ImportErrorCode.SOURCE_UNAVAILABLE)
        val id = checkedId(newId())
        val bookId = checkedId(newId())
        val name = request.displayName.filter { it.code >= 32 && it.code != 127 }.take(256).ifBlank { "텍스트 파일.txt" }
        var job = ImportJobEntity(id, bookId, "imports/$id.part", "books/$bookId/original", "NEW", name, now(), now(), request.sourceUri, request.expectedBytes, encodingId = request.encodingId)
        try {
            dao.insertJob(job)
            faultHook.reached(ImportCheckpoint.JOURNALED)
            job = job.copy(state = "COPYING", updatedAt = now()).also { dao.updateJob(it) }
            faultHook.reached(ImportCheckpoint.COPYING)
            val fingerprint = files.copySource(request, job.stagingPath, MAX_TXT_BYTES) { bytes ->
                currentCoroutineContext().ensureActive()
                if (bytes !in 0..MAX_TXT_BYTES) throw ImportFailure(ImportErrorCode.TOO_LARGE)
                onProgress(ImportProgress(ImportPhase.COPYING, bytes, request.expectedBytes))
            }
            job = job.copy(state = "VALIDATING", bytesCopied = fingerprint.byteSize, sourceSha256 = fingerprint.sha256, updatedAt = now()).also { dao.updateJob(it) }
            faultHook.reached(ImportCheckpoint.COPIED)
            validateAndFinish(job, onProgress)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { fail(job.id, "CANCELLED", ImportErrorCode.INTERRUPTED) }
            throw cancelled
        } catch (failure: ImportFailure) {
            fail(job.id, "FAILED", failure.code)
            ImportResult.Failure(failure.code)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // A failed commit is recoverable from FINALIZING; never delete its promoted file here.
            ImportResult.Failure(ImportErrorCode.DATABASE_UNAVAILABLE)
        }
    }

    suspend fun resumeValidation(jobId: String, encodingId: String, onProgress: suspend (ImportProgress) -> Unit = {}): ImportResult = managedOperations.withLock {
        val job = dao.findJob(jobId) ?: return@withLock ImportResult.Failure(ImportErrorCode.INVALID_JOURNAL)
        if (!owns(job) || job.state != "VALIDATING") return@withLock ImportResult.Failure(ImportErrorCode.INVALID_JOURNAL)
        if (encodingId !in setOf("utf-8", "utf-16le", "utf-16be", "cp949", "euc-kr")) return@withLock encodingRequired(job, ImportErrorCode.ENCODING_REQUIRED)
        val chosen = job.copy(encodingId = encodingId, failureCode = null, updatedAt = now()).also { dao.updateJob(it) }
        try { validateAndFinish(chosen, onProgress) }
        catch (cancelled: CancellationException) {
            withContext(NonCancellable) { fail(jobId, "CANCELLED", ImportErrorCode.INTERRUPTED) }
            throw cancelled
        }
        catch (failure: ImportFailure) { fail(jobId, "FAILED", failure.code); ImportResult.Failure(failure.code) }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); ImportResult.Failure(ImportErrorCode.DATABASE_UNAVAILABLE) }
    }

    suspend fun cancelImport(jobId: String): Boolean = managedOperations.withLock {
        val job = dao.findJob(jobId) ?: return@withLock false
        if (!owns(job) || job.state !in setOf("NEW", "COPYING", "VALIDATING")) return@withLock false
        fail(jobId, "CANCELLED", ImportErrorCode.INTERRUPTED)
        true
    }

    private suspend fun validateAndFinish(job: ImportJobEntity, onProgress: suspend (ImportProgress) -> Unit): ImportResult {
        onProgress(ImportProgress(ImportPhase.VALIDATING, job.bytesCopied, job.expectedBytes))
        val validation = try { files.validateTxt(job.stagingPath, job.sourceSha256!!, job.encodingId) }
        catch (failure: ImportFailure) {
            if (failure.code == ImportErrorCode.ENCODING_REQUIRED || failure.code == ImportErrorCode.INVALID_TEXT) {
                val pending = job.copy(failureCode = failure.code.name, updatedAt = now()).also { dao.updateJob(it) }
                return encodingRequired(pending, failure.code)
            }
            throw failure
        }
        val validated = job.copy(encodingId = validation.encodingId, normalizationVersion = validation.normalizationVersion, failureCode = null, updatedAt = now())
        dao.updateJob(validated)
        faultHook.reached(ImportCheckpoint.VALIDATED)
        currentCoroutineContext().ensureActive()
        val finalizing = validated.copy(state = "FINALIZING", updatedAt = now()).also { dao.updateJob(it) }
        faultHook.reached(ImportCheckpoint.FINALIZING)
        // Cancellation after this boundary cannot leave a user-visible success without a commit.
        return withContext(NonCancellable) {
            onProgress(ImportProgress(ImportPhase.FINALIZING, job.bytesCopied, job.expectedBytes))
            finish(finalizing)
        }
    }

    private suspend fun finish(job: ImportJobEntity): ImportResult.Success {
        check(owns(job))
        var duplicate = dao.findDuplicate(job.sourceSha256!!, job.bytesCopied)
        if (duplicate?.availability == "DELETING") throw ImportFailure(ImportErrorCode.BOOK_DELETING)
        if (duplicate?.availability == "READY") {
            val existing = duplicate
            val actual = files.fingerprint(existing.managedRelativePath, MAX_TXT_BYTES)
            if (actual == null) {
                // Only definite absence is relinkable. Permission/I/O/hash mismatch stays fail-closed.
                if (database.bookDao().markAvailability(existing.id, existing.currentRevision!!, "MISSING", now(), existing.activeSessionEpoch) != 1) throw ImportFailure(ImportErrorCode.EXISTING_COPY_UNAVAILABLE)
                duplicate = dao.findDuplicate(job.sourceSha256, job.bytesCopied)
            } else if (actual != FileFingerprint(job.sourceSha256, job.bytesCopied)) throw ImportFailure(ImportErrorCode.EXISTING_COPY_UNAVAILABLE)
        }
        if (duplicate?.availability != "READY") {
            if (duplicate != null && duplicate.availability != "MISSING") throw ImportFailure(ImportErrorCode.EXISTING_COPY_UNAVAILABLE)
            val finalFingerprint = files.fingerprint(job.finalRelativePath, MAX_TXT_BYTES)
            if (finalFingerprint != null && finalFingerprint != FileFingerprint(job.sourceSha256, job.bytesCopied)) throw ImportFailure(ImportErrorCode.INVALID_JOURNAL)
            if (finalFingerprint == null) files.promote(job.stagingPath, job.finalRelativePath)
            faultHook.reached(ImportCheckpoint.PROMOTED)
        }
        val candidate = BookEntity(
            id = job.proposedBookId, format = "TXT", title = job.displayName.substringBeforeLast('.').ifBlank { job.displayName },
            originalDisplayName = job.displayName, managedRelativePath = job.finalRelativePath,
            sourceSha256 = job.sourceSha256, sourceByteSize = job.bytesCopied,
            currentRevision = txtContentRevision(job.sourceSha256, job.encodingId!!, job.normalizationVersion!!),
            encodingId = job.encodingId, normalizationVersion = job.normalizationVersion, sourceUri = job.sourceUri,
            addedAt = job.createdAt, createdAt = job.createdAt, updatedAt = now(),
        )
        val committed = dao.commitImport(job.id, candidate, now())
        faultHook.reached(ImportCheckpoint.COMMITTED)
        try {
            removeUnreferenced(job.stagingPath)
            if (committed.managedRelativePath != job.finalRelativePath) removeUnreferenced(job.finalRelativePath)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // The transaction already committed. Cleanup remains retryable on the next recovery.
            try { dao.findJob(job.id)?.let { dao.updateJob(it.copy(failureCode = ImportErrorCode.FILE_IO.name)) } }
            catch (_: Exception) { currentCoroutineContext().ensureActive() }
        }
        return ImportResult.Success(committed.toBook(), committed.id != job.proposedBookId)
    }

    /** Returns pending encoding choices/recovered imports; interrupted source copies require reselection. */
    suspend fun recover(): List<ImportResult> = managedOperations.withLock {
        val outcomes = mutableListOf<ImportResult>()
        for (book in dao.deletingBooks()) finishDelete(book)
        for (job in dao.listJobs()) {
            currentCoroutineContext().ensureActive()
            if (!owns(job)) { outcomes += ImportResult.Failure(ImportErrorCode.INVALID_JOURNAL); continue }
            try {
                when (job.state) {
                    "FINALIZING" -> {
                        val path = if (files.fingerprint(job.finalRelativePath, MAX_TXT_BYTES) != null) job.finalRelativePath else job.stagingPath
                        val actual = files.fingerprint(path, MAX_TXT_BYTES)
                        if (job.sourceSha256 == null || actual != FileFingerprint(job.sourceSha256, job.bytesCopied) || job.encodingId == null || job.normalizationVersion == null) throw ImportFailure(ImportErrorCode.INVALID_JOURNAL)
                        val validation = files.validateTxt(path, job.sourceSha256, job.encodingId)
                        if (validation.encodingId != job.encodingId || validation.normalizationVersion != job.normalizationVersion) throw ImportFailure(ImportErrorCode.INVALID_JOURNAL)
                        outcomes += finish(job)
                    }
                    "VALIDATING" -> {
                        if (job.sourceSha256 != null && files.fingerprint(job.stagingPath, MAX_TXT_BYTES) == FileFingerprint(job.sourceSha256, job.bytesCopied)) {
                            outcomes += validateAndFinish(job, {})
                        } else { fail(job.id, "FAILED", ImportErrorCode.INTERRUPTED); outcomes += ImportResult.Failure(ImportErrorCode.INTERRUPTED) }
                    }
                    "NEW", "COPYING" -> { fail(job.id, "FAILED", ImportErrorCode.INTERRUPTED); outcomes += ImportResult.Failure(ImportErrorCode.INTERRUPTED) }
                    "COMMITTED", "FAILED", "CANCELLED" -> {
                        // COMMITTED is terminal, including after deletion: it never recreates a book.
                        removeUnreferenced(job.stagingPath)
                        removeUnreferenced(job.finalRelativePath)
                    }
                    else -> outcomes += ImportResult.Failure(ImportErrorCode.INVALID_JOURNAL)
                }
            } catch (failure: ImportFailure) {
                fail(job.id, "FAILED", failure.code)
                outcomes += ImportResult.Failure(failure.code)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); outcomes += ImportResult.Failure(ImportErrorCode.DATABASE_UNAVAILABLE) }
        }
        outcomes
    }

    suspend fun deleteBook(bookId: String, stopReading: suspend () -> Unit = {}): DeleteResult = managedOperations.withLock {
        try {
            val book = dao.beginDelete(bookId, now()) ?: return@withLock DeleteResult.Missing
            faultHook.reached(ImportCheckpoint.DELETING)
            stopReading()
            finishDelete(book)
            DeleteResult.Deleted
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: ImportFailure) { DeleteResult.Failure(failure.code) }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); DeleteResult.Failure(ImportErrorCode.DATABASE_UNAVAILABLE) }
    }

    private suspend fun finishDelete(book: BookEntity) {
        if (!isBookPath(book.managedRelativePath)) throw ImportFailure(ImportErrorCode.INVALID_JOURNAL)
        // A corrupt shared path must never cause another book's file to be removed.
        if (dao.references(book.managedRelativePath) != 1) throw ImportFailure(ImportErrorCode.INVALID_JOURNAL)
        files.remove(book.managedRelativePath)
        faultHook.reached(ImportCheckpoint.FILE_REMOVED)
        dao.finishDelete(book.id)
    }

    private suspend fun fail(id: String, state: String, code: ImportErrorCode) {
        try {
            val job = dao.findJob(id) ?: return
            if (!owns(job) || job.state == "COMMITTED") return
            dao.updateJob(job.copy(state = state, failureCode = code.name, updatedAt = now()))
            try { removeUnreferenced(job.stagingPath) }
            finally { removeUnreferenced(job.finalRelativePath) }
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // Preserve the original failure/cancellation. Journal recovery retries failed cleanup.
        }
    }

    private suspend fun removeUnreferenced(path: String) { if (dao.references(path) == 0) files.remove(path) }
    private fun encodingRequired(job: ImportJobEntity, code: ImportErrorCode) = ImportResult.EncodingRequired(job.id, job.stagingPath, job.displayName, code)
    private fun owns(job: ImportJobEntity): Boolean = validId(job.id) && validId(job.proposedBookId) &&
        job.stagingPath == "imports/${job.id}.part" && job.finalRelativePath == "books/${job.proposedBookId}/original"
    private fun checkedId(value: String): String { require(validId(value)); return value }
    private fun validId(value: String): Boolean = value.length in 1..64 && value.all { it.isLetterOrDigit() && it.code < 128 || it == '-' }
    private fun isBookPath(path: String): Boolean = isManagedRelativePath(path) && path.split('/').let { it.size == 3 && it[0] == "books" && validId(it[1]) && it[2] == "original" }
}
