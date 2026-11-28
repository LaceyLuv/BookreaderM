package org.bookreader.mobile.backup

import android.content.Context
import android.graphics.Typeface
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.comic.ComicManifest
import org.bookreader.mobile.encoding.TxtDecoder
import org.bookreader.mobile.encoding.TxtEncoding
import org.bookreader.mobile.epub.SafeEpub
import org.bookreader.mobile.importing.FileFingerprint
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.reader.FontFilePolicy
import org.bookreader.mobile.reader.UserFontStore

/**
 * Private filesystem storage for v2 backup staging, durable commit journal, safe book promotion,
 * and deduplicated user-font registration. Never mutates existing READY books or source URIs.
 */
internal class AndroidBackupAssetStorage(context: Context) : BackupAssetStorage {
    private val app = context.applicationContext
    private val managedRoot = File(app.filesDir, "managed").apply { mkdirs() }
    private val stagingRoot = File(managedRoot, "backup-staging")
    private val journalRoot = File(app.filesDir, "backup-journal")
    private val journalFile = File(journalRoot, "active.json")
    private val fontsRoot = File(app.filesDir, "reader-fonts")
    private val fontStore = UserFontStore(app)

    fun managedBookFile(relativePath: String): File {
        require(relativePath.matches(managedBookPathPattern))
        val file = File(managedRoot, relativePath)
        require(file.canonicalPath.startsWith(managedRoot.canonicalPath + File.separator))
        return file
    }

    fun createStagingSession(sessionId: String): File = mapIoSync {
        val dir = stagingDirectory(sessionId)
        check(dir.mkdirs() || dir.isDirectory)
        syncDirectory(stagingRoot)
        dir
    }

    fun syncStagingSession(sessionId: String) = mapIoSync {
        val dir = stagingDirectory(sessionId)
        if (dir.isDirectory) syncDirectory(dir)
    }

    fun ensureReserve(bytes: Long) {
        val available = StatFs(managedRoot.path).availableBytes
        if (available < bytes + RESERVE_BYTES) throw BackupFailure(BackupProblem.DISK_FULL)
    }

