package org.bookreader.mobile.locator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class LocatorCodecTest {
    private val codec = LocatorCodec()

    @Test
    fun txtRoundTripKeepsLongUtf16AnchorAndRevision() {
        val locator = TxtLocator("source+utf8+normalization1", TxtLocatorPayload(4_294_967_296L, TextAffinity.TRAILING, "context"))
        val encoded = codec.encode(locator)
        assertEquals(locator, assertIs<LocatorDecodeResult.Success>(codec.decode(encoded)).locator)
        val envelope = Json.parseToJsonElement(encoded).jsonObject
        assertEquals(setOf("type", "version", "contentRevision", "payload"), envelope.keys)
        assertFalse("page" in encoded)
    }

    @Test
    fun epubRoundTripPreservesCompleteSdkLocatorIncludingExtensionFields() {
        val publicLocator = Json.parseToJsonElement(
            """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"fragments":["anchor"],"progression":0.42,"position":7},"text":{"before":"before","highlight":"한글 😀","after":"after"},"vendorExtension":{"value":true}}""",
        ).jsonObject
        val locator = EpubLocator("epub-revision", EpubLocatorPayload(locator = publicLocator))
        assertEquals(locator, assertIs<LocatorDecodeResult.Success>(codec.decode(codec.encode(locator))).locator)
    }

    @Test
    fun comicIdentityIsEntryKeyAndAnchorNotImageHint() {
        val locator = ComicLocator("comic-revision", ComicLocatorPayload("folder/001.png", imageIndexHint = 12, normalizedX = 0.25, normalizedY = 0.75))
        assertEquals(locator, assertIs<LocatorDecodeResult.Success>(codec.decode(codec.encode(locator))).locator)
    }

    @Test
    fun unknownVersionAndTypeRemainUnsupported() {
        assertEquals(
            LocatorDecodeResult.Unsupported("txt", 2),
            codec.decode("""{"type":"txt","version":2,"contentRevision":"r1","payload":{"futureOffset":1}}"""),
        )
        assertEquals(
            LocatorDecodeResult.Unsupported("future-format", 1),
            codec.decode("""{"type":"future-format","version":1,"contentRevision":"r1","payload":{}}"""),
        )
    }

    @Test
    fun malformedAndInvalidCoordinatesNeverBecomeZeroLocator() {
        listOf(
            "not-json",
            """{"type":"txt","version":1,"contentRevision":"","payload":{"utf16Offset":10}}""",
            """{"type":"txt","version":1,"contentRevision":"r","payload":{"utf16Offset":-1}}""",
            """{"type":"txt","version":1,"contentRevision":"r","payload":{"page":1}}""",
            """{"type":"comic","version":1,"contentRevision":"r","payload":{"entryKey":"../1.png","normalizedY":0.3}}""",
            """{"type":"comic","version":1,"contentRevision":"r","payload":{"entryKey":"1.png","normalizedY":1.5}}""",
        ).forEach { assertEquals(LocatorDecodeResult.Malformed, codec.decode(it)) }
    }

    @Test
    fun changedRevisionIsNotCollapsedIntoSameAnchor() {
        assertNotEquals(
            codec.encode(TxtLocator("utf8-r1", TxtLocatorPayload(42))),
            codec.encode(TxtLocator("cp949-r2", TxtLocatorPayload(42))),
        )
    }
}
