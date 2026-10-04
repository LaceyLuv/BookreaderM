package org.bookreader.mobile.ui

import org.bookreader.mobile.importing.DeleteResult
import org.bookreader.mobile.importing.ImportProgress
import org.bookreader.mobile.importing.ImportResult
import org.bookreader.mobile.importing.IncomingDocument

interface BookManagement {
    suspend fun recover(): List<ImportResult>
    suspend fun import(document: IncomingDocument, onProgress: suspend (ImportProgress) -> Unit): ImportResult
    suspend fun selectEncoding(jobId: String, encodingId: String, onProgress: suspend (ImportProgress) -> Unit): ImportResult
    suspend fun preview(stagingPath: String): List<org.bookreader.mobile.encoding.TxtEncodingPreview>
    suspend fun cancelEncoding(jobId: String)
    suspend fun markUnavailable(book: org.bookreader.mobile.model.Book, availability: org.bookreader.mobile.model.BookAvailability)
    suspend fun delete(bookId: String, stopReading: suspend () -> Unit = {}): DeleteResult
}

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class ConfirmShare(val document: IncomingDocument) : ImportUiState
    data class Working(val progress: ImportProgress? = null) : ImportUiState
    data class SelectEncoding(val jobId: String, val origin: org.bookreader.mobile.importing.DocumentOrigin, val previews: List<org.bookreader.mobile.encoding.TxtEncodingPreview> = emptyList()) : ImportUiState
    data class Complete(val result: ImportResult.Success) : ImportUiState
    data class Error(
        val message: String,
        val code: org.bookreader.mobile.importing.ImportErrorCode? = null,
        val phase: org.bookreader.mobile.importing.ImportPhase? = null,
    ) : ImportUiState
}
