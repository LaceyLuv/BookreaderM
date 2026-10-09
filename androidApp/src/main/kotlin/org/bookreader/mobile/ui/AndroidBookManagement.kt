package org.bookreader.mobile.ui

import android.content.Context
import java.util.UUID
import org.bookreader.mobile.database.AndroidDatabaseOwner
import org.bookreader.mobile.importing.AndroidManagedImportFiles
import org.bookreader.mobile.importing.ImportCoordinator
import org.bookreader.mobile.importing.ImportProgress
import org.bookreader.mobile.importing.ImportResult
import org.bookreader.mobile.importing.IncomingDocument

class AndroidBookManagement(context: Context) : BookManagement {
    private val app = context.applicationContext
    private val files = AndroidManagedImportFiles(app)

    private suspend fun <T> useCoordinator(action: suspend (ImportCoordinator) -> T): T {
        val database = AndroidDatabaseOwner.borrow(app)
        return action(ImportCoordinator(database, files, { UUID.randomUUID().toString() }, System::currentTimeMillis))
    }

    override suspend fun recover() = useCoordinator { it.recover() }
    override suspend fun import(document: IncomingDocument, onProgress: suspend (ImportProgress) -> Unit): ImportResult =
        useCoordinator { it.importTxt(files.request(document), onProgress) }
    override suspend fun selectEncoding(jobId: String, encodingId: String, onProgress: suspend (ImportProgress) -> Unit): ImportResult =
        useCoordinator { it.resumeValidation(jobId, encodingId, onProgress) }
    override suspend fun preview(stagingPath: String) = files.preview(stagingPath)
    override suspend fun cancelEncoding(jobId: String) { useCoordinator { it.cancelImport(jobId) } }
    override suspend fun markUnavailable(book: org.bookreader.mobile.model.Book, availability: org.bookreader.mobile.model.BookAvailability) {
        val db = AndroidDatabaseOwner.borrow(app)
        db.bookDao().markAvailability(book.id, requireNotNull(book.currentRevision), availability.name, System.currentTimeMillis(), book.activeSessionEpoch)
    }
    override suspend fun delete(bookId: String, stopReading: suspend () -> Unit) = useCoordinator { it.deleteBook(bookId, stopReading) }
}
