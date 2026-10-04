package org.bookreader.mobile.importing

import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.BookEntity
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.ImportJobEntity
import org.bookreader.mobile.database.ReadingProgressEntity
import org.bookreader.mobile.database.createJvmDatabase
import org.bookreader.mobile.locator.LocatorCodec
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.locator.TxtLocatorPayload
import org.bookreader.mobile.repository.ProgressSession
import org.bookreader.mobile.repository.ProgressWriteEvent
import org.bookreader.mobile.repository.ProgressWriteResult
import org.bookreader.mobile.repository.ReadySessionResult
import org.bookreader.mobile.repository.RoomProgressWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportCoordinatorTest {
    @Test fun durableCopyDoesNotDependOnSourceAndDuplicateKeepsProgress() = fixture { f ->
        val first = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request("../private/name.txt")))
        assertEquals("../private/name", first.book.title)
        assertTrue(first.book.managedRelativePath.startsWith("books/"))
        assertTrue(!first.book.managedRelativePath.contains("private"))
        val progress = f.progress(first.book.id, first.book.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        val second = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request("different.txt")))
        assertTrue(second.duplicate)
        assertEquals(first.book, second.book)
        assertEquals(progress, f.db.progressDao().findProgress(first.book.id, first.book.currentRevision!!))
        Files.delete(f.source)
        assertEquals(f.payload.toList(), Files.readAllBytes(f.files.resolve(first.book.managedRelativePath)).toList())
        assertEquals(1, f.db.bookDao().listBooks().size)
        f.coordinator().recover()
        assertEquals(progress, f.db.progressDao().findProgress(first.book.id, first.book.currentRevision!!))
    }

    @Test fun interruptedCheckpointsRecoverIdempotentlyAndPreserveExistingBook() = fixture { f ->
        val existing = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val progress = f.progress(existing.id, existing.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        for (checkpoint in listOf(ImportCheckpoint.COPYING, ImportCheckpoint.COPIED, ImportCheckpoint.VALIDATED, ImportCheckpoint.FINALIZING, ImportCheckpoint.PROMOTED, ImportCheckpoint.COMMITTED)) {
            Files.writeString(f.source, "different self-authored text $checkpoint\r\n한글 😀")
            assertFailsWith<ProcessDeath> { f.coordinator(checkpoint).importTxt(f.request()) }
            f.coordinator().recover()
            val books = f.db.bookDao().listBooks()
            f.coordinator().recover()
            assertEquals(books, f.db.bookDao().listBooks())
            assertEquals(progress, f.db.progressDao().findProgress(existing.id, existing.currentRevision!!))
            assertTrue(Files.exists(f.files.resolve(existing.managedRelativePath)))
            assertTrue(f.db.importDao().listJobs().none { it.state in setOf("NEW", "COPYING", "VALIDATING", "FINALIZING") })
        }
        // COPYING dies before bytes are complete; all later checkpoints recover one committed book.
        assertEquals(6, f.db.bookDao().listBooks().size)
    }

    @Test fun deathWithActualPartialStagingBytesRecoversWithoutTouchingExistingProgress() = fixture { f ->
        val existing = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val progress = f.progress(existing.id, existing.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        Files.writeString(f.source, "Self-authored partial-copy fixture.\n".repeat(20_000))
        assertFailsWith<ProcessDeath> {
            f.coordinator().importTxt(f.request()) { if (it.phase == ImportPhase.COPYING) throw ProcessDeath() }
        }
        val interrupted = f.db.importDao().listJobs().single { it.state == "COPYING" }
        assertTrue(Files.size(f.files.resolve(interrupted.stagingPath)) in 1L until Files.size(f.source))
        f.coordinator().recover()
        f.coordinator().recover()
        assertTrue(!Files.exists(f.files.resolve(interrupted.stagingPath)))
        assertEquals(listOf(existing.id), f.db.bookDao().listBooks().map { it.id })
        assertEquals(progress, f.db.progressDao().findProgress(existing.id, existing.currentRevision!!))
    }

    @Test fun midStreamDiskAndPermissionFailuresCleanOnlyNewPartialBytes() = fixture { f ->
        val existing = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val progress = f.progress(existing.id, existing.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        Files.writeString(f.source, "Self-authored mid-stream fixture.\n".repeat(20_000))
        for (code in listOf(ImportErrorCode.DISK_FULL, ImportErrorCode.PERMISSION_DENIED)) {
            f.files.copyAfterBytesFailure = code
            assertEquals(ImportResult.Failure(code), f.coordinator().importTxt(f.request()))
            val failed = f.db.importDao().listJobs().last { it.failureCode == code.name }
            assertTrue(!Files.exists(f.files.resolve(failed.stagingPath)))
            assertEquals(progress, f.db.progressDao().findProgress(existing.id, existing.currentRevision!!))
            assertTrue(Files.exists(f.files.resolve(existing.managedRelativePath)))
        }
    }

    @Test fun encodingChoiceResumesPrivateStagingAfterTemporarySourceDisappears() = fixture { f ->
        Files.write(f.source, "한글 똠".toByteArray(java.nio.charset.Charset.forName("MS949")))
        f.files.requireChoice = true
        val pending = assertIs<ImportResult.EncodingRequired>(f.coordinator().importTxt(f.request()))
        Files.delete(f.source)
        val restored = assertIs<ImportResult.EncodingRequired>(f.coordinator().recover().single())
        assertEquals(pending.jobId, restored.jobId)
        val imported = assertIs<ImportResult.Success>(f.coordinator().resumeValidation(pending.jobId, "cp949"))
        assertEquals("cp949", imported.book.encodingId)
        assertEquals("COMMITTED", f.db.importDao().findJob(pending.jobId)!!.state)
        assertTrue(Files.exists(f.files.resolve(imported.book.managedRelativePath)))
    }

    @Test fun cancelledEncodingChoiceRemovesOnlyItsStaging() = fixture { f ->
        val original = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        f.files.requireChoice = true
        val pending = assertIs<ImportResult.EncodingRequired>(f.coordinator().importTxt(f.request()))
        assertTrue(f.coordinator().cancelImport(pending.jobId))
        assertTrue(!Files.exists(f.files.resolve(pending.stagingPath)))
        assertTrue(Files.exists(f.files.resolve(original.managedRelativePath)))
        f.coordinator().recover()
        assertEquals(original, f.db.bookDao().findBook(original.id)!!.toBook())
    }

    @Test fun cancellationDuringEncodingResumeNeverCommitsOnRecovery() = fixture { f ->
        f.files.requireChoice = true
        val pending = assertIs<ImportResult.EncodingRequired>(f.coordinator().importTxt(f.request()))
        f.files.validationCheckpoint = { currentCoroutineContext().cancel(); currentCoroutineContext().ensureActive() }
        var cancelled = false
        coroutineScope {
            val caller = launch {
                try { f.coordinator().resumeValidation(pending.jobId, "utf-8") }
                catch (_: CancellationException) { cancelled = true }
            }
            caller.join()
        }
        assertTrue(cancelled)
        f.files.validationCheckpoint = {}
        f.coordinator().recover()
        assertEquals("CANCELLED", f.db.importDao().findJob(pending.jobId)!!.state)
        assertTrue(f.db.bookDao().listBooks().isEmpty())
        assertTrue(!Files.exists(f.files.resolve(pending.stagingPath)))
    }

    @Test fun cancellationDuringCopyKeepsPreviouslyCommittedBookAndCleansStage() = fixture { f ->
        val original = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        assertFailsWith<CancellationException> {
            f.coordinator().importTxt(f.request()) { if (it.phase == ImportPhase.COPYING) throw CancellationException("fixture cancellation") }
        }
        f.coordinator().recover()
        assertEquals(listOf(original.id), f.db.bookDao().listBooks().map { it.id })
        assertTrue(Files.exists(f.files.resolve(original.managedRelativePath)))
        val cancelled = f.db.importDao().listJobs().single { it.state == "CANCELLED" }
        assertTrue(!Files.exists(f.files.resolve(cancelled.stagingPath)))
    }

    @Test fun failedPostCommitCleanupStillReportsSuccessAndRecoveryRetries() = fixture { f ->
        f.files.removeFailure = true
        val success = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request()))
        assertEquals("COMMITTED", f.db.importDao().listJobs().single().state)
        assertEquals(ImportErrorCode.FILE_IO.name, f.db.importDao().listJobs().single().failureCode)
        f.files.removeFailure = false
        f.coordinator().recover()
        assertTrue(Files.exists(f.files.resolve(success.book.managedRelativePath)))
        assertEquals(success.book, f.db.bookDao().findBook(success.book.id)!!.toBook())
    }

    @Test fun classifiedFailuresAndUnknownSizeKeepCommittedData() = fixture { f ->
        val original = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val progress = f.progress(original.id, original.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        for (code in listOf(ImportErrorCode.DISK_FULL, ImportErrorCode.PERMISSION_DENIED, ImportErrorCode.SOURCE_UNAVAILABLE)) {
            f.files.copyFailure = code
            assertEquals(ImportResult.Failure(code), f.coordinator().importTxt(f.request()))
            assertEquals(progress, f.db.progressDao().findProgress(original.id, original.currentRevision!!))
            assertTrue(Files.exists(f.files.resolve(original.managedRelativePath)))
        }
        f.files.copyFailure = null
        val calls = f.files.copyCalls
        assertEquals(ImportResult.Failure(ImportErrorCode.TOO_LARGE), f.coordinator().importTxt(f.request().copy(expectedBytes = MAX_TXT_BYTES + 1)))
        assertEquals(calls, f.files.copyCalls)
        Files.writeString(f.source, "unknown length fixture")
        var copied = 0L
        val unknown = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request().copy(expectedBytes = null)) { copied = maxOf(copied, it.bytesCopied) })
        assertTrue(copied > 0)
        assertEquals(Files.size(f.source), unknown.book.sourceByteSize)
    }

    @Test fun actualUnknownLengthOverLimitStopsCopyWithoutCommitting() = fixture { f ->
        java.io.RandomAccessFile(f.source.toFile(), "rw").use { it.setLength(MAX_TXT_BYTES + 1) }
        assertEquals(ImportResult.Failure(ImportErrorCode.TOO_LARGE), f.coordinator().importTxt(f.request().copy(expectedBytes = null)))
        assertTrue(f.db.bookDao().listBooks().isEmpty())
        val job = f.db.importDao().listJobs().single()
        assertEquals("FAILED", job.state)
        assertTrue(!Files.exists(f.files.resolve(job.stagingPath)))
    }

    @Test fun missingIdenticalCopyRelinksWithoutChangingInterpretationOrProgress() = fixture { f ->
        Files.writeString(f.source, "ASCII identical bytes remain interpretable in both encodings")
        val original = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val entity = f.db.bookDao().findBook(original.id)!!
        val progress = f.progress(original.id, original.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        Files.delete(f.files.resolve(original.managedRelativePath))
        f.db.importDao().updateBook(entity.copy(availability = "MISSING"))
        val restored = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request().copy(encodingId = "cp949"))).book
        assertEquals(original.id, restored.id)
        assertEquals(original.encodingId, restored.encodingId)
        assertEquals(original.currentRevision, restored.currentRevision)
        assertEquals(progress, f.db.progressDao().findProgress(original.id, original.currentRevision!!))
        assertTrue(Files.exists(f.files.resolve(restored.managedRelativePath)))
    }

    @Test fun readyRowWithDefinitelyAbsentCopyRelinksIdempotentlyAndKeepsProgress() = fixture { f ->
        val original = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val progress = f.progress(original.id, original.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        Files.delete(f.files.resolve(original.managedRelativePath))
        val relinked = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        assertEquals(original.id, relinked.id)
        assertEquals(original.currentRevision, relinked.currentRevision)
        assertEquals(original.encodingId, relinked.encodingId)
        assertEquals(original.lastReadAt, relinked.lastReadAt)
        assertEquals(original.readOrder, relinked.readOrder)
        assertTrue(relinked.activeSessionEpoch > original.activeSessionEpoch)
        assertEquals(progress, f.db.progressDao().findProgress(original.id, original.currentRevision!!))
        f.coordinator().recover()
        f.coordinator().recover()
        assertEquals(relinked, f.db.bookDao().findBook(original.id)!!.toBook())
        assertEquals(progress, f.db.progressDao().findProgress(original.id, original.currentRevision!!))
        assertEquals(1, f.db.bookDao().listBooks().size)
        assertEquals(relinked, assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book)
    }

    @Test fun changedExistingCopyIsNeverSilentlyOverwrittenByIdenticalReimport() = fixture { f ->
        val original = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val progress = f.progress(original.id, original.currentRevision!!)
        f.db.progressDao().insertProgress(progress)
        Files.writeString(f.files.resolve(original.managedRelativePath), "A different damaged managed file")
        assertEquals(ImportResult.Failure(ImportErrorCode.EXISTING_COPY_UNAVAILABLE), f.coordinator().importTxt(f.request()))
        assertEquals("A different damaged managed file", Files.readString(f.files.resolve(original.managedRelativePath)))
        assertEquals(original, f.db.bookDao().findBook(original.id)!!.toBook())
        assertEquals(progress, f.db.progressDao().findProgress(original.id, original.currentRevision!!))
    }

    @Test fun deletingInvalidatesWriterBeforeFileRemovalAndRecoveryNeverResurrects() = fixture { f ->
        val imported = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val writer = RoomProgressWriter(f.db) { 10L }
        val session = assertIs<ReadySessionResult.Ready>(writer.startReadySession(imported.id, imported.currentRevision!!, null)).session
        assertEquals(ProgressWriteResult.Committed, writer.save(ProgressWriteEvent(session, 1, TxtLocator(session.contentRevision, TxtLocatorPayload(9)), null)))
        assertFailsWith<ProcessDeath> { f.coordinator(ImportCheckpoint.DELETING).deleteBook(imported.id) }
        assertEquals("DELETING", f.db.bookDao().findBook(imported.id)!!.availability)
        assertEquals(ProgressWriteResult.RejectedStale, writer.save(ProgressWriteEvent(session, 2, TxtLocator(session.contentRevision, TxtLocatorPayload(20)), null)))
        f.coordinator().recover()
        f.coordinator().recover()
        assertNull(f.db.bookDao().findBook(imported.id))
        assertNull(f.db.progressDao().findProgress(imported.id, imported.currentRevision!!))
        assertTrue(!Files.exists(f.files.resolve(imported.managedRelativePath)))
        assertEquals(ProgressWriteResult.RejectedStale, writer.save(ProgressWriteEvent(session, 3, TxtLocator(session.contentRevision, TxtLocatorPayload(30)), null)))
    }

    @Test fun corruptedJournalCannotDeleteAnotherBooksReferencedFile() = fixture { f ->
        val original = assertIs<ImportResult.Success>(f.coordinator().importTxt(f.request())).book
        val id = UUID.randomUUID().toString()
        f.db.importDao().insertJob(ImportJobEntity(id, UUID.randomUUID().toString(), "imports/$id.part", original.managedRelativePath, "FINALIZING", "wrong.txt", 1, 1))
        assertTrue(f.coordinator().recover().any { it == ImportResult.Failure(ImportErrorCode.INVALID_JOURNAL) })
        assertTrue(Files.exists(f.files.resolve(original.managedRelativePath)))
        assertEquals(original, f.db.bookDao().findBook(original.id)!!.toBook())
        val second = UUID.randomUUID().toString()
        f.db.importDao().insertJob(ImportJobEntity(second, original.id, "imports/$second.part", original.managedRelativePath, "FINALIZING", "wrong.txt", 1, 1,
            bytesCopied = original.sourceByteSize, sourceSha256 = "b".repeat(64), encodingId = "utf-8", normalizationVersion = 1))
        assertTrue(f.coordinator().recover().any { it == ImportResult.Failure(ImportErrorCode.INVALID_JOURNAL) })
        assertTrue(Files.exists(f.files.resolve(original.managedRelativePath)))
        assertEquals(original, f.db.bookDao().findBook(original.id)!!.toBook())
    }

    private class ProcessDeath : Error()

    private class Fixture(val directory: Path, val db: BookReaderDatabase) {
        val payload = "Self-authored TXT fixture\r\n한글 😀\rEnd".toByteArray()
        val source = directory.resolve("source.txt").also { Files.write(it, payload) }
        val files = DiskFiles(directory.resolve("private"), source)
        fun request(name: String = "fixture.txt") = ImportRequest("fixture://temporary-grant", name)
        fun coordinator(crash: ImportCheckpoint? = null) = ImportCoordinator(db, files, { UUID.randomUUID().toString() }, { 100L }, ImportFaultHook { if (it == crash) throw ProcessDeath() })
        fun progress(bookId: String, revision: String) = ReadingProgressEntity(bookId, revision, "txt", 1, LocatorCodec().encode(TxtLocator(revision, TxtLocatorPayload(7))), 12.0, 1, 1, 1)
    }

    /** Actual disk I/O and SHA, not a fake repository; platform error injection is explicit. */
    private class DiskFiles(private val root: Path, private val source: Path) : ManagedImportFiles {
        var copyFailure: ImportErrorCode? = null
        var copyAfterBytesFailure: ImportErrorCode? = null
        var requireChoice = false
        var copyCalls = 0
        var removeFailure = false
        var validationCheckpoint: suspend () -> Unit = {}
        fun resolve(path: String): Path = root.resolve(path).normalize().also { require(it.startsWith(root) && !path.startsWith('/')) }
        override suspend fun copySource(request: ImportRequest, stagingPath: String, maxBytes: Long, onBytes: suspend (Long) -> Unit): FileFingerprint {
            copyCalls++
            copyFailure?.let { throw ImportFailure(it) }
            val destination = resolve(stagingPath)
            Files.createDirectories(destination.parent)
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            Files.newInputStream(source).use { input ->
                Files.newOutputStream(destination, java.nio.file.StandardOpenOption.CREATE_NEW).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (total + count > maxBytes) throw ImportFailure(ImportErrorCode.TOO_LARGE)
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        total += count
                        onBytes(total)
                        copyAfterBytesFailure?.let { throw ImportFailure(it) }
                    }
                }
            }
            return FileFingerprint(hex(digest.digest()), total)
        }
        override suspend fun validateTxt(relativePath: String, sourceSha256: String, encodingId: String?): TxtValidation {
            validationCheckpoint()
            if (requireChoice && encodingId == null) throw ImportFailure(ImportErrorCode.ENCODING_REQUIRED)
            val id = encodingId ?: "utf-8"
            val charset = java.nio.charset.Charset.forName(if (id == "cp949") "MS949" else id)
            val decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                Files.newInputStream(resolve(relativePath)).use { stream ->
                    java.io.InputStreamReader(stream, decoder).use { reader ->
                        val chars = CharArray(16 * 1024)
                        while (reader.read(chars) >= 0) currentCoroutineContext().ensureActive()
                    }
                }
            } catch (_: java.nio.charset.CharacterCodingException) { throw ImportFailure(ImportErrorCode.INVALID_TEXT) }
            return TxtValidation(id, 1)
        }
        override suspend fun fingerprint(relativePath: String, maxBytes: Long): FileFingerprint? {
            val path = resolve(relativePath)
            if (!Files.exists(path)) return null
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw ImportFailure(ImportErrorCode.TOO_LARGE)
                    digest.update(buffer, 0, count)
                }
            }
            return FileFingerprint(hex(digest.digest()), total)
        }
        override suspend fun promote(stagingPath: String, finalRelativePath: String) {
            val destination = resolve(finalRelativePath)
            Files.createDirectories(destination.parent)
            Files.createLink(destination, resolve(stagingPath))
            Files.delete(resolve(stagingPath))
        }
        override suspend fun remove(relativePath: String) {
            if (removeFailure) throw ImportFailure(ImportErrorCode.FILE_IO)
            Files.deleteIfExists(resolve(relativePath))
        }
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun fixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("bookreader-import-")
        val db = createJvmDatabase(directory.resolve("db.sqlite"))
        try { block(Fixture(directory, db)) }
        finally {
            db.close()
            Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