    suspend fun validateStagedAsset(sessionId: String, asset: StagedBackupAsset, records: ReadingBackup) {
        val file = stagedAssetFile(sessionId, asset.stagingName)
        val request = currentCoroutineContext()
        request.ensureActive()
        when (asset.kind) {
            BackupAssetKind.FONT -> {
                try {
                    FontFilePolicy.validate(file)
                    requireNotNull(Typeface.Builder(file).build())
                } catch (_: IllegalArgumentException) {
                    throw BackupFailure(BackupProblem.FORMAT)
                } catch (_: IllegalStateException) {
                    throw BackupFailure(BackupProblem.FORMAT)
                }
            }
            BackupAssetKind.BOOK -> {
                val format = try {
                    BookFormat.valueOf(requireNotNull(asset.format))
                } catch (_: Exception) {
                    throw BackupFailure(BackupProblem.FORMAT)
                }
                when (format) {
                    BookFormat.TXT -> {
                        val book = records.books.firstOrNull { it.sha256 == asset.sha256 && it.byteSize == asset.byteSize }
                            ?: throw BackupFailure(BackupProblem.FORMAT)
                        val encoding = book.encoding?.let(TxtEncoding::fromId)
                            ?: throw BackupFailure(BackupProblem.FORMAT)
                        try {
                            FileInputStream(file).use { input ->
                                TxtDecoder.validate(input, encoding, cancellationCheck = request::ensureActive)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            throw BackupFailure(BackupProblem.FORMAT)
                        }
                    }
                    BookFormat.EPUB -> {
                        try {
                            val publication = SafeEpub.open(file)
                            publication.close()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            throw BackupFailure(BackupProblem.FORMAT)
                        }
                    }
                    BookFormat.COMIC -> {
                        try {
                            ComicManifest.inspect(file, asset.sha256, request::ensureActive)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            throw BackupFailure(BackupProblem.FORMAT)
                        }
                    }
                }
            }
        }
    }

    override suspend fun readJournal(): BackupRestoreJournal? = mapIo {
        if (!journalFile.isFile) return@mapIo null
        val text = journalFile.readText(Charsets.UTF_8)
        BackupRules.boundedJson(text, BackupRules.MAX_PAYLOAD_BYTES)
        BackupRules.json.decodeFromString<BackupRestoreJournal>(text)
    }

    override suspend fun writeJournal(journal: BackupRestoreJournal) = mapIo {
        val encoded = BackupRules.json.encodeToString(BackupRestoreJournal.serializer(), journal).encodeToByteArray()
        ensureReserve(encoded.size.toLong())
        check(journalRoot.mkdirs() || journalRoot.isDirectory)
        val tmp = File(journalRoot, "active.json.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(encoded)
            out.fd.sync()
        }
        Files.move(tmp.toPath(), journalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(journalRoot)
    }

    override suspend fun clearJournal() = mapIo {
        if (journalFile.exists()) check(journalFile.delete())
        File(journalRoot, "active.json.tmp").delete()
        if (journalRoot.isDirectory) syncDirectory(journalRoot)
    }

    override suspend fun listStagingSessions(): List<String> = mapIo {
        stagingRoot.listFiles().orEmpty()
            .filter { it.isDirectory && uuidPattern.matches(it.name) }
            .map { it.name }
            .sorted()
    }

    override suspend fun deleteStagingSession(sessionId: String) = mapIo {
        val dir = stagingDirectory(sessionId)
        if (dir.exists()) {
            dir.deleteRecursively()
            if (stagingRoot.isDirectory) syncDirectory(stagingRoot)
        }
    }

    override suspend fun managedBookFingerprint(relativePath: String, maxBytes: Long): FileFingerprint? {
        val file = managedBookFile(relativePath)
        if (!file.exists()) return null
        return fingerprint(file, maxBytes)
    }

    override suspend fun promoteBookAsset(sessionId: String, asset: StagedBackupAsset, finalRelativePath: String) {
        val source = stagedAssetFile(sessionId, asset.stagingName)
        val target = managedBookFile(finalRelativePath)
        val actual = fingerprint(source, asset.byteSize)
            ?: throw BackupFailure(BackupProblem.INTEGRITY)
        if (actual.sha256 != asset.sha256 || actual.byteSize != asset.byteSize) {
            throw BackupFailure(BackupProblem.INTEGRITY)
        }
        mapIo {
            check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
            // Exact ADR-0002 no-replace promotion contract: regular file, target absent, same filesystem.
            if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                Os.stat(source.path).st_dev != Os.stat(target.parentFile!!.path).st_dev
            ) {
                throw BackupFailure(BackupProblem.DOCUMENT_IO)
            }
            Files.move(source.toPath(), target.toPath())
            syncDirectory(target.parentFile!!)
            syncDirectory(source.parentFile!!)
        }
    }

    override suspend fun installFontAsset(sessionId: String, asset: StagedBackupAsset): FontInstallOutcome {
        val existing = fontStore.find(asset.sha256)
        if (existing != null) {
            val actual = fingerprint(existing.file, BackupAssetRules.MAX_FONT_BYTES)
            if (actual != null && actual.sha256 == asset.sha256 && actual.byteSize == asset.byteSize) {
                return FontInstallOutcome.ALREADY_PRESENT
            }
        }
        val installedList = fontStore.list()
        if (installedList.size >= BackupAssetRules.MAX_FONT_COUNT ||
            installedList.sumOf { it.file.length() } + asset.byteSize > BackupAssetRules.MAX_TOTAL_FONT_BYTES
        ) {
            return FontInstallOutcome.QUOTA_EXCEEDED
        }
        val source = stagedAssetFile(sessionId, asset.stagingName)
        val actual = fingerprint(source, BackupAssetRules.MAX_FONT_BYTES)
            ?: throw BackupFailure(BackupProblem.INTEGRITY)
        if (actual.sha256 != asset.sha256 || actual.byteSize != asset.byteSize) {
            throw BackupFailure(BackupProblem.INTEGRITY)
        }
        try {
            FontFilePolicy.validate(source)
            requireNotNull(Typeface.Builder(source).build())
        } catch (_: Exception) {
            throw BackupFailure(BackupProblem.FORMAT)
        }
        val label = asset.label?.take(180)?.ifBlank { "사용자 글꼴" } ?: "사용자 글꼴"
        return mapIo {
            ensureReserve(asset.byteSize)
            check(fontsRoot.mkdirs() || fontsRoot.isDirectory)
            val destination = File(fontsRoot, asset.sha256)
            if (destination.exists()) {
                val destFont = File(destination, "font")
                if (destFont.isFile && destFont.length() == asset.byteSize) {
                    return@mapIo FontInstallOutcome.ALREADY_PRESENT
                }
                throw BackupFailure(BackupProblem.INTEGRITY)
            }
            fontsRoot.listFiles().orEmpty().filter { it.name.startsWith(".import-") }.forEach { it.deleteRecursively() }
            val tmpDir = File(fontsRoot, ".import-${UUID.randomUUID()}")
            check(tmpDir.mkdir())
            try {
                val destFont = File(tmpDir, "font")
                FileInputStream(source).use { input ->
                    FileOutputStream(destFont).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            ensureReserve(count.toLong())
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                FileOutputStream(File(tmpDir, "label")).use { out ->
                    out.write(label.toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                syncDirectory(tmpDir)
                Files.move(tmpDir.toPath(), destination.toPath())
                syncDirectory(fontsRoot)
                FontInstallOutcome.INSTALLED
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }

    private suspend fun fingerprint(file: File, maxBytes: Long): FileFingerprint? = mapIo {
        if (!file.isFile) return@mapIo null
        FileInputStream(file).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) throw BackupFailure(BackupProblem.LIMIT)
                digest.update(buffer, 0, count)
            }
            FileFingerprint(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }, total)
        }
    }

    private fun stagingDirectory(sessionId: String): File {
        require(uuidPattern.matches(sessionId))
        val dir = File(stagingRoot, sessionId)
        require(dir.canonicalPath.startsWith(stagingRoot.canonicalPath + File.separator))
        return dir
    }

    private fun stagedAssetFile(sessionId: String, stagingName: String): File {
        require(stagingFilePattern.matches(stagingName))
        val dir = stagingDirectory(sessionId)
        val file = File(dir, stagingName)
        require(file.canonicalPath.startsWith(dir.canonicalPath + File.separator))
        return file
    }

    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private suspend fun <T> mapIo(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: BackupFailure) {
        throw failure
    } catch (failure: ErrnoException) {
        currentCoroutineContext().ensureActive()
        throw BackupFailure(if (failure.errno == OsConstants.ENOSPC) BackupProblem.DISK_FULL else BackupProblem.DOCUMENT_IO)
    } catch (failure: IOException) {
        currentCoroutineContext().ensureActive()
        val cause = failure.cause as? ErrnoException
        val message = failure.message.orEmpty().lowercase()
        val full = cause?.errno == OsConstants.ENOSPC || "no space left" in message || "enospc" in message
        throw BackupFailure(if (full) BackupProblem.DISK_FULL else BackupProblem.DOCUMENT_IO)
    }

    private fun <T> mapIoSync(block: () -> T): T = try {
        block()
    } catch (failure: BackupFailure) {
        throw failure
    } catch (failure: ErrnoException) {
        throw BackupFailure(if (failure.errno == OsConstants.ENOSPC) BackupProblem.DISK_FULL else BackupProblem.DOCUMENT_IO)
    } catch (failure: IOException) {
        val cause = failure.cause as? ErrnoException
        val message = failure.message.orEmpty().lowercase()
        val full = cause?.errno == OsConstants.ENOSPC || "no space left" in message || "enospc" in message
        throw BackupFailure(if (full) BackupProblem.DISK_FULL else BackupProblem.DOCUMENT_IO)
    }

    private companion object {
        const val RESERVE_BYTES = 1024L * 1024
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val stagingFilePattern = Regex("asset-[0-9]{4}\\.bin")
        val managedBookPathPattern = Regex("books/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/original")
    }
}
