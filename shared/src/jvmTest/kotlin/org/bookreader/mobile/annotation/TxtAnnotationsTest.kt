package org.bookreader.mobile.annotation

import kotlin.test.*

class TxtAnnotationsTest {
    @Test fun repeatedQuotesUseExactSelectedUtf16CoordinatesIncludingEmojiAndReverseSelection() {
        val text = "😀 같은 문장\n같은 문장 끝"
        val start = text.lastIndexOf("같은 문장")
        assertEquals(TxtQuote(900 + start.toLong(), 900 + start + 5L, "같은 문장"),
            TxtAnnotations.select(900, text, start + 5, start, 1000))
        assertEquals(TxtQuote(900,902,"😀"),TxtAnnotations.select(900,text,0,2,1000))
        assertNull(TxtAnnotations.select(900,text,1,2,1000))
        assertNull(TxtAnnotations.select(900,text,0,1,1000))
    }

    @Test fun invalidRangesOverflowUnpairedSurrogatesAndSizeLimitsAreRejected() {
        for ((a,b) in listOf(-1 to 1,0 to 4,1 to 1)) assertNull(TxtAnnotations.select(0,"abc",a,b,3))
        assertNull(TxtAnnotations.select(Long.MAX_VALUE,"abc",0,1,Long.MAX_VALUE))
        assertNull(TxtAnnotations.select(-1,"abc",0,1,3))
        assertNull(TxtAnnotations.select(0,"\uD800x",0,2,2))
        assertNull(TxtAnnotations.select(0," \n",0,2,2))
        val limit = "x".repeat(TxtAnnotations.MAX_QUOTE)
        assertNotNull(TxtAnnotations.select(0,limit,0,limit.length,limit.length.toLong()))
        assertNull(TxtAnnotations.select(0,limit+"x",0,1,limit.length+1L))
        assertFalse(TxtAnnotations.validNote("x".repeat(TxtAnnotations.MAX_NOTE+1)))
        assertFalse(TxtAnnotations.validNote("\uDC00"))
        assertFalse(TxtAnnotations.validNote("nul\u0000"))
        assertTrue(TxtAnnotations.validNote("메모\n다음\t😀"))
    }

    @Test fun clippedHighlightSpansCheckExactTextAndNeverSearchForAnotherOccurrence() {
        val entry=TxtAnnotation("a",TxtQuote(10,15,"ABCDE"),HighlightColor.BLUE,"","one")
        assertEquals(listOf(TxtHighlightSpan(0,3,HighlightColor.BLUE)),TxtAnnotations.spans(12,"CDE tail",listOf(entry)))
        assertEquals(listOf(TxtHighlightSpan(2,5,HighlightColor.BLUE)),TxtAnnotations.spans(8,"xxABC",listOf(entry)))
        assertTrue(TxtAnnotations.spans(10,"ABCDE",emptyList()).isEmpty())
        assertTrue(TxtAnnotations.spans(10,"wrong ABCDE",listOf(entry)).isEmpty())
        assertTrue(TxtAnnotations.spans(30,"ABCDE",listOf(entry)).isEmpty())
    }
}
