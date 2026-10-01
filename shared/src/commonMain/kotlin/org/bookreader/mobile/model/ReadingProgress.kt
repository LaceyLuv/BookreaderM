package org.bookreader.mobile.model

import org.bookreader.mobile.locator.ContentLocator

/** percent is 0..100, or null when it cannot yet be computed. It is never the location. */
data class ReadingProgress(
    val bookId: String,
    val locator: ContentLocator,
    val percent: Double?,
    val writerSessionEpoch: Long,
    val writerSequence: Long,
    val updatedAt: Long,
) {
    val contentRevision: String get() = locator.contentRevision

    init {
        require(bookId.isNotBlank())
        require(percent == null || (percent.isFinite() && percent in 0.0..100.0))
        require(writerSessionEpoch >= 0 && writerSequence >= 0)
    }
}
