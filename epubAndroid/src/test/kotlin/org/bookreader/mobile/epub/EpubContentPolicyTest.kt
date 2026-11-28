package org.bookreader.mobile.epub

import org.junit.Assert.*
import org.junit.Test

class EpubContentPolicyTest {
    private fun html(body: String) = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Fixture</title><style>p { color: blue; }</style></head><body>$body</body></html>".toByteArray()
    @Test fun passiveTextLocalImagesCssAndAnchorsSurviveCspInjection() {
        val result = EpubContentPolicy.protectXhtml(html("<p id=\"anchor\">한글😀</p><img src=\"image.png\"/><a href=\"#anchor\">이동</a>")).toString(Charsets.UTF_8)
        assertTrue(result.contains("Content-Security-Policy")); assertTrue(result.contains("한글")); assertTrue(result.contains("image.png"))
        assertTrue(result.indexOf("Content-Security-Policy") < result.indexOf("<style>"))
        val directives = EpubContentPolicy.CSP.split(';').map(String::trim)
        val fonts = directives.single { it.startsWith("font-src ") }.split(' ').drop(1)
        assertEquals(setOf("https://readium/publication/", "https://readium/assets/readium/",
            "https://readium/assets/reader-fonts/NanumGothic-Regular.ttf",
            "https://readium/assets/reader-fonts/NanumMyeongjo-Regular.ttf"), fonts.toSet())
        assertTrue(directives.contains("connect-src 'none'"))
        assertEquals("script-src https://readium/assets/readium/scripts/", directives.single { it.startsWith("script-src ") })
    }
    @Test fun supplementaryTextAndAttributesRemainValidUtf8AfterCspInjection() {
        val text="한글😀𐀀"+String(Character.toChars(0x10ffff))+" & < > literal &#55357;"
        val escaped=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        val output=EpubContentPolicy.protectXhtml(html("<p id=\"unicode\" title=\"$escaped\">$escaped</p>"))
        val xml=output.toString(Charsets.UTF_8)
        assertFalse(xml.contains("encoding=\"UTF-16\""))
        assertFalse(xml.contains("&#55357;&#56832;"))
        // Parse trusted engine output independently: the publication-input policy deliberately
        // rejects a book's own HTTP directives, including the CSP added by this engine.
        val parsed=javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true }
            .newDocumentBuilder().parse(java.io.ByteArrayInputStream(output))
        val paragraph=parsed.getElementsByTagNameNS("*","p").item(0) as org.w3c.dom.Element
        assertEquals(text,paragraph.textContent);assertEquals(text,paragraph.getAttribute("title"))
        assertTrue(xml.contains("Content-Security-Policy"))
    }
    @Test fun activeMarkupExternalImagesAndTraversalAreRejected() {
        for (body in listOf("<script>alert(1)</script>", "<p onclick=\"alert(1)\">x</p>", "<iframe src=\"x\"/>",
            "<a href=\"javascript:alert(1)\">x</a>", "<img src=\"https://example.invalid/track\"/>",
            "<img src=\"//example.invalid/track\"/>", "<img src=\"../other-book/image\"/>", "<svg><foreignObject/></svg>")) {
            assertThrows(Exception::class.java) { EpubContentPolicy.protectXhtml(html(body)) }
        }
    }
    @Test fun oversizedLinksFailBeforeReachingAnyBrowserIntent() {
        val normal = "https://example.invalid/" + "a".repeat(1024)
        assertTrue(EpubContentPolicy.protectXhtml(html("<a href=\"$normal\">open</a>")).isNotEmpty())
        val oversized = "https://example.invalid/" + "a".repeat(8192)
        assertThrows(Exception::class.java) { EpubContentPolicy.protectXhtml(html("<a href=\"$oversized\">open</a>")) }
    }
    @Test fun dtdEntitiesAndExcessiveNestingFailClosed() {
        assertThrows(Exception::class.java) { EpubContentPolicy.validateXml("<!DOCTYPE html [<!ENTITY secret SYSTEM 'file:///private/canary'>]><html>&secret;</html>".toByteArray(), true) }
        assertThrows(Exception::class.java) { EpubContentPolicy.validateXml(("<a>".repeat(65) + "</a>".repeat(65)).toByteArray(), true) }
    }
}
