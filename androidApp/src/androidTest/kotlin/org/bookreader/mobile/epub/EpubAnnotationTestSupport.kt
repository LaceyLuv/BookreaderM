package org.bookreader.mobile.epub

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import org.bookreader.mobile.annotation.EpubAnnotation
import org.json.JSONObject
import org.junit.Assert.*

/** Generated fixture DOM only; tests independently compare the SDK's painted boxes with a DOM Range. */
internal fun <A:Activity> selectEpubFixture(scenario:ActivityScenario<A>,chapter:String,id:String,start:Int,end:Int) {
    assertEquals("true",evaluateLoadedChapter(scenario,chapter,"""
        (()=>{const node=document.getElementById('$id').firstChild,r=document.createRange();
        r.setStart(node,$start);r.setEnd(node,$end);const s=getSelection();s.removeAllRanges();s.addRange(r);return !s.isCollapsed;})()
    """.trimIndent()))
}

internal fun <A:Activity> epubAnnotationBoxes(scenario:ActivityScenario<A>,chapter:String,id:String,start:Int,end:Int,entry:EpubAnnotation):JSONObject =
    JSONObject(evaluateLoadedChapter(scenario,chapter,"""
        (()=>{const r=document.createRange(),n=document.getElementById('$id').firstChild;r.setStart(n,$start);r.setEnd(n,$end);
        const box=b=>({left:b.left,top:b.top,right:b.right,bottom:b.bottom});
        return {target:Array.from(r.getClientRects(),box),actual:Array.from(document.querySelectorAll('[data-bookreader-annotation="${entry.id}"]'),e=>box(e.getBoundingClientRect())),
        width:innerWidth,height:innerHeight,selector:document.querySelector('${entry.anchor.selector}').id};})()
    """.trimIndent()))

internal fun assertEpubAnnotationBoxes(observation:JSONObject,id:String) {
    assertEquals(id,observation.getString("selector"))
    val expected=observation.getJSONArray("target");val actual=observation.getJSONArray("actual")
    assertTrue("The fixture must have a painted highlight",actual.length()>0)
    assertEquals("Every source fragment must have exactly one decoration",expected.length(),actual.length())
    for(i in 0 until expected.length()) for(edge in listOf("left","top","right","bottom"))
        assertEquals("Highlight $i $edge must match the selected range",expected.getJSONObject(i).getDouble(edge),actual.getJSONObject(i).getDouble(edge),2.0)
    val first=actual.getJSONObject(0)
    assertTrue("Highlight must be visible",first.getDouble("left")>=0 && first.getDouble("right")<=observation.getDouble("width") &&
        first.getDouble("top")>=0 && first.getDouble("bottom")<=observation.getDouble("height"))
}
