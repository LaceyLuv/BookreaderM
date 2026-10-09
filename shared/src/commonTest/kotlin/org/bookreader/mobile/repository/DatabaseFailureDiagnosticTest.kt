package org.bookreader.mobile.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DatabaseFailureDiagnosticTest {
    @Test fun causeSnapshotIsBoundedAndExcludesMessages() {
        val secret = "content://private/book body and SQL"
        var failure: Throwable = IllegalArgumentException(secret)
        repeat(8) { failure = IllegalStateException(secret, failure) }
        val snapshot = DatabaseFailureDiagnostic(DatabaseFailureStage.READ_PROGRESS, failureTypes(failure))
        assertEquals(List(4) { "IllegalStateException" }, snapshot.exceptionTypes)
        assertFalse(snapshot.toString().contains(secret))
    }
}
