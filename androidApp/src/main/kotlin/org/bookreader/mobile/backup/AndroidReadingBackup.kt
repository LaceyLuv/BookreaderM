package org.bookreader.mobile.backup

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bookreader.mobile.database.AndroidDatabaseOwner

/** Handles transient system-picker grants. No path or document URI is stored or logged. */
class AndroidReadingBackup(context: Context) {
    private val app = context.applicationContext
    private val repository by lazy {
        RoomReadingBackup(AndroidDatabaseOwner.borrow(app), { UUID.randomUUID().toString() }, System::currentTimeMillis)
    }

    internal suspend fun prepareExport() = withContext(Dispatchers.IO) {
        val snapshot = repository.snapshot()
        val request = coroutineContext
        ReadingBackupFile.encode(snapshot, request::ensureActive)
    }

    internal suspend fun writeExport(uri: Uri, backup: ReadingBackupFile.EncodedReadingBackup) {
        document(uri, "wt", android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) { descriptor ->
            val request = coroutineContext
            descriptor.createOutputStream().use { output ->
                backup.writeTo(output, request::ensureActive)
                val fd = descriptor.parcelFileDescriptor.fileDescriptor
                if (OsConstants.S_ISREG(Os.fstat(fd).st_mode)) Os.fsync(fd)
            }
        }
    }

    suspend fun preview(uri: Uri): PreparedReadingBackup = withContext(Dispatchers.IO) {
        val snapshot = document(uri, "r", android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) { descriptor ->
            if (descriptor.length > ReadingBackupFile.MAX_FILE_BYTES) throw BackupFailure(BackupProblem.LIMIT)
            val request = coroutineContext
            descriptor.createInputStream().use { ReadingBackupFile.decode(it, request::ensureActive) }
        }
        repository.preview(snapshot)
    }

    suspend fun restore(prepared: PreparedReadingBackup, mode: BackupRestoreMode) = withContext(Dispatchers.IO) {
        repository.restore(prepared, mode)
    }

    private suspend fun <T> document(
        uri: Uri, mode: String, grant: Int, use: suspend kotlinx.coroutines.CoroutineScope.(AssetFileDescriptor) -> T,
    ): T = withContext(Dispatchers.IO) {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank() || uri.userInfo != null || uri.fragment != null ||
            app.checkUriPermission(uri, Process.myPid(), Process.myUid(), grant) != PackageManager.PERMISSION_GRANTED
        ) throw BackupFailure(BackupProblem.DOCUMENT_IO)
        withTimeout(60_000) {
            coroutineScope {
                val signal = CancellationSignal()
                val cancelOpen = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { signal.cancel() }
                }
                try {
                    val descriptor = app.contentResolver.openAssetFileDescriptor(uri, mode, signal)
                        ?: throw BackupFailure(BackupProblem.DOCUMENT_IO)
                    descriptor.use {
                        val closeOnCancel = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { runCatching { descriptor.close() } }
                        }
                        try {
                            use(descriptor)
                        } finally {
                            closeOnCancel.cancel()
                        }
                    }
                } finally {
                    cancelOpen.cancel()
                }
            }
        }
    }
}
