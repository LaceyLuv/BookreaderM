package org.bookreader.mobile.reader

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.CancellationSignal
import android.os.StatFs
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UserFont(val id: String, val label: String, val file: File)

/** Private, bounded, content-addressed copies. No persisted URI grant or source mutation. */
class UserFontStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "reader-fonts")
    fun find(id: String?): UserFont? {
        if (id == null || !id.matches(ID)) return null
        val directory = File(root, id)
        val font = File(directory, "font")
        val label = File(directory, "label")
        if (!font.isFile || font.length() !in 12..FontFilePolicy.MAX_BYTES || !label.isFile || label.length() > 1024) return null
        return runCatching { UserFont(id, label.readText().take(180), font) }.getOrNull()
    }
    fun list(): List<UserFont> = root.listFiles().orEmpty().asSequence()
        .filter { it.name.matches(ID) }.take(MAX_FONTS).mapNotNull { find(it.name) }.sortedBy { it.label }.toList()

    suspend fun import(uri: Uri): UserFont = withContext(Dispatchers.IO) {
        operations.withLock {
            withTimeout(60_000) {
                require(uri.scheme == "content" && uri.userInfo == null) { "파일 선택기로 글꼴을 선택하세요." }
                check(root.mkdirs() || root.isDirectory)
                // Only abandoned font staging directories; never touch books or registered fonts.
                root.listFiles().orEmpty().filter { it.name.startsWith(".import-") }.forEach { it.deleteRecursively() }
                val staging = File(root, ".import-${UUID.randomUUID()}")
                check(staging.mkdir())
                try {
                    val signal = CancellationSignal()
                    coroutineScope {
                        val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { signal.cancel() }
                        }
                        try {
                            var label = "사용자 글꼴"
                            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null, signal)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex >= 0) label = cursor.getString(nameIndex).orEmpty().substringAfterLast('/').substringAfterLast('\\')
                                        .filter { !it.isISOControl() }.take(180).ifBlank { "사용자 글꼴" }
                                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex))
                                        require(cursor.getLong(sizeIndex) <= FontFilePolicy.MAX_BYTES) { "글꼴 파일은 20MB 이하여야 합니다." }
                                }
                            }
                            val file = File(staging, "font")
                            val digest = MessageDigest.getInstance("SHA-256")
                            val asset = requireNotNull(app.contentResolver.openAssetFileDescriptor(uri, "r", signal)) { "글꼴 파일을 열 수 없습니다." }
                            asset.use { descriptor ->
                                val input = descriptor.createInputStream()
                                val closer = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                                    try { awaitCancellation() } finally { runCatching { input.close() }; runCatching { descriptor.close() } }
                                }
                                try { input.use {
                                    FileOutputStream(file).use { output ->
                                        val buffer = ByteArray(64 * 1024)
                                        var copied = 0L
                                        while (true) {
                                            ensureActive()
                                            val poll = StructPollfd().apply { fd = descriptor.parcelFileDescriptor.fileDescriptor; events = OsConstants.POLLIN.toShort() }
                                            if (Os.poll(arrayOf(poll), 200) == 0) continue
                                            ensureActive()
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            copied += count
                                            require(copied <= FontFilePolicy.MAX_BYTES) { "글꼴 파일은 20MB 이하여야 합니다." }
                                            check(StatFs(root.path).availableBytes > count + 1024L * 1024) { "저장 공간이 부족합니다." }
                                            output.write(buffer, 0, count); digest.update(buffer, 0, count)
                                        }
                                        output.fd.sync()
                                    }
                                } } finally { closer.cancel() }
                            }
                            FontFilePolicy.validate(file)
                            requireNotNull(Typeface.Builder(file).build()) { "이 기기에서 사용할 수 없는 글꼴입니다." }
                            val id = digest.digest().joinToString("") { "%02x".format(it) }
                            find(id)?.let { return@coroutineScope it }
                            val existing = list()
                            require(existing.size < MAX_FONTS && existing.sumOf { it.file.length() } + file.length() <= 100L * 1024 * 1024) {
                                "글꼴은 최대 12개·합계 100MB까지 등록할 수 있습니다. 사용하지 않는 글꼴을 삭제하세요."
                            }
                            FileOutputStream(File(staging, "label")).use { it.write(label.toByteArray()); it.fd.sync() }
                            sync(staging)
                            ensureActive()
                            Files.move(staging.toPath(), File(root, id).toPath())
                            sync(root)
                            requireNotNull(find(id))
                        } finally { watcher.cancel() }
                    }
                } finally { staging.deleteRecursively() }
            }
        }
    }

    /** UI excludes the applied font; active readers retain their selected file. */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        operations.withLock {
            require(id.matches(ID))
            check(File(root, id).deleteRecursively()) { "글꼴을 삭제하지 못했습니다." }
            if (root.exists()) sync(root)
        }
    }
    private fun sync(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
    companion object {
        private val operations = Mutex()
        private val ID = Regex("[0-9a-f]{64}")
        private const val MAX_FONTS = 12
    }
}
