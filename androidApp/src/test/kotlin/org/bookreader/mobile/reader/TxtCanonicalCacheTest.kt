package org.bookreader.mobile.reader

import java.io.File
import java.security.MessageDigest
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TxtCanonicalCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun canonicalFragmentsCoverCrLfAndSurrogatesWithoutGaps() {
        val original = temporary.newFile("canonical.txt")
        val source = "\uFEFF" + "x".repeat(16_383) + "\r\n😀\r끝\uFEFF  \n\n"
        original.writeBytes(source.toByteArray())
        val expected = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val cache = TxtCanonicalCache(book(original), temporary.newFolder("cache"))
        val observed = mutableListOf<TxtCacheSnapshot>()
        cache.openOrBuild(original, {}, observed::add)
        assertTrue(observed.any { !it.complete && it.committedLength > 0 })
        assertTrue(cache.snapshot().complete)
        var offset = 0L
        val rebuilt = StringBuilder()
        cache.snapshot().fragments.forEachIndexed { index, fragment ->
            assertEquals(offset, fragment.start)
            assertTrue(fragment.length <= TxtCanonicalCache.MAX_FRAGMENT)
            val text = cache.readFragment(index)
            assertFalse(text.first().isLowSurrogate())
            assertFalse(text.last().isHighSurrogate())
            rebuilt.append(text)
            offset += fragment.length
        }
        assertEquals(expected, rebuilt.toString())
        assertEquals(expected.length.toLong(), offset)
    }

    @Test fun fiveMiBParagraphHasBoundedFragmentsAndCacheLossRebuildsOriginal() {
        val original = temporary.newFile("long.txt")
        val size = 5 * 1024 * 1024
        original.outputStream().use { output ->
            val block = ByteArray(16 * 1024) { 'x'.code.toByte() }
            repeat(size / block.size) { output.write(block) }
        }
        val metadata = book(original)
        val folder = temporary.newFolder("long-cache")
        val cache = TxtCanonicalCache(metadata, folder)
        cache.openOrBuild(original, {}) {}
        assertEquals(size.toLong(), cache.snapshot().committedLength)
        assertTrue(cache.snapshot().fragments.size > 1)
        cache.snapshot().fragments.forEachIndexed { index, fragment ->
            assertTrue(fragment.length in 1..TxtCanonicalCache.MAX_FRAGMENT)
            assertTrue(cache.readFragment(index).all { it == 'x' })
        }
        folder.listFiles()!!.forEach { assertTrue(it.delete()) }
        val rebuilt = TxtCanonicalCache(metadata, folder)
        rebuilt.openOrBuild(original, {}) {}
        assertEquals(size.toLong(), rebuilt.snapshot().committedLength)
        assertEquals(size.toLong(), original.length())
        assertEquals(metadata.sourceSha256, digest(original))
    }

    @Test fun cancellationLeavesOriginalAndNoCompletionMarker() {
        val original = temporary.newFile("cancel.txt")
        original.writeBytes(ByteArray(128 * 1024) { 'x'.code.toByte() })
        val metadata = book(original)
        val folder = temporary.newFolder("cancel-cache")
        val cache = TxtCanonicalCache(metadata, folder)
        var checks = 0
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            cache.openOrBuild(original, {
                if (++checks > 4) throw kotlinx.coroutines.CancellationException()
            }) {}
        }
        assertTrue(folder.listFiles()!!.none { it.extension == "index" || it.extension == "building" })
        assertEquals(metadata.sourceSha256, digest(original))
        val rebuilt = TxtCanonicalCache(metadata, folder)
        rebuilt.openOrBuild(original, {}) {}
        assertTrue(rebuilt.snapshot().complete)
    }

    @Test fun sameLengthSourceMutationAndCacheMutationFailClosed() {
        val original = temporary.newFile("tamper.txt")
        original.writeBytes("original original original".toByteArray())
        val metadata = book(original)
        val folder = temporary.newFolder("tamper-cache")
        TxtCanonicalCache(metadata, folder).openOrBuild(original, {}) {}
        val canonical = folder.listFiles()!!.single { it.extension == "utf16" }
        java.io.RandomAccessFile(canonical, "rw").use { it.writeByte('z'.code) }
        assertThrows(IllegalArgumentException::class.java) {
            TxtCanonicalCache(metadata, folder).openOrBuild(original, {}) {}
        }
        assertEquals(metadata.sourceSha256, digest(original))
        TxtCanonicalCache(metadata, folder).openOrBuild(original, {}) {}
        original.writeBytes("mutated! original original".toByteArray())
        assertEquals(metadata.sourceByteSize, original.length())
        assertThrows(TxtSourceException::class.java) {
            TxtCanonicalCache(metadata, folder).openOrBuild(original, {}) {}
        }
    }

    private fun book(file: File) = Book(
        "cache-fixture", BookFormat.TXT, "Fixture", "fixture.txt", "books/fixture/original",
        digest(file), file.length(), 1, 1, 1, currentRevision = "fixture-${digest(file)}",
        encodingId = "utf-8", normalizationVersion = 1,
    )

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
