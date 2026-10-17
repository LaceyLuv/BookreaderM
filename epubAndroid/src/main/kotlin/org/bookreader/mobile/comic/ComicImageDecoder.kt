package org.bookreader.mobile.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bookreader.mobile.epub.BoundedZip

/** A page owns its bitmap. The host must remove it from rendering before closing the lease. */
class ComicImage internal constructor(val entryKey: String, val bitmap: Bitmap,
    val sourceWidth: Int, val sourceHeight: Int, val limitedResolution: Boolean,
    private val released: (Long) -> Unit) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    val allocationBytes = bitmap.allocationByteCount.toLong()
    override fun close() { if (closed.compareAndSet(false, true)) { bitmap.recycle(); released(allocationBytes) } }
}

/** One decode at a time; compressed input is spooled under an opaque temporary name, never ZIP names. */
class ComicImageDecoder(private val file: File, private val scratch: File,
    heapBudget: Long = Runtime.getRuntime().maxMemory()) : AutoCloseable {
    val bitmapBudget = minOf(64L * 1024 * 1024, heapBudget / 5)
    private val closed = AtomicBoolean(false)
    private val decodedBytes = AtomicLong(0)
    private val mutex = Mutex()
    val retainedBitmapBytes: Long get() = decodedBytes.get()

    suspend fun decode(entryKey: String, viewportWidth: Int, viewportHeight: Int): ComicImage {
        var produced: ComicImage? = null
        try { return withContext(Dispatchers.IO) {
        require(viewportWidth in 1..16_384 && viewportHeight in 1..16_384)
        mutex.withLock {
            val context = currentCoroutineContext()
            val deadline = System.nanoTime() + 30_000_000_000L
            fun checkpoint() {
                context.ensureActive(); check(!closed.get()) { "Comic decoder closed" }
                check(System.nanoTime() < deadline) { "Image decode time budget exceeded" }
            }
            checkpoint()
            require(bitmapBudget >= 64 * 1024) { "Insufficient image budget" }
            check(scratch.mkdirs() || scratch.isDirectory)
            val temporary = File.createTempFile("comic-", ".image", scratch)
            var owned: Bitmap? = null
            try {
                val archive = BoundedZip(file, 2L * 1024 * 1024 * 1024, ::checkpoint)
                temporary.outputStream().use { archive.copyTo(entryKey, it, 64 * 1024 * 1024, ::checkpoint) }
                val jpeg = validateStaticImage(temporary, ::checkpoint)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.path, bounds)
                require(bounds.outWidth in 1..1_000_000 && bounds.outHeight in 1..1_000_000) { "Invalid image dimensions" }
                val available = (bitmapBudget - decodedBytes.get()) / 2 // rotation may briefly own both bitmaps
                require(available >= 16 * 1024) { "Release an older page before decoding" }
                var sample = 1
                fun width() = (bounds.outWidth.toLong() + sample - 1) / sample
                fun height() = (bounds.outHeight.toLong() + sample - 1) / sample
                while (width() * height() * 4 > available || width() > 4096 || height() > 4096 ||
                    width() > viewportWidth * 2L && height() > viewportHeight * 2L) sample *= 2
                checkpoint()
                val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
                val bitmap = requireNotNull(BitmapFactory.decodeFile(temporary.path, options)) { "Damaged image" }
                owned = bitmap
                require(bitmap.allocationByteCount.toLong() <= available) { "Decoder exceeded reserved memory" }
                checkpoint()
                val orientation = if (jpeg) ExifInterface(temporary.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) else 1
                require(orientation in 1..8)
                val matrix = orientationMatrix(orientation)
                if (!matrix.isIdentity) {
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (rotated !== bitmap) bitmap.recycle()
                    owned = rotated
                }
                checkpoint()
                val result = requireNotNull(owned)
                require(result.allocationByteCount.toLong() <= available)
                val lease = ComicImage(entryKey, result, bounds.outWidth, bounds.outHeight, sample > 1) { decodedBytes.addAndGet(-it) }
                decodedBytes.addAndGet(lease.allocationBytes)
                owned = null
                produced = lease
                lease
            } catch (_: OutOfMemoryError) {
                throw IllegalStateException("Image exceeds available platform memory")
            } finally { owned?.recycle(); temporary.delete() }
        }
        } } catch (failure: Throwable) {
            // withContext has prompt cancellation on return to the caller's dispatcher. A page
            // produced just before that boundary must not leak if the caller never receives it.
            produced?.close()
            throw failure
        }
    }
    override fun close() { closed.set(true) }

    private fun orientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }
            8 -> setRotate(-90f)
        }
    }

    /** Reject animation before platform decode. Compressed image input has already passed ZIP CRC. */
    private fun validateStaticImage(file: File, checkpoint: () -> Unit): Boolean = RandomAccessFile(file, "r").use { input ->
        require(input.length() >= 12)
        val signature = ByteArray(12); input.readFully(signature)
        if (signature[0] == 0xff.toByte() && signature[1] == 0xd8.toByte()) return@use true
        if (signature.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) {
            input.seek(8)
            while (input.filePointer + 12 <= input.length()) {
                checkpoint()
                val size = input.readInt().toLong() and 0xffffffffL
                val type = ByteArray(4); input.readFully(type)
                val name = type.toString(Charsets.US_ASCII)
                require(name != "acTL" && name != "fcTL" && name != "fdAT") { "Animated PNG unsupported" }
                require(input.filePointer + size + 4 <= input.length())
                input.seek(input.filePointer + size + 4)
                if (name == "IEND") return@use false
            }
            error("Incomplete PNG")
        }
        require(signature.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            signature.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP") { "Unsupported image" }
        while (input.filePointer + 8 <= input.length()) {
            checkpoint()
            val type = ByteArray(4); input.readFully(type)
            val name = type.toString(Charsets.US_ASCII)
            val size = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            require(name != "ANIM" && name != "ANMF") { "Animated WebP unsupported" }
            val end = input.filePointer + size + (size and 1)
            require(end <= input.length())
            if (name == "VP8X") { require(size >= 10); require(input.readUnsignedByte() and 2 == 0) { "Animated WebP unsupported" } }
            input.seek(end)
        }
        false
    }
}
