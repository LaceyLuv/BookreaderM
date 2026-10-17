package org.bookreader.mobile.epub

import org.junit.Assert.*
import org.junit.Test

class EpubContentPolicyTest {
    private fun html(body: String) = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Fixture</title><style>p { color: blue; }</style></head><body>$body</body></html>".toByteArray()
    @Test fun passiveTextLocalImagesCssAndAnchorsSurviveCspInjection() {
        val result = EpubContentPolicy.protectXhtml(html("<p id=\"anchor\">한글😀</p><img src=\"image.png\"/><a href=\"#anchor\">이동</a>")).toString(Charsets.UTF_8)
        assertTrue(result.contains("Content-Security-Policy")); assertTrue(result.contains("한글")); assertTrue(result.contains("image.png"))
        assertTrue(result.indexOf("Content-Security-Policy") < result.indexOf("<style>"))
    }
    @Test fun activeMarkupExternalImagesAndTraversalAreRejected() {
        for (body in listOf("<script>alert(1)</script>", "<p onclick=\"alert(1)\">x</p>", "<iframe src=\"x\"/>",
            "<a href=\"javascript:alert(1)\">x</a>", "<img src=\"https://example.invalid/track\"/>",
            "<img src=\"//example.invalid/track\"/>", "<img src=\"../other-book/image\"/>", "<svg><foreignObject/></svg>")) {
            assertThrows(Exception::class.java) { EpubContentPolicy.protectXhtml(html(body)) }
        }
    }
    @Test fun dtdEntitiesAndExcessiveNestingFailClosed() {
        assertThrows(Exception::class.java) { EpubContentPolicy.validateXml("<!DOCTYPE html [<!ENTITY secret SYSTEM 'file:///private/canary'>]><html>&secret;</html>".toByteArray(), true) }
        assertThrows(Exception::class.java) { EpubContentPolicy.validateXml(("<a>".repeat(65) + "</a>".repeat(65)).toByteArray(), true) }
    }
}
