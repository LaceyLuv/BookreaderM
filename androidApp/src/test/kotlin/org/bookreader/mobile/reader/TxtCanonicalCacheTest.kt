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

    @Test fun percentageSeekSnapsSurrogatesAndCombiningSequencesWithoutChangingEof() {
        val original = temporary.newFile("seek.txt")
        original.writeText("a😀e\u0301끝")
        val cache = TxtCanonicalCache(book(original), temporary.newFolder("seek-cache"))
        cache.openOrBuild(original, {}) {}
        assertEquals(1L, cache.anchorAtOrBefore(2))
        assertEquals(3L, cache.anchorAtOrBefore(4))
        assertEquals(6L, cache.anchorAtOrBefore(6))
        assertThrows(IllegalArgumentException::class.java) { cache.anchorAtOrBefore(7) }
    }

    @Test fun fiftyMiBTxtBuildSearchAndArbitraryAnchorsStayBounded() = kotlinx.coroutines.runBlocking {
        val original = temporary.newFile("large-generated.txt")
        val size = 50L * 1024 * 1024
        val needle = "END-OF-GENERATED-TXT"
        original.outputStream().buffered().use { output ->
            val block = ByteArray(16 * 1024) { 'x'.code.toByte() }
            var remaining = size - needle.length
            while (remaining > 0) {
                val count = minOf(remaining, block.size.toLong()).toInt()
                output.write(block, 0, count); remaining -= count
            }
            output.write(needle.toByteArray())
        }
        val metadata = book(original)
        val cache = TxtCanonicalCache(metadata, temporary.newFolder("large-generated-cache"))
        var prefixSeen = false
        val buildStart = System.nanoTime()
        cache.openOrBuild(original, {}) { snapshot ->
            if (!snapshot.complete && snapshot.committedLength > 0) prefixSeen = true
        }
        val buildMs = (System.nanoTime() - buildStart) / 1_000_000
        assertTrue(prefixSeen)
        assertEquals(size, cache.snapshot().committedLength)
        assertTrue(cache.snapshot().fragments.all { it.length <= TxtCanonicalCache.MAX_FRAGMENT })
        var maxRead = 0
        var reads = 0
        val searchStart = System.nanoTime()
        val result = org.bookreader.mobile.search.TxtLiteralSearch.find(needle, false, size) { offset, count ->
            maxRead = maxOf(maxRead, count); reads++
            cache.readRange(offset, count)
        }
        val searchMs = (System.nanoTime() - searchStart) / 1_000_000
        assertEquals(listOf(size - needle.length), result.offsets)
        assertEquals(size, result.scannedUntil)
        assertNull(result.nextOffset)
        assertTrue(maxRead <= org.bookreader.mobile.search.TxtLiteralSearch.WINDOW)
        for (offset in listOf(0L, size / 2, size - needle.length, size))
            assertEquals(offset, cache.anchorAtOrBefore(offset))
        assertEquals(metadata.sourceSha256, digest(original))
        println("LARGE_TXT {\"source_bytes\":$size,\"canonical_utf16\":$size,\"build_ms\":$buildMs,\"search_ms\":$searchMs,\"read_windows\":$reads,\"max_window_utf16\":$maxRead,\"status\":\"PASS\",\"physical_performance\":\"NOT_RUN\"}")
    }

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
