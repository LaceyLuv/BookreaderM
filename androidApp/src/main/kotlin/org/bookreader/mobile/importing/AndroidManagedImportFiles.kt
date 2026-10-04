package org.bookreader.mobile.importing

import androidx.core.net.toUri
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.os.CancellationSignal
import android.system.StructPollfd
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import android.os.StatFs
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.encoding.TxtDecoder
import org.bookreader.mobile.encoding.TxtDecodingException
import org.bookreader.mobile.encoding.TxtEncoding

/** No source mutation, tree traversal, persisted grants, or broad storage permission. */
class AndroidManagedImportFiles(private val context: Context) : ManagedImportFiles {
    private val root = File(context.filesDir, "managed").apply { mkdirs() }

    suspend fun request(document: IncomingDocument, encodingId: String? = null): ImportRequest {
        requireAccess(document.uri)
        var name = "가져온 책.txt"
        var size: Long? = null
        try {
            cancellableProvider { signal -> context.contentResolver.query(document.uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, signal)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex).orEmpty().filter { it.code >= 32 }.take(240)
                            .ifBlank { "가져온 책.txt" }
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                    }
                }
            } }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: SecurityException) { currentCoroutineContext().ensureActive(); throw ImportFailure(ImportErrorCode.PERMISSION_DENIED) }
        catch (_: Exception) { throw ImportFailure(ImportErrorCode.SOURCE_UNAVAILABLE) }
        return ImportRequest(document.uri.toString(), name, size, encodingId)
    }

    private fun requireAccess(uri: Uri) {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank() || uri.userInfo != null) {
            throw ImportFailure(ImportErrorCode.PERMISSION_DENIED)
        }
        @Suppress("DEPRECATION")
        val provider = context.packageManager.resolveContentProvider(uri.authority!!, 0)
            ?: throw ImportFailure(ImportErrorCode.SOURCE_UNAVAILABLE)
        if (provider.applicationInfo.uid == Process.myUid() ||
            context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) throw ImportFailure(ImportErrorCode.PERMISSION_DENIED)
    }

    override suspend fun copySource(request: ImportRequest, stagingPath: String, maxBytes: Long,
        onBytes: suspend (Long) -> Unit,
    ): FileFingerprint {
        val uri = request.sourceUri.toUri()
        requireAccess(uri)
        if (request.expectedBytes?.let { it > maxBytes } == true) throw ImportFailure(ImportErrorCode.TOO_LARGE)
        if (request.expectedBytes?.let { it + RESERVE_BYTES > StatFs(root.path).availableBytes } == true) {
            throw ImportFailure(ImportErrorCode.DISK_FULL)
        }
        val target = owned(stagingPath)
        mapIo {
            if (!target.parentFile!!.mkdirs() && !target.parentFile!!.isDirectory) {
                throw ImportFailure(ImportErrorCode.FILE_IO)
            }
            if (!target.createNewFile()) throw ImportFailure(ImportErrorCode.FILE_IO)
        }
        return mapIo {
            cancellableProvider { signal ->
            val asset = try { context.contentResolver.openAssetFileDescriptor(uri, "r", signal) }
                catch (_: java.io.FileNotFoundException) {
                    currentCoroutineContext().ensureActive()
                    throw ImportFailure(ImportErrorCode.SOURCE_UNAVAILABLE)
                } ?: throw ImportFailure(ImportErrorCode.SOURCE_UNAVAILABLE)
            asset.use { descriptor ->
            val input = descriptor.createInputStream()
            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { runCatching { input.close() }; runCatching { descriptor.close() } }
            }
            try {
            input.use {
                FileOutputStream(target).use { output ->
                    val hash = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val poll = StructPollfd().apply {
                            fd = descriptor.parcelFileDescriptor.fileDescriptor
                            events = OsConstants.POLLIN.toShort()
                        }
                        if (Os.poll(arrayOf(poll), 200) == 0) continue
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        copied += count
                        if (copied > maxBytes) throw ImportFailure(ImportErrorCode.TOO_LARGE)
                        if (StatFs(root.path).availableBytes < count + RESERVE_BYTES) {
                            throw ImportFailure(ImportErrorCode.DISK_FULL)
                        }
                        output.write(buffer, 0, count)
                        hash.update(buffer, 0, count)
                        if (lastReported == 0L || copied - lastReported >= 256 * 1024) { onBytes(copied); lastReported = copied }
                    }
                    output.fd.sync()
                    syncDirectory(target.parentFile!!)
                    onBytes(copied)
                    FileFingerprint(hash.digest().hex(), copied)
                }
            }
            } finally { watcher.cancel() }
            }
            }
        }
    }

    private suspend fun <T> cancellableProvider(block: suspend kotlinx.coroutines.CoroutineScope.(CancellationSignal) -> T): T =
        withTimeout(60_000) {
            coroutineScope {
                val signal = CancellationSignal()
                val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { signal.cancel() }
                }
                try { block(signal) } finally { watcher.cancel() }
            }
        }

    override suspend fun validateTxt(relativePath: String, sourceSha256: String, encodingId: String?): TxtValidation {
        val coroutine = currentCoroutineContext()
        val encoding = encodingId?.let { TxtEncoding.fromId(it)
            ?: throw ImportFailure(ImportErrorCode.ENCODING_REQUIRED) }
        return try {
            FileInputStream(owned(relativePath)).use { input ->
                val valid = TxtDecoder.validate(input, encoding, cancellationCheck = { coroutine.ensureActive() })
                TxtValidation(valid.encoding.id, valid.normalizationVersion)
            }
        } catch (failure: TxtDecodingException) {
            throw ImportFailure(when (failure.code) {
                org.bookreader.mobile.encoding.TxtDecodeFailure.BINARY_CONTENT ->
                    if (encodingId == null && preview(relativePath).any { it.text != null }) ImportErrorCode.ENCODING_REQUIRED
                    else ImportErrorCode.UNSUPPORTED_FORMAT
                org.bookreader.mobile.encoding.TxtDecodeFailure.SOURCE_TOO_LARGE -> ImportErrorCode.TOO_LARGE
                org.bookreader.mobile.encoding.TxtDecodeFailure.TIME_BUDGET_EXCEEDED -> ImportErrorCode.FILE_IO
                else -> if (encodingId == null) ImportErrorCode.ENCODING_REQUIRED else ImportErrorCode.INVALID_TEXT
            })
        }
    }

    suspend fun preview(relativePath: String): List<org.bookreader.mobile.encoding.TxtEncodingPreview> {
        val coroutine = currentCoroutineContext()
        return FileInputStream(owned(relativePath)).use {
            TxtDecoder.probe(it, cancellationCheck = { coroutine.ensureActive() }).previews
        }
    }

    override suspend fun fingerprint(relativePath: String, maxBytes: Long): FileFingerprint? {
        val file = owned(relativePath)
        if (!file.exists()) return null
        return mapIo {
            FileInputStream(file).use { input ->
                val hash = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var size = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    if (size > maxBytes) throw ImportFailure(ImportErrorCode.TOO_LARGE)
                    hash.update(buffer, 0, count)
                }
                FileFingerprint(hash.digest().hex(), size)
            }
        }
    }

    override suspend fun promote(stagingPath: String, finalRelativePath: String) {
        val source = owned(stagingPath)
        val target = owned(finalRelativePath)
        mapIo {
            check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
            // All coordinator calls hold its process-wide managedOperations mutex. Paths are private
            // UUID-owned files, and no external provider can write this tree. Default Files.move
            // refuses an existing target; Android libcore then uses rename on this same filesystem.
            // Do not request ATOMIC_MOVE: its API permits replacing an existing destination.
            if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                Os.stat(source.path).st_dev != Os.stat(target.parentFile!!.path).st_dev
            ) throw ImportFailure(ImportErrorCode.FILE_IO)
            Files.move(source.toPath(), target.toPath())
            syncDirectory(target.parentFile!!)
            syncDirectory(source.parentFile!!)
        }
    }

    override suspend fun remove(relativePath: String) {
        val file = owned(relativePath)
        mapIo {
            if (file.exists() && !file.delete()) throw ImportFailure(ImportErrorCode.FILE_IO)
            file.parentFile?.let { parent ->
                if (parent.exists()) syncDirectory(parent)
                if (parent != root && parent.list()?.isEmpty() == true) parent.delete()
            }
        }
    }

    private fun owned(path: String): File {
        val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        require(path.matches(Regex("(?:imports/$uuid\\.part|books/$uuid/original)")))
        val file = File(root, path)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
        return file
    }

    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }

    private suspend fun <T> mapIo(block: suspend () -> T): T = try { block() }
    catch (_: SecurityException) { currentCoroutineContext().ensureActive(); throw ImportFailure(ImportErrorCode.PERMISSION_DENIED) }
    catch (failure: ErrnoException) {
        currentCoroutineContext().ensureActive()
        throw ImportFailure(if (failure.errno == OsConstants.ENOSPC) ImportErrorCode.DISK_FULL else ImportErrorCode.FILE_IO)
    } catch (failure: IOException) {
        currentCoroutineContext().ensureActive()
        val cause = failure.cause as? ErrnoException
        throw ImportFailure(if (cause?.errno == OsConstants.ENOSPC) ImportErrorCode.DISK_FULL else ImportErrorCode.FILE_IO)
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
    private companion object { const val RESERVE_BYTES = 1024L * 1024 }
}
