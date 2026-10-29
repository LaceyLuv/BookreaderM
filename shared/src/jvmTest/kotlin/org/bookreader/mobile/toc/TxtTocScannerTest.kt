package org.bookreader.mobile.toc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.*

class TxtTocScannerTest {
    private suspend fun scan(body: String): TxtTocScan = TxtTocScanner.scan(body.length.toLong()) { start, count ->
        val end = minOf(start.toInt() + count, body.length)
        body.substring(start.toInt(), if (body[end - 1].isHighSurrogate()) end - 1 else end)
    }

    @Test fun recognizesStandaloneKoreanEnglishAndBookendsAtExactUtf16Offsets() = runBlocking {
        val titles = listOf("프롤로그", "제1화 처음", "2화: 만남", "제 3 장 시작", "Chapter 4 — Return", "[5회] 마지막", "에필로그")
        val body = "😀 시작하는 본문\n" + titles.joinToString("\n본문 문장이다.\n\n") { "  $it" }
        val result = scan(body)
        assertEquals(titles, result.headings.map { it.title })
        assertEquals(titles.map { body.indexOf(it).toLong() }, result.headings.map { it.offset })
        assertEquals(listOf(TxtHeadingKind.PROLOGUE, TxtHeadingKind.EPISODE, TxtHeadingKind.EPISODE,
            TxtHeadingKind.CHAPTER, TxtHeadingKind.CHAPTER, TxtHeadingKind.EPISODE, TxtHeadingKind.EPILOGUE), result.headings.map { it.kind })
        assertFalse(result.truncated); assertEquals(body.length.toLong(), result.scannedUnits)
        assertEquals(listOf(TxtHeading(1, "Chapter 1", TxtHeadingKind.CHAPTER)), scan("\tChapter\t1").headings)
    }

    @Test fun rejectsNumbersEmbeddedMentionsAndSentenceLikeCandidates() = runBlocking {
        val body = listOf("123", "2026년 10월", "그는 제1화를 읽었다", "1화에서는 이야기가 시작된다",
            "12장짜리 문서", "프롤로그를 읽었다", "Chapter 3 is mentioned in this sentence.",
            "제1화 내용을 살펴보자.", "chapter123", "[1화", "제1234567화", "1화 " + "가".repeat(120)).joinToString("\n")
        assertTrue(scan(body).headings.isEmpty())
    }

    @Test fun handlesWindowBoundariesSurrogatesCrLfAndRepeatedTitlesWithoutDeduplicatingOffsets() = runBlocking {
        val body = "x".repeat(TxtTocScanner.WINDOW - 1) + "😀\r\n1화 같은 제목\r\n" +
            "x".repeat(TxtTocScanner.WINDOW - 11) + "\n제2장 경계😀\n1화 같은 제목"
        val result = scan(body)
        assertEquals(listOf(body.indexOf("1화").toLong(), body.indexOf("제2장").toLong(), body.lastIndexOf("1화").toLong()),
            result.headings.map { it.offset })
        assertEquals("제2장 경계😀", result.headings[1].title)
        assertEquals(body.length.toLong(), result.scannedUnits)
    }

    @Test fun longLinesNeverBecomeTruncatedHeadingsAndNextLineStillWorks() = runBlocking {
        val body = "제1화 " + "가".repeat(1_000_000) + "\n제2화 정상\n"
        val result = scan(body)
        assertEquals(listOf(TxtHeading(body.indexOf("제2화").toLong(), "제2화 정상", TxtHeadingKind.EPISODE)), result.headings)
    }

    @Test fun limitsCandidatesAndStopsReadingAfterFirstExcessHeading() = runBlocking {
        val body = (1..TxtTocScanner.MAX_HEADINGS + 10).joinToString("\n") { "${it}화 제목" }
        var calls = 0
        val result = TxtTocScanner.scan(body.length.toLong()) { start, count ->
            assertTrue(count <= TxtTocScanner.WINDOW); calls++
            body.substring(start.toInt(), start.toInt() + count)
        }
        assertEquals(TxtTocScanner.MAX_HEADINGS, result.headings.size)
        assertTrue(result.truncated); assertTrue(result.scannedUnits < body.length)
        assertTrue(calls <= body.length / TxtTocScanner.WINDOW + 1)
        assertFalse(scan("1화\n".repeat(TxtTocScanner.MAX_HEADINGS)).truncated)
    }

    @Test fun cancellationAndInvalidReadersCannotProduceACompletedIndex() = runBlocking {
        val job = Job(); var calls = 0
        assertFailsWith<CancellationException> { withContext(job) {
            TxtTocScanner.scan(1_000_000) { _, count -> calls++; job.cancel(); "x".repeat(count) }
        } }
        assertEquals(1, calls)
        assertFailsWith<IllegalStateException> { TxtTocScanner.scan(10) { _, _ -> "" } }
        assertFailsWith<IllegalStateException> { TxtTocScanner.scan(10) { _, count -> "x".repeat(count + 1) } }
        assertFailsWith<IllegalArgumentException> { TxtTocScanner.scan(-1) { _, _ -> error("No read") } }
        assertEquals(TxtTocScan(emptyList(), 0, false), TxtTocScanner.scan(0) { _, _ -> error("No read") })
    }
}
