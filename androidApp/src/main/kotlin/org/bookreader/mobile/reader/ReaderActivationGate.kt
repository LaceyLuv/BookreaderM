package org.bookreader.mobile.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room 2.8.3 uses its database Job when acquiring a connection. Cancelling the
 * requesting Reader therefore cannot interrupt that wait. A ViewModel-owned
 * sibling performs activation, while the Reader's await remains cancellable.
 * Only one request may enter Room; cancelled queued requests release their jobs.
 * The operation must check the original request after acquiring its connection
 * and before committing, so a cancelled request cannot mutate durable state.
 */
class ReaderActivationGate {
    private val admission = Mutex()

    suspend fun <T> run(owner: CoroutineScope, operation: suspend (checkActive: () -> Unit) -> T): T {
        val request = currentCoroutineContext()
        val work = owner.async(Dispatchers.IO) {
            admission.withLock {
                request.ensureActive()
                operation { request.ensureActive() }
            }
        }
        try { return work.await() }
        finally { work.cancel() }
    }
}
