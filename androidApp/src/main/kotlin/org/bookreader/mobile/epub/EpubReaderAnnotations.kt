package org.bookreader.mobile.epub

import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bookreader.mobile.annotation.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.reader.paper
import org.bookreader.mobile.repository.ProgressSession
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator

data class EpubAnnotationsState(val entries:List<EpubAnnotation> = emptyList(),val ready:Boolean=false,
    val loading:Boolean=false,val writing:Boolean=false,val error:Boolean=false,val renderError:Boolean=false,
    val dialog:Boolean=false,val draft:EpubAnnotation?=null,val expected:EpubAnnotation?=null,
    val selectionError:Boolean=false,val selecting:Boolean=false,val savedVersion:Long=0)

/** Reader lifetime owns storage, capture and rendering; none of these writes progress. */
class EpubReaderAnnotations(private val book:Book,private val scope:CoroutineScope,
    private val session:()->ProgressSession?,private val isReady:()->Boolean,
    private val access:EpubAnnotationsAccess?,private val bridge:EpubAnnotationBridge,
    private val validate:(Locator)->Unit,private val navigate:(Locator)->Unit) {
    companion object { const val GROUP="bookreader-annotations" }
    private val mutableState=MutableStateFlow(EpubAnnotationsState());val state=mutableState.asStateFlow()
    private var host:EpubNavigatorFragment?=null
    private var hostVersion=0L
    private var loadJob:Job?=null;private var captureJob:Job?=null;private var paintJob:Job?=null;private var writeJob:Job?=null
    private var closed=false
    private var draftSession:ProgressSession?=null
    fun attach(value:EpubNavigatorFragment) { hostVersion++;host=value }
    fun detach(value:EpubNavigatorFragment) {
        if(host===value) {hostVersion++;host=null;captureJob?.cancel();paintJob?.cancel();mutableState.update{it.copy(selecting=false)}}
    }
    fun load(force:Boolean=false) {
        if(closed || !isReady() || state.value.loading || state.value.writing || state.value.ready && !force) return
        mutableState.update{it.copy(loading=true,ready=false,error=false)}
        loadJob=scope.launch {
            try {
                val result=access?.load(book.id,requireNotNull(book.currentRevision)) ?: EpubAnnotationsRead.Ready(emptyList())
                val entries=(result as? EpubAnnotationsRead.Ready)?.entries ?: error("Annotation read failed")
                require(entries.size<=TxtAnnotations.MAX_ENTRIES)
                entries.forEach { EpubAnnotations.validate(it); validate(sdk(it)) }
                ensureActive();mutableState.update{it.copy(entries=entries,loading=false,ready=true,error=false)}
                render()
            } catch(c:CancellationException){throw c}
            catch(_:Exception){mutableState.update{it.copy(loading=false,ready=false,error=true,entries=emptyList())};render()}
        }
    }
    fun open() { mutableState.update{it.copy(dialog=true)};load() }
    fun dismiss() { if(!state.value.writing) {draftSession=null;mutableState.update{it.copy(dialog=false,draft=null,expected=null,selectionError=false)}} }
    fun cancelEdit() { if(!state.value.writing) {draftSession=null;mutableState.update{it.copy(draft=null,expected=null)}} }
    fun capture(onCaptured:()->Unit={}) {
        val owner=host ?: return;val token=hostVersion;val active=session() ?: return
        if(state.value.selecting) return
        if(!canWrite() || state.value.entries.size>=TxtAnnotations.MAX_ENTRIES) {
            open();owner.clearSelection();onCaptured();return
        }
        mutableState.update{it.copy(selecting=true,selectionError=false)}
        captureJob=scope.launch {
            try {
                val selected=requireNotNull(withTimeoutOrNull(10_000){bridge.capture(owner)})
                ensureActive();check(host===owner && token==hostVersion && active==session() && isReady())
                validate(selected.second)
                val entry=EpubAnnotation(UUID.randomUUID().toString(),selected.first,selected.second.toJSON().toString(),
                    HighlightColor.YELLOW,"",UUID.randomUUID().toString())
                EpubAnnotations.validate(entry);draftSession=active
                mutableState.update{it.copy(draft=entry,expected=null,dialog=true,selecting=false)}
                owner.clearSelection();onCaptured()
            } catch(c:CancellationException){throw c}
            catch(_:Exception){if(host===owner && token==hostVersion) {
                mutableState.update{it.copy(selectionError=true,selecting=false,dialog=true)}
                owner.clearSelection();onCaptured()
            }}
        }
    }
    fun edit(entry:EpubAnnotation) {
        if(!canWrite() || entry !in state.value.entries) return
        draftSession=session();mutableState.update{it.copy(draft=entry,expected=entry,dialog=true)}
    }
    fun save(color:HighlightColor,note:String) {
        val draft=state.value.draft ?: return
        change(draft.id,state.value.expected,draft.copy(color=color,note=note,changeId=UUID.randomUUID().toString()))
    }
    fun delete() { val expected=state.value.expected ?: return;change(expected.id,expected,null) }
    fun jump(entry:EpubAnnotation) { if(canWrite() && entry in state.value.entries) {navigate(sdk(entry));dismiss()} }
    private fun canWrite()= !closed && scope.isActive && access!=null && isReady() && state.value.ready &&
        !state.value.writing && !state.value.error && !state.value.renderError
    private fun change(id:String,expected:EpubAnnotation?,replacement:EpubAnnotation?) {
        val active=session() ?: return;val repository=access ?: return
        if(!canWrite() || active!=draftSession || state.value.entries.firstOrNull{it.id==id}!=expected) return
        try {replacement?.let(EpubAnnotations::validate)} catch(_:Exception){return}
        mutableState.update{it.copy(writing=true,error=false)}
        writeJob=scope.launch {
            try {
                // New captures are revalidated after dialog/IME host replacement before saving.
                if(expected==null && replacement!=null) {
                    val owner=host ?: error("Host unavailable");val token=hostVersion
                    check(withTimeoutOrNull(10_000){bridge.matches(owner,replacement.anchor)}==true)
                    ensureActive();check(host===owner && token==hostVersion && active==session())
                }
                check(repository.save(active,id,expected,replacement)==AnnotationWrite.SAVED)
                ensureActive();draftSession=null
                mutableState.update{it.copy(writing=false,ready=false,draft=null,expected=null,savedVersion=it.savedVersion+1)}
                load(force=true)
            } catch(c:CancellationException){throw c}
            catch(_:Exception){mutableState.update{it.copy(writing=false,error=true,ready=false)};render()}
        }
    }
    fun render() {
        val owner=host ?: return;val token=hostVersion
        if(closed || !isReady()) return
        paintJob?.cancel()
        paintJob=scope.launch {
            try {
                val entries=if(state.value.ready && !state.value.error) state.value.entries.filter{it.anchor.href==owner.currentLocator.value.href.toString()} else emptyList()
                check(withTimeoutOrNull(10_000) {for(entry in entries) {ensureActive();check(bridge.matches(owner,entry.anchor))};true}==true)
                ensureActive();if(host!==owner || token!=hostVersion || !isReady()) return@launch
                check(owner.supportsDecorationStyle(Decoration.Style.Highlight::class))
                owner.applyDecorations(entries.map{Decoration(it.id,sdk(it),Decoration.Style.Highlight(it.color.paper().toArgb()))},GROUP)
                ensureActive();if(host===owner && token==hostVersion) mutableState.update{it.copy(renderError=false)}
            } catch(c:CancellationException){throw c}
            catch(_:Exception) {
                if(host===owner && token==hostVersion) {
                    owner.applyDecorations(emptyList(),GROUP)
                    mutableState.update{it.copy(renderError=true)}
                }
            }
        }
    }
    fun close() {closed=true;hostVersion++;host=null;loadJob?.cancel();captureJob?.cancel();paintJob?.cancel();writeJob?.cancel();mutableState.value=EpubAnnotationsState()}
    private fun sdk(entry:EpubAnnotation)=requireNotNull(Locator.fromJSON(JSONObject(entry.locatorJson)))
}
