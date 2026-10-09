package org.bookreader.mobile.repository

/** Fixed operation names only: never book metadata, URI, SQL, exception messages or stack traces. */
enum class DatabaseFailureStage {
    OPEN_DATABASE, READ_PROGRESS, READ_BOOK, ACTIVATE_EXPECTED, VERIFY_AVAILABILITY,
    ACTIVATE, SAVE_BOOK, SAVE_COMMIT,
}

data class DatabaseFailureDiagnostic(
    val stage: DatabaseFailureStage,
    val exceptionTypes: List<String>,
)

/** A bounded snapshot which does not retain exceptions or their potentially private messages. */
fun failureTypes(failure: Throwable): List<String> {
    val types = mutableListOf<String>()
    var cause: Throwable? = failure
    repeat(4) {
        val current = cause ?: return types
        types += (current::class.simpleName ?: "Exception")
            .filter { it.isLetterOrDigit() || it == '_' || it == '$' }.take(64)
        cause = current.cause
    }
    return types
}

internal fun reportDatabaseFailure(
    observer: (DatabaseFailureStage, Exception) -> Unit,
    stage: DatabaseFailureStage,
    failure: Exception,
) {
    // Diagnostics must never change the repository's fail-closed result.
    try { observer(stage, failure) } catch (_: Exception) { }
}
