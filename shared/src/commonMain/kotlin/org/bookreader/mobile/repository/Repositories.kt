package org.bookreader.mobile.repository

import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.ReadingProgress

enum class LibraryErrorCode { DATABASE_UNAVAILABLE, INVALID_RECORD }

sealed interface LibraryState {
    data object Loading : LibraryState
    data object Empty : LibraryState
    data class Content(val books: List<Book>) : LibraryState
    data class Error(val code: LibraryErrorCode) : LibraryState
}

interface BookRepository {
    suspend fun loadBooks(): LibraryState
}

enum class ProgressErrorCode {
    DATABASE_UNAVAILABLE, INVALID_RECORD, UNSUPPORTED_LOCATOR, REVISION_MISMATCH,
}

sealed interface ProgressReadResult {
    data class Found(val progress: ReadingProgress) : ProgressReadResult
    data object Missing : ProgressReadResult
    data class Error(val code: ProgressErrorCode) : ProgressReadResult
}

/** M00 exposes read access only. Conditional writer/restore gates belong to M03/M04. */
interface ProgressRepository {
    suspend fun loadProgress(bookId: String, contentRevision: String): ProgressReadResult
}
