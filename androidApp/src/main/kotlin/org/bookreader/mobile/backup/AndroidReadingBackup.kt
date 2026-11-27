package org.bookreader.mobile.backup

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.bookreader.mobile.database.AndroidDatabaseOwner
import org.bookreader.mobile.importing.withManagedBookOperations
import org.bookreader.mobile.reader.UserFontStore

/** Handles transient system-picker grants and v1/v2 backup streaming. No path or URI is stored or logged. */
class AndroidReadingBackup(
    context: Context,
    private val commitHook: BackupCommitHook = BackupCommitHook { _, _ -> },
) {
    private val app = context.applicationContext
    private val database by lazy { AndroidDatabaseOwner.borrow(app) }
    private val repository by lazy {
        RoomReadingBackup(database, { UUID.randomUUID().toString() }, System::currentTimeMillis)
    }
    private val storage by lazy { AndroidBackupAssetStorage(app) }
    private val coordinator by lazy {
        BackupAssetRestoreCoordinator(
            database = database,
            storage = storage,
            readingBackup = repository,
            newId = { UUID.randomUUID().toString() },
            now = System::currentTimeMillis,
            hook = commitHook,
        )
    }

    internal sealed interface ExportPlan {
        data class V1(val encoded: ReadingBackupFile.EncodedReadingBackup) : ExportPlan
        data class V2(val bundle: BackupBundleV2, val files: List<Pair<BackupAssetEntry, File>>) : ExportPlan
    }

    internal class PreparedBackupBundle(
        val prepared: PreparedReadingBackup,
        val assets: BackupAssetOverview,
        val sessionId: String?,
        val stagedAssets: List<StagedBackupAsset>,
    )

    internal suspend fun prepareExport(options: BackupExportOptions = BackupExportOptions()): ExportPlan =
        withContext(Dispatchers.IO) {
            val request = coroutineContext
            if (!options.includesAssets) {
                val snapshot = repository.snapshot()
                return@withContext ExportPlan.V1(ReadingBackupFile.encode(snapshot, request::ensureActive))
            }
            withManagedBookOperations {
                val snapshot = repository.snapshot()
                val entries = ArrayList<BackupAssetEntry>()
                val files = ArrayList<Pair<BackupAssetEntry, File>>()
                if (options.includeBooks) {
                    val readyByKey = database.bookDao().listBooks()
                        .filter { it.availability == "READY" }
                        .associateBy { BackupAssetRules.bookKey(it.sourceSha256, it.sourceByteSize) }
                    for (book in snapshot.books) {
                        request.ensureActive()
                        val row = readyByKey[BackupAssetRules.bookKey(book.sha256, book.byteSize)] ?: continue
                        val file = storage.managedBookFile(row.managedRelativePath)
                        if (!file.isFile || file.length() != book.byteSize) continue
                        val entry = BackupAssetEntry(
                            kind = BackupAssetKind.BOOK,
                            path = BackupAssetRules.canonicalPath(BackupAssetKind.BOOK, book.sha256),
                            sha256 = book.sha256,
                            byteSize = book.byteSize,
                            format = book.format,
                        )
                        entries.add(entry)
                        files.add(entry to file)
                    }
                }
                if (options.includeFonts) {
                    for (font in UserFontStore(app).list()) {
                        request.ensureActive()
                        val size = font.file.length()
                        if (!font.file.isFile || size !in BackupAssetRules.MIN_FONT_BYTES..BackupAssetRules.MAX_FONT_BYTES) continue
                        val entry = BackupAssetEntry(
                            kind = BackupAssetKind.FONT,
                            path = BackupAssetRules.canonicalPath(BackupAssetKind.FONT, font.id),
                            sha256 = font.id,
                            byteSize = size,
                            label = font.label,
                        )
                        entries.add(entry)
                        files.add(entry to font.file)
                    }
                }
                val bundle = BackupBundleV2(snapshot, entries)
                BackupAssetRules.validateBundle(bundle)
                ExportPlan.V2(bundle, files)
            }
        }

    internal suspend fun writeExport(uri: Uri, plan: ExportPlan) {
        val timeoutMs = if (plan is ExportPlan.V2) ASSET_IO_TIMEOUT_MS else RECORD_IO_TIMEOUT_MS
        document(uri, "wt", Intent.FLAG_GRANT_WRITE_URI_PERMISSION, timeoutMs) { descriptor ->
            val request = coroutineContext
            try {
                descriptor.createOutputStream().use { output ->
                    when (plan) {
                        is ExportPlan.V1 -> plan.encoded.writeTo(output, request::ensureActive)
                        is ExportPlan.V2 -> {
                            val sources = plan.files.map { (entry, file) ->
                                ReadingBackupFile.AssetSource(entry) { FileInputStream(file) }
                            }
                            ReadingBackupFile.writeBundleV2(plan.bundle, sources, output, request::ensureActive)
                        }
                    }
                    val fd = descriptor.parcelFileDescriptor.fileDescriptor
                    if (OsConstants.S_ISREG(Os.fstat(fd).st_mode)) Os.fsync(fd)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: BackupFailure) {
                throw failure
            } catch (failure: ErrnoException) {
                throw BackupFailure(if (failure.errno == OsConstants.ENOSPC) BackupProblem.DISK_FULL else BackupProblem.DOCUMENT_IO)
            } catch (failure: IOException) {
                val cause = failure.cause as? ErrnoException
                val full = cause?.errno == OsConstants.ENOSPC || "no space left" in failure.message.orEmpty().lowercase()
                throw BackupFailure(if (full) BackupProblem.DISK_FULL else BackupProblem.DOCUMENT_IO)
            }
        }
    }

    internal suspend fun previewBundle(uri: Uri): PreparedBackupBundle = withContext(Dispatchers.IO) {
        var createdSession: String? = null
        try {
            val decoded = document(uri, "r", Intent.FLAG_GRANT_READ_URI_PERMISSION, ASSET_IO_TIMEOUT_MS) { descriptor ->
                if (descriptor.length > MAX_ACCEPTED_BUNDLE_FILE_BYTES) throw BackupFailure(BackupProblem.LIMIT)
                val request = coroutineContext
                descriptor.createInputStream().use { input ->
                    ReadingBackupFile.decodeBundle(
                        input = input,
                        stageDirProvider = {
                            val id = UUID.randomUUID().toString()
                            createdSession = id
                            storage.createStagingSession(id)
                        },
                        reserveCheck = storage::ensureReserve,
                        checkpoint = request::ensureActive,
                    )
                }
            }
            when (decoded) {
                is ReadingBackupFile.DecodedBackup.V1 -> PreparedBackupBundle(
                    prepared = repository.preview(decoded.snapshot),
                    assets = BackupAssetOverview.RecordsOnlyV1,
                    sessionId = null,
                    stagedAssets = emptyList(),
                )
                is ReadingBackupFile.DecodedBackup.V2 -> {
                    val sessionId = createdSession ?: UUID.randomUUID().toString().also {
                        createdSession = it
                        storage.createStagingSession(it)
                    }
                    for (staged in decoded.staged) {
                        storage.validateStagedAsset(sessionId, staged, decoded.bundle.records)
                    }
                    storage.syncStagingSession(sessionId)
                    PreparedBackupBundle(
                        prepared = repository.preview(decoded.bundle.records),
                        assets = BackupAssetRules.overview(decoded.bundle),
                        sessionId = sessionId,
                        stagedAssets = decoded.staged,
                    )
                }
            }
        } catch (failure: Throwable) {
            createdSession?.let { runCatching { storage.deleteStagingSession(it) } }
            throw failure
        }
    }

    internal suspend fun discardPreview(bundle: PreparedBackupBundle) = withContext(Dispatchers.IO) {
        bundle.sessionId?.let { runCatching { storage.deleteStagingSession(it) } }
    }

    internal suspend fun restoreBundle(bundle: PreparedBackupBundle, mode: BackupRestoreMode): BackupAssetRestoreOutcome =
        withContext(Dispatchers.IO) {
            val sessionId = bundle.sessionId
            if (sessionId == null) {
                val records = repository.restore(bundle.prepared, mode)
                BackupAssetRestoreOutcome(records, promotedBooks = 0, installedFonts = 0, skippedFonts = 0)
            } else {
                coordinator.commit(bundle.prepared, mode, sessionId, bundle.stagedAssets)
            }
        }

    suspend fun recoverInterruptedRestore(): BackupRecoverySummary = withContext(Dispatchers.IO) {
        coordinator.recoverOrResume()
    }

    suspend fun hasPendingRestore(): Boolean = withContext(Dispatchers.IO) {
        coordinator.hasPendingJournal()
    }

    suspend fun discardInterruptedRestore() = withContext(Dispatchers.IO) {
        coordinator.discardPendingJournal()
    }

    suspend fun preview(uri: Uri): PreparedReadingBackup = previewBundle(uri).let { bundle ->
        if (bundle.sessionId != null) discardPreview(bundle)
        bundle.prepared
    }

    suspend fun restore(prepared: PreparedReadingBackup, mode: BackupRestoreMode) = withContext(Dispatchers.IO) {
        repository.restore(prepared, mode)
    }

    private suspend fun <T> document(
        uri: Uri,
        mode: String,
        grant: Int,
        timeoutMs: Long,
        use: suspend kotlinx.coroutines.CoroutineScope.(AssetFileDescriptor) -> T,
    ): T = withContext(Dispatchers.IO) {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank() || uri.userInfo != null || uri.fragment != null ||
            app.checkUriPermission(uri, Process.myPid(), Process.myUid(), grant) != PackageManager.PERMISSION_GRANTED
        ) throw BackupFailure(BackupProblem.DOCUMENT_IO)
        withTimeout(timeoutMs) {
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

    private companion object {
        const val RECORD_IO_TIMEOUT_MS = 60_000L
        const val ASSET_IO_TIMEOUT_MS = 300_000L
        const val MAX_ACCEPTED_BUNDLE_FILE_BYTES = BackupAssetRules.MAX_BUNDLE_BYTES + ReadingBackupFile.MAX_FILE_BYTES + (2_048L * 256L)
    }
}
