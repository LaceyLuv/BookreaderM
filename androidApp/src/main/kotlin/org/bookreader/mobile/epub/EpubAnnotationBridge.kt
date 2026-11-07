package org.bookreader.mobile.epub

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.annotation.*
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

/** Public SDK selection + a bounded, unique DOM/text check. Never patches Readium internals. */
class EpubAnnotationBridge(private val script:String) {
    suspend fun capture(host:EpubNavigatorFragment):Pair<EpubAnchor,Locator>? {
        val href=host.currentLocator.value.href.toString()
        val raw=host.evaluateJavascript("$script(${JSONObject().put("href",href)});") ?: return null
        currentCoroutineContext().ensureActive()
        if (raw=="null") return null
        val anchor=EpubAnnotations.json.decodeFromString<EpubAnchor>(raw)
        val selection=host.currentSelection() ?: return null
        if (selection.locator.text.highlight!=anchor.text || selection.locator.href.toString()!=href ||
            host.currentLocator.value.href!=selection.locator.href) return null
        val json=selection.locator.toJSON()
        val locations=json.optJSONObject("locations") ?: JSONObject()
        locations.put("cssSelector",anchor.selector)
        json.put("locations",locations).put("text",JSONObject().put("highlight",anchor.text).put("before",anchor.before).put("after",anchor.after))
        val locator=Locator.fromJSON(json) ?: return null
        EpubAnnotations.validate(EpubAnnotation("validation",anchor,locator.toJSON().toString(),HighlightColor.YELLOW,"","validation"))
        return anchor to locator
    }
    suspend fun matches(host:EpubNavigatorFragment,anchor:EpubAnchor):Boolean {
        if (host.currentLocator.value.href.toString()!=anchor.href) return false
        val input=JSONObject().put("href",anchor.href).put("anchor",JSONObject(EpubAnnotations.json.encodeToString(EpubAnchor.serializer(),anchor)))
        return host.evaluateJavascript("$script($input);")=="true"
    }
}
