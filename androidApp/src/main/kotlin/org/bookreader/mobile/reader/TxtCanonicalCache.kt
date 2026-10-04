package org.bookreader.mobile.reader

import android.annotation.SuppressLint
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.DigestInputStream
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Locale
import java.util.UUID
import org.bookreader.mobile.encoding.TxtDecoder
import org.bookreader.mobile.encoding.TxtDecodeFailure
import org.bookreader.mobile.encoding.TxtDecodingException
import org.bookreader.mobile.encoding.TxtEncoding
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability

class TxtSourceException(val availability: BookAvailability) : java.io.IOException("Managed TXT unavailable")

data class TxtFragment(val start: Long, val length: Int)
data class TxtCacheSnapshot(
    val fragments: List<TxtFragment> = emptyList(),
    val committedLength: Long = 0,
    val complete: Boolean = false,
)

/** Derived cache only. The private original and database are never modified here. */
class TxtCanonicalCache(private val book: Book, private val directory: File) {
    private val revision = requireNotNull(book.currentRevision)
    private val key = MessageDigest.getInstance("SHA-256").digest(revision.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val canonical = File(directory, "$key-v$VERSION.utf16")
    private val marker = File(directory, "$key-v$VERSION.index")
    private var readable: File = canonical
    private var current = TxtCacheSnapshot()
    private val textLru = object : LinkedHashMap<Int, String>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean = size > 8
    }

    @Synchronized fun snapshot(): TxtCacheSnapshot = current

    @Synchronized fun readFragment(index: Int): String {
        textLru[index]?.let { return it }
        val fragment = current.fragments[index]
        val bytes = ByteArray(fragment.length * 2)
        RandomAccessFile(readable, "r").use { file ->
            file.seek(fragment.start * 2)
            file.readFully(bytes)
        }
        val chars = CharArray(fragment.length) { position ->
            ((bytes[position * 2].toInt() and 255) or
                ((bytes[position * 2 + 1].toInt() and 255) shl 8)).toChar()
        }
        return String(chars).also { textLru[index] = it }
    }

