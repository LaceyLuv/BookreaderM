package org.bookreader.mobile.annotation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.bookreader.mobile.repository.ProgressSession

@Serializable
data class EpubAnchor(val href: String, val selector: String, val start: Int, val end: Int,
    val text: String, val before: String, val after: String)
data class EpubAnnotation(val id: String, val anchor: EpubAnchor, val locatorJson: String,
    val color: HighlightColor, val note: String, val changeId: String)
sealed interface EpubAnnotationsRead {
    data class Ready(val entries: List<EpubAnnotation>) : EpubAnnotationsRead
    data object Error : EpubAnnotationsRead
}
interface EpubAnnotationsAccess {
    suspend fun load(bookId: String, revision: String): EpubAnnotationsRead
    suspend fun save(session: ProgressSession, id: String, expected: EpubAnnotation?, replacement: EpubAnnotation?): AnnotationWrite
}
object EpubAnnotations {
    const val VERSION = 1
    const val MAX_LOCATOR = 16384
    const val MAX_ANCHOR = 16384
    const val MAX_SCOPE = 65536
    const val MAX_CONTEXT = 256
    val json = Json { encodeDefaults = true }
    fun validate(entry: EpubAnnotation) {
        require(entry.id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && entry.changeId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        require(TxtAnnotations.validNote(entry.note))
        val a=entry.anchor
        require(a.href.isNotBlank() && a.href.length<=4096 && !a.href.startsWith('/') &&
            a.href.none { it.code<32 || it in ":?#\\" } && a.href.split('/').none { it=="." || it==".." })
        require(a.selector.length<=4096 && a.selector.matches(Regex("body( > :nth-child\\([1-9][0-9]{0,5}\\)){0,64}")))
        require(a.start>=0 && a.end>a.start && a.end<=MAX_SCOPE && a.end-a.start==a.text.length &&
            a.text.length<=TxtAnnotations.MAX_QUOTE && a.text.isNotBlank())
        require(a.before.length<=MAX_CONTEXT && a.after.length<=MAX_CONTEXT &&
            listOf(a.text,a.before,a.after).all(TxtAnnotations::validUnicode))
        require(json.encodeToString(EpubAnchor.serializer(),a).length<=MAX_ANCHOR)
        require(entry.locatorJson.length<=MAX_LOCATOR && TxtAnnotations.validUnicode(entry.locatorJson))
        val l=json.parseToJsonElement(entry.locatorJson).jsonObject
        require(l["href"]?.jsonPrimitive?.content==a.href)
        val locations=l["locations"]!!.jsonObject
        require(locations["cssSelector"]?.jsonPrimitive?.content==a.selector)
        for (key in listOf("progression","totalProgression")) locations[key]?.let {
            val value=it.jsonPrimitive.double;require(value.isFinite() && value in 0.0..1.0)
        }
        val text=l["text"]!!.jsonObject
        require(text["highlight"]?.jsonPrimitive?.content==a.text && text["before"]?.jsonPrimitive?.content==a.before &&
            text["after"]?.jsonPrimitive?.content==a.after)
    }
}
