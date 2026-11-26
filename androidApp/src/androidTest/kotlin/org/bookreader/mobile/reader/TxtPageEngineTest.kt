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
            for (region in 1..20) {
                var start = body.length * region / 22
                while (body[start].isLowSurrogate()) start++
                val first = engine.next(start.toLong())
                val second = engine.next(first.range.end)
                repeat(5) {
                    assertEquals(first, engine.previous(second.range.start))
                    assertEquals(second, engine.next(first.range.end))
                }
            }
            assertEquals(body.length.toLong(), engine.previous(body.length.toLong()).range.end)
        }
    }

    @Test fun pageIndexWithBoundedWindowMatchesEngineBoundariesExactly() = runBlocking {
        lateinit var measurer: TextMeasurer
        compose.setContent { MaterialTheme { measurer = rememberTextMeasurer(cacheSize = 0) } }
        compose.waitForIdle()
        val style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 29.sp)
        val koreanBook = buildString {
            for (line in 0 until 1_500) {
                if (line != 0) append('\n')
                append("reader-pages 본문 ").append(line.toString().padStart(5, '0'))
                    .append(" · 읽기 위치와 줄바꿈을 검증하는 자체 생성 문장입니다.")
            }
        }
        val cases = listOf(
            "가".repeat(131_072),
            ("한글 😀 e\u0301 👨‍👩‍👧‍👦\n\n  공백과 끝\n").repeat(1_000),
            koreanBook,
        )
        for (body in cases) {
            val expectedStarts = mutableListOf<Long>()
            val reference = TxtPageEngine(body.length.toLong(), 800, { start, count ->
                val raw = body.substring(start.toInt(), minOf(body.length, start.toInt() + count))
                if (raw.lastOrNull()?.isHighSurrogate() == true) raw.dropLast(1) else raw
            }, adaptiveBudget = false) { text ->
                measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = 700))
            }
            var offset = 0L
            while (offset < body.length) {
                expectedStarts += offset
                offset = reference.next(offset).range.end
            }

            var sourceReads = 0
            var largestRead = 0
            val windowReader = BoundedTxtWindowReader(body.length.toLong(), { start, count ->
                sourceReads++
                largestRead = maxOf(largestRead, count)
                val raw = body.substring(start.toInt(), minOf(body.length, start.toInt() + count))
                if (raw.lastOrNull()?.isHighSurrogate() == true) raw.dropLast(1) else raw
            })
            val indexedEngine = TxtPageEngine(body.length.toLong(), 800, windowReader::read) { text ->
                measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = 700))
            }
            val index = TxtPageIndex.build(body.length.toLong()) { indexedEngine.nextRange(it).end }

            assertEquals(expectedStarts.size, index.total)
            assertArrayEquals(expectedStarts.toLongArray(), index.pageStarts())
            assertTrue(largestRead <= BoundedTxtWindowReader.MAX_WINDOW_UNITS)
            val maxExpectedReads = (body.length / BoundedTxtWindowReader.MAX_WINDOW_UNITS) + 3
            assertTrue("Expected <= $maxExpectedReads bounded reads, got $sourceReads", sourceReads <= maxExpectedReads)
        }
    }
}
