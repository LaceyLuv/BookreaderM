package org.bookreader.mobile.epub

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.*
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.MainActivity
import org.bookreader.mobile.annotation.*
import org.bookreader.mobile.database.*
import org.bookreader.mobile.importing.*
import org.bookreader.mobile.reader.*
import org.bookreader.mobile.ui.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class EpubAnnotationsFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val target get()=instrumentation.targetContext
    private val database by lazy{createAndroidDatabase(target)}
    private var existing:Set<String>?=null
    private var global:ReaderGlobalSettingsEntity?=null
    @Before fun preserve(){existing=runBlocking{database.bookDao().listBooks().map{it.id}.toSet()};global=runBlocking{database.readerSettingsDao().global()}}
    @After fun cleanup(){try {
        compose.activityRule.scenario.close()
        val ids=existing ?: return
        runBlocking {database.bookDao().listBooks().filter{it.id !in ids && it.originalDisplayName=="../../epub-annotations.epub"}.forEach{
            assertEquals(DeleteResult.Deleted,AndroidBookManagement(target).delete(it.id))}
            if(global==null) database.useWriterConnection{it.execSQL("DELETE FROM reader_global_settings WHERE id='global'")}
            else database.readerSettingsDao().putGlobal(requireNotNull(global))}
    } finally {database.close()}}

    @Test fun nativeSelectionRepeatedQuoteNotesReflowAndReopenUseExactDecoration() {
        File(target.filesDir,"epub-annotation-observations.json").writeText("[]")
        open();val owner=reader();val book=owner.book
        val source=File(target.filesDir,"managed/${book.managedRelativePath}").readBytes()
        val original=progress();val viewport=compose.onNodeWithTag("epub_ready").fetchSemanticsNode().boundsInRoot.size
        val point=JSONObject(evaluateLoadedChapter(compose.activityRule.scenario,"one","""
            (()=>{const r=document.createRange(),n=document.getElementById('selection').firstChild;r.setStart(n,14);r.setEnd(n,18);
            const b=r.getBoundingClientRect();return {x:(b.left+b.right)/2,y:(b.top+b.bottom)/2,width:innerWidth};})()
        """.trimIndent()))
        var x=0f;var y=0f
        compose.activityRule.scenario.onActivity{activity->
            fun webs(v:View):List<WebView> = when(v){is WebView->listOf(v);is ViewGroup->(0 until v.childCount).flatMap{webs(v.getChildAt(it))};else->emptyList()}
            val web=webs(activity.window.decorView).first{it.isShown && it.url?.endsWith("/one.xhtml")==true}
            val location=IntArray(2);web.getLocationOnScreen(location);val scale=web.width/point.getDouble("width")
            x=(location[0]+point.getDouble("x")*scale).toFloat();y=(location[1]+point.getDouble("y")*scale).toFloat()
        }
        val started=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(started,SystemClock.uptimeMillis(),action,x,y,0);event.source=InputDevice.SOURCE_TOUCHSCREEN
            try{assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
            if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+400L)
        }
        compose.waitUntil(10_000){evaluateLoadedChapter(compose.activityRule.scenario,"one","getSelection().toString()") == "\"same\""}
        clickNativeHighlight()
        waitFor("epub_annotation_note")
        assertEquals(14,owner.annotations.state.value.draft!!.anchor.start)
        compose.onNodeWithTag("epub_annotation_color_PINK").performClick()
        compose.onNodeWithTag("epub_annotation_note").performScrollTo().performTextInput("반복 문장 메모 😀")
        hideIme(viewport)
        compose.onNodeWithTag("epub_annotation_save").performClick()
        compose.waitUntil(20_000){owner.annotations.state.value.let{it.ready && it.savedVersion==1L}}
        val entry=owner.annotations.state.value.entries.single()
        assertEquals("same",entry.anchor.text);assertEquals(14,entry.anchor.start);assertEquals(18,entry.anchor.end)
        assertEquals(HighlightColor.PINK,entry.color);assertEquals("반복 문장 메모 😀",entry.note)
        assertEquals(original,progress())
        compose.onNodeWithTag("epub_annotations_close").performClick();boxes("saved",entry)
        ReaderFixtureScreenshot.capture("phase3-epub-highlight.png")
        compose.readerControl("epub_annotations").performClick();waitFor("epub_annotation_edit_${entry.id}")
        ReaderFixtureScreenshot.capture("phase3-epub-annotations.png")
        compose.onNodeWithTag("epub_annotation_edit_${entry.id}").performClick()
        compose.onNodeWithTag("epub_annotation_note").performScrollTo().performTextReplacement("수정 메모")
        hideIme(viewport);compose.onNodeWithTag("epub_annotation_save").performClick()
        compose.waitUntil(20_000){owner.annotations.state.value.let{it.ready && it.savedVersion==2L}}
        val edited=owner.annotations.state.value.entries.single();assertEquals(entry.anchor,edited.anchor);assertEquals(entry.locatorJson,edited.locatorJson)
        assertEquals("수정 메모",edited.note);compose.onNodeWithTag("epub_annotations_close").performClick()
        compose.activityRule.scenario.recreate();ready();assertEquals(listOf(edited),reader().annotations.state.value.entries);boxes("recreated",edited)
        val previous=reader().state.value
        compose.readerControl("epub_settings").performClick();compose.onNodeWithTag("font_size_increase").performScrollTo().performClick()
        compose.onNodeWithTag("reader_settings_apply").performClick()
        compose.waitUntil(40_000){reader().let{it.state.value.hostVersion>previous.hostVersion && it.state.value.settings.fontSizeSp==previous.settings.fontSizeSp+1 && it.progress.state.value.phase==ArchiveReaderPhase.READY}}
        boxes("font-reflow",edited);assertEquals(original,progress())
        compose.readerControl("epub_back").performClick();waitFor("continue_reading");compose.onNodeWithTag("continue_reading").performClick();ready()
        assertEquals(listOf(edited),reader().annotations.state.value.entries);boxes("reopened",edited);assertEquals(original,progress())
        compose.readerControl("epub_toc").performClick();compose.onAllNodesWithTag("epub_toc_item")[1].performClick()
        compose.waitUntil(20_000){progress()?.locatorJson?.contains("two.xhtml")==true}
        compose.readerControl("epub_annotations").performClick();compose.onNodeWithTag("epub_annotation_jump_${entry.id}").performClick()
        compose.waitUntil(20_000){progress()?.locatorJson?.contains("one.xhtml")==true};boxes("jump",edited)
        compose.readerControl("epub_annotations").performClick();compose.onNodeWithTag("epub_annotation_edit_${entry.id}").performClick()
        compose.onNodeWithTag("epub_annotation_delete").performScrollTo().performClick()
        compose.waitUntil(20_000){reader().annotations.state.value.let{it.ready && it.entries.isEmpty()}}
        compose.onNodeWithTag("epub_annotations_close").performClick()
        compose.waitUntil(10_000){evaluateLoadedChapter(compose.activityRule.scenario,"one","document.querySelectorAll('[data-bookreader-annotation]').length")=="0"}
        assertArrayEquals(source,File(target.filesDir,"managed/${book.managedRelativePath}").readBytes())
    }

    @Test fun ambiguousSelectionAndChangedDomRejectWithoutRewritingRecords() {
        open();val owner=reader();val before=progress()
        selectEpubFixture(compose.activityRule.scenario,"one","ambiguous",700,706)
        compose.runOnIdle{owner.annotations.capture()};waitFor("epub_annotation_selection_error")
        assertNull(owner.annotations.state.value.draft);assertTrue(rows().isEmpty());assertEquals(before,progress())
        compose.onNodeWithTag("epub_annotations_close").performClick()
        selectEpubFixture(compose.activityRule.scenario,"one","selection",14,18)
        compose.runOnIdle{owner.annotations.capture()};waitFor("epub_annotation_note")
        // Fixture-only DOM mutation between capture and save must not persist a guessed match.
        assertEquals("true",evaluateLoadedChapter(compose.activityRule.scenario,"one","(()=>{document.getElementById('selection').firstChild.data='changed same same';return true})()"))
        compose.onNodeWithTag("epub_annotation_save").performClick()
        compose.waitUntil(20_000){owner.annotations.state.value.error}
        assertTrue(rows().isEmpty());assertEquals(before,progress())
    }
    private fun clickNativeHighlight(){
        var clicked=false
        compose.waitUntil(10_000){
            val roots=instrumentation.uiAutomation.windows.mapNotNull{it.root}+listOfNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            for(root in roots) for(node in root.findAccessibilityNodeInfosByText("형광펜 · 메모")) {
                if(node.text?.toString()!="형광펜 · 메모")continue
                var candidate:android.view.accessibility.AccessibilityNodeInfo?=node
                repeat(3){val current=candidate;if(current!=null && !clicked){clicked=current.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);candidate=current.parent}}
            };clicked
        }
    }
    private fun hideIme(viewport:androidx.compose.ui.geometry.Size){
        var visible=false;compose.activityRule.scenario.onActivity{visible=androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true}
        if(visible)instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(20_000){reader().progress.state.value.phase==ArchiveReaderPhase.READY && compose.onAllNodesWithTag("epub_ready").fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.size==viewport}
    }
    private fun boxes(stage:String,entry:EpubAnnotation){
        var value:JSONObject?=null
        compose.waitUntil(20_000){value=epubAnnotationBoxes(compose.activityRule.scenario,"one","selection",14,18,entry);value!!.getJSONArray("actual").length()>0}
        val report=File(target.filesDir,"epub-annotation-observations.json")
        val list=if(report.exists())org.json.JSONArray(report.readText())else org.json.JSONArray()
        list.put(value!!.put("stage",stage));report.writeText(list.toString(2));assertEpubAnnotationBoxes(value!!,"selection")
    }
    private fun open(){
        instrumentation.context.startActivity(Intent().apply{component=ComponentName(instrumentation.context,TestSenderActivity::class.java);putExtra("fixture","epub-annotations");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)})
        ready()
        val book=reader().book
        assertEquals("../../epub-annotations.epub",book.originalDisplayName)
        val expected=target.assets.open("epub-fixtures/annotations.epub").use{it.readBytes()}
        assertArrayEquals(expected,File(target.filesDir,"managed/${book.managedRelativePath}").readBytes())
        // READY/complete may precede the first visible content frame. Probe before dereferencing.
        // Body diagnostics are allowed only after the managed file is proved to be our fixture.
        val report=File(target.filesDir,"epub-annotation-fixture-observations.json")
        var observation=JSONObject()
        try {compose.waitUntil(15_000){
            observation=JSONObject(evaluateLoadedChapter(compose.activityRule.scenario,"one","""
                ({ready:document.readyState,selection:!!document.getElementById('selection'),ambiguous:!!document.getElementById('ambiguous'),
                ids:Array.from(document.querySelectorAll('[id]')).slice(0,16).map(e=>e.id),body:document.body.innerText.slice(0,500)})
            """.trimIndent()))
            observation.getBoolean("selection") && observation.getBoolean("ambiguous")
        }} finally {
            val observations=if(report.exists())org.json.JSONArray(report.readText())else org.json.JSONArray()
            observations.put(observation.put("fixtureBytes",expected.size));report.writeText(observations.toString(2))
        }
    }
    private fun ready(){waitFor("epub_ready");compose.waitUntil(20_000){reader().annotations.state.value.ready}}
    private fun reader():EpubReaderController{var owner:EpubReaderController?=null;compose.activityRule.scenario.onActivity{owner=ViewModelProvider(it)[AppViewModel::class.java].state.value.epubReader};return requireNotNull(owner)}
    private fun progress()=runBlocking{val b=reader().book;database.progressDao().findProgress(b.id,requireNotNull(b.currentRevision))}
    private fun rows()=runBlocking{val b=reader().book;database.epubAnnotationDao().entries(b.id,requireNotNull(b.currentRevision))}
    private fun waitFor(tag:String){compose.waitUntil(40_000){compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty()};compose.onNodeWithTag(tag).assertIsDisplayed()}
}
