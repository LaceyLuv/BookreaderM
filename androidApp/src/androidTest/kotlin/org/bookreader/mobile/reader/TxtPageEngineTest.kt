package org.bookreader.mobile.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TxtPageEngineTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualMeasurementCoversLongParagraphsBlankLinesAndEmojiWithoutDrift() = runBlocking {
        lateinit var measurer: TextMeasurer
        compose.setContent { MaterialTheme { measurer = rememberTextMeasurer(cacheSize = 4) } }
        compose.waitForIdle()
        val style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 29.sp)
        val cases = listOf("가".repeat(131_072), "x".repeat(1_048_576),
            ("한글 😀 e\u0301 👨‍👩‍👧‍👦\n\n  공백과 끝\n").repeat(1_000))
        for (body in cases) {
            var largestRead = 0
            val engine = TxtPageEngine(body.length.toLong(), 800, { start, count ->
                largestRead = maxOf(largestRead, count)
                body.substring(start.toInt(), minOf(body.length, start.toInt() + count))
            }, { text -> measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = 700)) })
            var offset = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            var pages = 0
            while (offset < body.length) {
                val page = engine.next(offset)
                assertEquals(offset, page.range.start)
                assertEquals(body.substring(offset.toInt(), page.range.end.toInt()), page.text)
                assertFalse(page.text.first().isLowSurrogate())
                assertFalse(page.text.last().isHighSurrogate())
                digest.update(page.text.toByteArray(Charsets.UTF_16LE))
                offset = page.range.end
                pages++
            }
            assertTrue(pages > 1)
            assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_16LE)), digest.digest())
            assertTrue(largestRead <= 65_536)
            // A random regional start and 100 forward/back pairs must reuse identical boundaries.
            var start = body.length / 3
            while (body[start].isLowSurrogate()) start++
            val first = engine.next(start.toLong())
            val second = engine.next(first.range.end)
            repeat(100) {
                assertEquals(first, engine.previous(second.range.start))
                assertEquals(second, engine.next(first.range.end))
            }
            assertEquals(body.length.toLong(), engine.previous(body.length.toLong()).range.end)
        }
    }
}
