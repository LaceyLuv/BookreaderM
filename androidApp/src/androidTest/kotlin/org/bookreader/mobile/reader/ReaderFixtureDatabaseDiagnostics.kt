package org.bookreader.mobile.reader

import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.repository.ProgressReadResult

/** The repository error remains a failure even when the subsequent diagnostic query succeeds. */
internal suspend fun fixtureProgressReadFailed(
    database: BookReaderDatabase,
    bookId: String,
    revision: String,
    error: ProgressReadResult.Error,
): Nothing {
    val failure = IllegalStateException("Fixture progress read failed: ${error.code}")
    try {
        database.progressDao().findProgress(bookId, revision)
        database.bookDao().findBook(bookId)
    } catch (diagnostic: Exception) {
        failure.addSuppressed(diagnostic)
    }
    throw failure
}
