package org.bookreader.mobile.epub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bookreader.mobile.annotation.*
import org.bookreader.mobile.reader.*

@Composable fun EpubAnnotationsDialog(controller:EpubReaderController) {
    val annotations=controller.annotations
    val state=annotations.state.collectAsStateWithLifecycle().value
    val progress=controller.progress.state.collectAsStateWithLifecycle().value
    val enabled=state.ready && !state.writing && !state.error && !state.renderError && progress.phase==ArchiveReaderPhase.READY
    if(!state.dialog) return
    AlertDialog(onDismissRequest=annotations::dismiss,title={Text("형광펜 · 메모")},text={Column {
        Text("본문을 길게 누른 뒤 선택 메뉴의 형광펜 · 메모를 눌러 저장하세요.")
        if(state.loading || state.selecting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.selectionError) Text("선택 위치를 정확히 확인하지 못했습니다. 더 짧은 문장을 선택해 주세요.",Modifier.testTag("epub_annotation_selection_error"))
        if(state.error || state.renderError) {
            Text("원문과 기록을 확인하지 못했습니다. 기존 기록은 보존됩니다.",Modifier.testTag("epub_annotation_error"),color=MaterialTheme.colorScheme.error)
            TextButton(onClick={annotations.load(force=true)},modifier=Modifier.testTag("epub_annotation_retry")){Text("다시 읽기")}
        }
        if(state.ready && state.entries.isEmpty()) Text("저장한 문장이 없습니다.")
        if(state.entries.size==TxtAnnotations.MAX_ENTRIES) Text("이 책의 형광펜 저장 한도 500개에 도달했습니다.")
        LazyColumn(Modifier.heightIn(max=340.dp).testTag("epub_annotation_entries")) {
            items(state.entries,key={it.id}){entry->Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                Text(entry.anchor.text,Modifier.fillMaxWidth().background(entry.color.paper()).padding(8.dp),color=Color(0xFF171717),maxLines=3)
                if(entry.note.isNotBlank()) Text(entry.note,maxLines=3)
                Row {
                    TextButton(enabled=enabled,onClick={annotations.jump(entry)},modifier=Modifier.testTag("epub_annotation_jump_${entry.id}")){Text("본문으로")}
                    TextButton(enabled=enabled,onClick={annotations.edit(entry)},modifier=Modifier.testTag("epub_annotation_edit_${entry.id}")){Text("수정")}
                }
            }}
        }
    }},confirmButton={TextButton(enabled=!state.writing,onClick=annotations::dismiss,modifier=Modifier.testTag("epub_annotations_close")){Text("닫기")}})
    state.draft?.let{draft->key(draft.id,draft.changeId){
        var color by remember{mutableStateOf(draft.color)};var note by remember{mutableStateOf(draft.note)}
        AlertDialog(onDismissRequest=annotations::cancelEdit,title={Text("형광펜 · 메모 저장")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(draft.anchor.text,maxLines=6,modifier=Modifier.testTag("epub_annotation_quote"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {HighlightColor.entries.forEach{candidate->
                FilterChip(selected=color==candidate,enabled=!state.writing,onClick={color=candidate},modifier=Modifier.testTag("epub_annotation_color_${candidate.name}"),label={Text(candidate.label())})
            }}
            OutlinedTextField(note,{if(it.length<=TxtAnnotations.MAX_NOTE)note=it},enabled=!state.writing,label={Text("메모 · 선택 사항")},
                supportingText={Text("${note.length} / ${TxtAnnotations.MAX_NOTE}")},modifier=Modifier.testTag("epub_annotation_note"))
            if(state.error) Text("저장하지 못했습니다. 창을 닫고 기록을 다시 읽어 주세요.",color=MaterialTheme.colorScheme.error)
            if(state.expected!=null) TextButton(enabled=enabled,onClick=annotations::delete,modifier=Modifier.testTag("epub_annotation_delete")){Text("형광펜과 메모 삭제")}
        }},confirmButton={TextButton(enabled=enabled && TxtAnnotations.validNote(note),onClick={annotations.save(color,note)},modifier=Modifier.testTag("epub_annotation_save")){Text("저장")}},
            dismissButton={TextButton(enabled=!state.writing,onClick=annotations::cancelEdit,modifier=Modifier.testTag("epub_annotation_cancel")){Text("취소")}})
    }}
}
