package org.bookreader.mobile.database

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bookreader.mobile.importing.DeleteResult
import org.bookreader.mobile.importing.txtContentRevision
import org.bookreader.mobile.locator.TxtLocator
import org.bookreader.mobile.reader.TxtReaderController
import org.bookreader.mobile.reader.TxtReaderPhase
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.repository.ProgressReadResult
import org.bookreader.mobile.repository.RoomProgressRepository
import org.bookreader.mobile.ui.AndroidBookManagement
import org.bookreader.mobile.ui.androidLibrarySessionFactory
import org.bookreader.mobile.ui.androidReaderFactory
import org.junit.Assert.*
import org.junit.Test

/** Real Android Room pool/managed cache/writer regression; measured UI restoration is tested separately. */
class AndroidDatabaseOwnerTest {
    @Test fun concurrentBorrowersAndClosedLibraryLeasesPreserveFirstReaderAndSave() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val shared = AndroidDatabaseOwner.borrow(context)
        val borrowers = coroutineScope {
            List(12) { async(Dispatchers.IO) { AndroidDatabaseOwner.borrow(context.applicationContext) } }.awaitAll()
        }
        borrowers.forEach { assertSame(shared, it) }
        val id = UUID.randomUUID().toString()
        val bytes = ("Generated owner regression $id\n" + "한글 TXT line\n".repeat(100)).toByteArray()
        val hash = digest(bytes)
        val revision = txtContentRevision(hash, "utf-8", 1)
        val record = BookEntity(id, "TXT", "Owner regression fixture", "owner-fixture.txt",
            "books/$id/original", hash, bytes.size.toLong(), 1, 1, 1,
            currentRevision = revision, encodingId = "utf-8", normalizationVersion = 1)
        val original = File(File(context.filesDir, "managed"), record.managedRelativePath)
        check(requireNotNull(original.parentFile).mkdirs())
        original.writeBytes(bytes)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var controller: TxtReaderController? = null
        var inserted = false
        try {
            withContext(Dispatchers.IO) { shared.bookDao().insertBook(record) }
            inserted = true
            val createReader = androidReaderFactory(context)
            // Each freshly created reader must read, prepare and activate without a silent retry.
            repeat(3) { attempt ->
                val active = createReader(record.toBook(), scope)
                controller = active
                withContext(Dispatchers.IO) {
                    repeat(4) {
                        val lease = androidLibrarySessionFactory(context).open()
                        try { assertTrue(lease.repository.loadBooks() is LibraryState.Content) }
                        finally { lease.close() }
                    }
                }
                val restoring = withTimeout(10_000) { active.state.first {
                    it.phase == TxtReaderPhase.ERROR || (it.phase == TxtReaderPhase.RESTORING && it.cache.complete)
                } }
                assertEquals("First open failure: ${restoring.failureDiagnostic}", TxtReaderPhase.RESTORING, restoring.phase)
                assertEquals(if (attempt == 0) 0L else (attempt - 1) * 10L + 20L, restoring.targetOffset)
                active.restored(restoring.restoreGeneration)
                val ready = withTimeout(10_000) { active.state.first {
                    it.phase == TxtReaderPhase.READY || it.phase == TxtReaderPhase.ERROR
                } }
                assertEquals("Activation failure: ${ready.failureDiagnostic}", TxtReaderPhase.READY, ready.phase)
                active.visibleAnchor(attempt * 10L + 20L, userMovement = true)
                active.flush()
                assertFalse("Save failure: ${active.state.value.failureDiagnostic}", active.state.value.saveError)
                active.close()
                controller = null
                // Fresh independent observers remain supported and own only their own handles.
                withContext(Dispatchers.IO) {
                    val observer = createAndroidDatabase(context)
                    try {
                        val saved = RoomProgressRepository(observer).loadProgress(id, revision)
                        assertTrue("Independent observer failed: $saved", saved is ProgressReadResult.Found)
                        assertEquals(attempt * 10L + 20L,
                            ((saved as ProgressReadResult.Found).progress.locator as TxtLocator).payload.utf16Offset)
                    } finally { observer.close() }
                    assertSame(shared, AndroidDatabaseOwner.borrow(context))
                    assertNotNull(shared.bookDao().findBook(id))
                }
            }
        } finally {
            try { controller?.close() }
            finally {
                scope.cancel()
                if (inserted) withContext(Dispatchers.IO) {
                    assertEquals(DeleteResult.Deleted, AndroidBookManagement(context).delete(id))
                } else original.parentFile?.deleteRecursively()
                val cacheKey = digest(revision.toByteArray())
                File(context.cacheDir, "txt").listFiles()?.filter { it.name.startsWith(cacheKey) }?.forEach { it.delete() }
            }
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
