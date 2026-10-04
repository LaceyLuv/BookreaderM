package org.bookreader.mobile.repository

import org.bookreader.mobile.locator.ContentLocator

data class ProgressSession(val bookId: String, val contentRevision: String, val sessionEpoch: Long)

sealed interface ReadySessionResult {
    data class Ready(val session: ProgressSession) : ReadySessionResult
    data class Error(val code: ProgressErrorCode) : ReadySessionResult
}

data class ProgressWriteEvent(
    val session: ProgressSession,
    val sequence: Long,
    val locator: ContentLocator,
    val percent: Double?,
)

sealed interface ProgressWriteResult {
    data object Committed : ProgressWriteResult
    data object RejectedStale : ProgressWriteResult
    data class Error(val code: ProgressErrorCode) : ProgressWriteResult
}

/** Call only after content display and locator restoration; null only for a successful Missing read. */
interface ProgressWriter {
    suspend fun startReadySession(bookId: String, contentRevision: String, restoredLocator: ContentLocator?): ReadySessionResult
    suspend fun save(event: ProgressWriteEvent): ProgressWriteResult
}
