package org.bookreader.mobile.annotation

import kotlinx.serialization.json.*
import kotlin.test.*

internal fun epubEntry(id:String="one"):EpubAnnotation {
    val a=EpubAnchor("OPS/one.xhtml","body > :nth-child(2)",14,18,"same","😀 alpha same "," 끝.")
    val locator=buildJsonObject {put("href",a.href);put("type","application/xhtml+xml")
        putJsonObject("locations"){put("cssSelector",a.selector);put("progression",0.3)}
        putJsonObject("text"){put("highlight",a.text);put("before",a.before);put("after",a.after)}}
    return EpubAnnotation(id,a,locator.toString(),HighlightColor.YELLOW,"내 메모","change1")
}
class EpubAnnotationsTest {
    @Test fun completeLocatorRetainsExactRepeatedQuoteScopeAndUnicode() {
        val entry=epubEntry();EpubAnnotations.validate(entry)
        val copy=EpubAnnotations.json.decodeFromString<EpubAnchor>(EpubAnnotations.json.encodeToString(EpubAnchor.serializer(),entry.anchor))
        assertEquals(entry.anchor,copy);assertEquals("same",copy.text)
    }
    @Test fun omittedEmptyEdgeContextsRoundTripButMissingNonemptyContextIsRejected() {
        val base=epubEntry()
        fun withoutContexts(entry:EpubAnnotation):EpubAnnotation {
            val locator=EpubAnnotations.json.parseToJsonElement(entry.locatorJson).jsonObject
            return entry.copy(locatorJson=JsonObject(locator + ("text" to buildJsonObject{put("highlight",entry.anchor.text)})).toString())
        }
        val whole=base.copy(anchor=base.anchor.copy(start=0,end=4,before="",after=""))
        EpubAnnotations.validate(withoutContexts(whole))
        assertFails{EpubAnnotations.validate(withoutContexts(base))}
        val beforeOnly=base.copy(anchor=base.anchor.copy(after=""))
        val locator=EpubAnnotations.json.parseToJsonElement(beforeOnly.locatorJson).jsonObject
        EpubAnnotations.validate(beforeOnly.copy(locatorJson=JsonObject(locator+("text" to buildJsonObject{
            put("highlight","same");put("before",base.anchor.before)
        })).toString()))
    }
    @Test fun malformedExternalAmbiguousShapeAndOversizeValuesFailClosed() {
        val entry=epubEntry()
        for(bad in listOf(entry.copy(anchor=entry.anchor.copy(href="https://invalid")),entry.copy(anchor=entry.anchor.copy(selector="body,script")),
            entry.copy(anchor=entry.anchor.copy(end=19)),entry.copy(anchor=entry.anchor.copy(text="\uD800xxx")),
            entry.copy(note="x".repeat(2001)),entry.copy(locatorJson="x".repeat(16385)),entry.copy(changeId="bad'"),
            entry.copy(locatorJson=entry.locatorJson.replace("0.3","9.0")))) assertFails{EpubAnnotations.validate(bad)}
    }
}