    @Synchronized fun fragmentFor(offset: Long): Int {
        val fragments = current.fragments
        require(fragments.isNotEmpty())
        require(offset in 0..current.committedLength)
        var low = 0
        var high = fragments.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (fragments[middle].start <= offset) low = middle else high = middle - 1
        }
        return low
    }

    fun openOrBuild(original: File, checkCancellation: () -> Unit, publish: (TxtCacheSnapshot) -> Unit) {
        require(book.normalizationVersion == 1) { "Unsupported normalization version" }
        if (!original.isFile) throw TxtSourceException(BookAvailability.MISSING)
        if (original.length() != book.sourceByteSize) throw TxtSourceException(BookAvailability.CORRUPT)
        directory.mkdirs()
        if (loadCompleted()) {
            publish(snapshot()) // Show the bounded prefix while integrity is checked in the background.
            try { checkDigest(original, book.sourceSha256, checkCancellation, book.sourceByteSize) }
            catch (invalid: IllegalArgumentException) { throw TxtSourceException(BookAvailability.CORRUPT) }
            val expectedCanonical = DataInputStream(marker.inputStream().buffered()).use { input ->
                input.readInt(); input.readUTF(); input.readUTF()
            }
            try { checkDigest(canonical, expectedCanonical, checkCancellation) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) {
                marker.delete()
                canonical.delete()
                throw failure
            }
            synchronized(this) { current = current.copy(complete = true) }
            publish(snapshot())
            return
        }
        directory.listFiles()?.filter { it.name.startsWith("$key-") && it.name.endsWith("building") }
            ?.forEach { it.delete() }
        require(currentlyFreeBytes(directory) >= book.sourceByteSize * 2 + 64 * 1024) { "Insufficient canonical cache space" }
        val temporary = File(directory, "$key-${UUID.randomUUID()}.building")
        val indexTemporary = File(directory, "$key-${UUID.randomUUID()}.index-building")
        val fragments = ArrayList<TxtFragment>()
        var offset = 0L
        val pending = StringBuilder(MAX_FRAGMENT * 2)
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT)
        val canonicalDigest = MessageDigest.getInstance("SHA-256")
        try {
            RandomAccessFile(temporary, "rw").use { output ->
                synchronized(this) { readable = temporary; current = TxtCacheSnapshot(); textLru.clear() }
                fun commit(length: Int) {
                    checkCancellation()
                    val bytes = ByteArray(length * 2)
                    for (index in 0 until length) {
                        val code = pending[index].code
                        bytes[index * 2] = code.toByte()
                        bytes[index * 2 + 1] = (code ushr 8).toByte()
                    }
                    output.write(bytes)
                    canonicalDigest.update(bytes)
                    synchronized(fragments) { fragments += TxtFragment(offset, length) }
                    offset += length
                    pending.delete(0, length)
                    val next = TxtCacheSnapshot(fragmentView(fragments), offset)
                    synchronized(this) { current = next }
                    publish(next)
                }
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    DigestInputStream(original.inputStream().buffered(64 * 1024), digest).use { input ->
                        TxtDecoder.decode(input, requireNotNull(TxtEncoding.fromId(requireNotNull(book.encodingId))),
                            cancellationCheck = checkCancellation) { chunk ->
                            pending.append(chunk)
                            while (pending.length > MAX_FRAGMENT) {
                                boundaries.setText(pending.toString())
                                val split = boundaries.preceding(MAX_FRAGMENT + 1)
                                require(split > 0) { "Text cluster exceeds fragment limit" }
                                commit(split)
                            }
                        }
                    }
                } catch (failure: TxtDecodingException) {
                    if (failure.code in setOf(TxtDecodeFailure.MALFORMED_INPUT,
                            TxtDecodeFailure.BOM_MISMATCH, TxtDecodeFailure.BINARY_CONTENT)) {
                        // Strict decoding can fail before EOF. Verify the original before calling
                        // it corrupt; a decoder/platform regression must not change availability.
                        try { checkDigest(original, book.sourceSha256, checkCancellation, book.sourceByteSize) }
                        catch (invalid: IllegalArgumentException) { throw TxtSourceException(BookAvailability.CORRUPT) }
                    }
                    throw failure
                }
                if (digest.digest().joinToString("") { "%02x".format(it) } != book.sourceSha256)
                    throw TxtSourceException(BookAvailability.CORRUPT)
                if (pending.isNotEmpty()) commit(pending.length)
                require(fragments.isNotEmpty()) { "EMPTY_TXT" }
                output.fd.sync()
            }
            checkCancellation()
            DataOutputStream(indexTemporary.outputStream().buffered()).use { index ->
                index.writeInt(VERSION)
                index.writeUTF(revision)
                index.writeUTF(canonicalDigest.digest().joinToString("") { "%02x".format(it) })
                index.writeLong(offset)
                index.writeInt(fragments.size)
                fragments.forEach { index.writeLong(it.start); index.writeInt(it.length) }
            }
            synchronized(this) {
                // The completion index is published last; partial files never count as READY.
                check(temporary.renameTo(canonical)) { "Cannot publish canonical cache" }
                check(indexTemporary.renameTo(marker)) { "Cannot publish canonical index" }
                readable = canonical
                current = TxtCacheSnapshot(fragmentView(fragments), offset, true)
            }
            publish(snapshot())
        } finally {
            temporary.delete()
            indexTemporary.delete()
        }
    }

    private fun loadCompleted(): Boolean = synchronized(this) {
        if (!canonical.isFile || !marker.isFile) return false
        try {
            DataInputStream(marker.inputStream().buffered()).use { input ->
                require(input.readInt() == VERSION && input.readUTF() == revision)
                require(input.readUTF().matches(Regex("[a-f0-9]{64}")))
                val total = input.readLong()
                val count = input.readInt()
                require(total > 0 && total <= book.sourceByteSize * 2 && count in 1..100_000)
                require(canonical.length() == total * 2)
                var expected = 0L
                val fragments = List(count) {
                    val start = input.readLong()
                    val length = input.readInt()
                    require(start == expected && length in 1..MAX_FRAGMENT)
                    expected += length
                    TxtFragment(start, length)
                }
                require(expected == total && input.read() == -1)
                readable = canonical
                current = TxtCacheSnapshot(fragments, total, false)
                true
            }
        } catch (_: Exception) { false }
    }

    private fun checkDigest(file: File, expected: String, checkCancellation: () -> Unit,
        maxBytes: Long = file.length()) {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(64 * 1024)
        var total = 0L
        file.inputStream().buffered().use { input ->
            while (true) {
                checkCancellation()
                val count = input.read(bytes)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "Managed file size changed" }
                digest.update(bytes, 0, count)
            }
        }
        require(total == maxBytes && digest.digest().joinToString("") { "%02x".format(it) } == expected) {
            "Managed copy or canonical cache checksum changed"
        }
    }

    // Deliberately count current free space only, without allocation or cache reclamation.
    // StorageManager's allocatable capacity includes reclaimable caches; this preflight does
    // not reserve disk or guarantee against later OS eviction. Actual write failures still
    // close handles and remove only this build's temporary files in openOrBuild's finally.
    @SuppressLint("UsableSpace")
    private fun currentlyFreeBytes(directory: File): Long = directory.usableSpace

    /** Fixed-size view over append-only metadata: no per-fragment full-index copies or queued jobs. */
    private fun fragmentView(fragments: ArrayList<TxtFragment>): List<TxtFragment> {
        val count = synchronized(fragments) { fragments.size }
        return object : AbstractList<TxtFragment>() {
            override val size = count
            override fun get(index: Int): TxtFragment {
                require(index in 0 until count)
                return synchronized(fragments) { fragments[index] }
            }
        }
    }

    companion object {
        const val MAX_FRAGMENT = 16 * 1024
        const val VERSION = 2
    }
}
