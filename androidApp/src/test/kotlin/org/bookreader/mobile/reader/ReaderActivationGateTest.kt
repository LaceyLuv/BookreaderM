package org.bookreader.mobile.reader

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderActivationGateTest {
    @Test fun cancelledReaderReturnsBeforeDatabaseJobAndCannotActivateAfterRelease() = runBlocking {
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val databaseJob = SupervisorJob()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val exited = CompletableDeferred<Unit>()
        val writes = AtomicInteger()
        val gate = ReaderActivationGate()
        try {
            val request = async {
                gate.run(owner) { checkActive ->
                    try {
                        // Match Room's switch to a database-owned Job, including delayed acquisition.
                        withContext(Dispatchers.Default + databaseJob) {
                            entered.complete(Unit)
                            release.await()
                            checkActive()
                            writes.incrementAndGet()
                        }
                    } finally { exited.complete(Unit) }
                }
            }
            withTimeout(3_000) { entered.await(); request.cancelAndJoin() }
            // New requests queued behind the one native wait also cancel promptly.
            repeat(5) {
                val queued = async { gate.run(owner) { writes.incrementAndGet() } }
                withTimeout(3_000) { queued.cancelAndJoin() }
            }
            release.complete(Unit)
            withTimeout(3_000) { exited.await() }
            assertEquals(0, writes.get())
            assertEquals(1, withTimeout(3_000) { gate.run(owner) { checkActive ->
                checkActive(); writes.incrementAndGet()
            } })
        } finally { release.complete(Unit); owner.cancel(); databaseJob.cancel() }
    }
}
