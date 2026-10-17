package org.bookreader.mobile.comic

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Actual platform BitmapFactory/EXIF, never a JVM stub. Product gestures and physical PSS are separate. */
class ComicImageDecoderTest {
    private fun withArchive(block: suspend (File, File) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "comic-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val file = File(directory, "source.cbz")
            context.assets.open("comic-fixtures/images.cbz").use { source -> file.outputStream().use(source::copyTo) }
            withTimeout(60_000) { block(file, File(directory, "decode")) }
        } finally { directory.deleteRecursively() }
    }
    @Test fun staticImagesExifAndLongPngWebpUseBudgetedFallbackAndPreserveBrokenEntries() = withArchive { file, scratch ->
        ComicImageDecoder(file, scratch, heapBudget = 64L * 1024 * 1024).use { decoder ->
            decoder.decode("small.png", 1080, 1920).use { image ->
                assertEquals(40, image.bitmap.width); assertEquals(20, image.bitmap.height)
                assertFalse(image.limitedResolution)
            }
            decoder.decode("rotated.jpg", 1080, 1920).use { image ->
                assertEquals(20, image.bitmap.width); assertEquals(40, image.bitmap.height)
            }
            for (key in listOf("long.png", "long.webp")) decoder.decode(key, 1080, 1920).use { image ->
                assertTrue(image.limitedResolution)
                assertEquals(1080, image.sourceWidth)
                assertTrue(image.sourceHeight >= 16000)
                assertTrue(image.bitmap.height <= 4096)
                assertTrue(decoder.retainedBitmapBytes <= decoder.bitmapBudget)
            }
            for (key in listOf("broken.png", "animated.png", "animated.webp")) {
                var rejected = false
                try { decoder.decode(key, 1080, 1920).close() } catch (_: Exception) { rejected = true }
                assertTrue("Damaged/animated image must be recoverable", rejected)
                assertEquals(0L, decoder.retainedBitmapBytes)
                assertEquals(0, scratch.listFiles()!!.size)
            }
            decoder.decode("pages/001.png", 1080, 1920).use { assertEquals("pages/001.png", it.entryKey) }
            assertEquals(0L, decoder.retainedBitmapBytes)
        }
    }
    @Test fun hundredPageMovesReleaseBitmapsAndCancellationLeavesNoTemporaryImages() = withArchive { file, scratch ->
        ComicImageDecoder(file, scratch, heapBudget = 64L * 1024 * 1024).use { decoder ->
            var maximumBytes = 0L
            val initialPss = Debug.getPss(); var maximumPss = initialPss
            repeat(100) { index ->
                decoder.decode("pages/${index.toString().padStart(3, '0')}.png", 1080, 1920).use {
                    maximumBytes = maxOf(maximumBytes, decoder.retainedBitmapBytes)
                    assertTrue(decoder.retainedBitmapBytes <= decoder.bitmapBudget)
                }
                assertEquals(0L, decoder.retainedBitmapBytes)
                if (index % 10 == 0) maximumPss = maxOf(maximumPss, Debug.getPss())
            }
            coroutineScope {
                repeat(20) {
                    val job = launch(Dispatchers.Default) { decoder.decode("long.png", 1080, 1920).use { yield() } }
                    delay(2)
                    job.cancelAndJoin()
                }
            }
            assertEquals(0L, decoder.retainedBitmapBytes)
            assertEquals(0, scratch.listFiles()!!.size)
            println("COMIC_MEMORY {\"moves\":100,\"maximum_bitmap_bytes\":$maximumBytes,\"budget_bytes\":${decoder.bitmapBudget},\"initial_pss_kib\":$initialPss,\"maximum_pss_kib\":$maximumPss,\"physical_performance\":\"NOT_RUN\"}")
            decoder.close()
            var closed = false
            try { decoder.decode("small.png", 1080, 1920).close() } catch (_: IllegalStateException) { closed = true }
            assertTrue(closed)
            assertEquals(0, scratch.listFiles()!!.size)
        }
    }
}
