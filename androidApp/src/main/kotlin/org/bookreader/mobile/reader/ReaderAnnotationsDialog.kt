package org.bookreader.mobile.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bookreader.mobile.annotation.*

fun HighlightColor.paper(): Color = when (this) {
    HighlightColor.YELLOW -> Color(0xFFFFE596)
    HighlightColor.GREEN -> Color(0xFFAAE8BD)
    HighlightColor.BLUE -> Color(0xFFACD9FF)
    HighlightColor.PINK -> Color(0xFFFFBFD2)
}
fun HighlightColor.label(): String = when (this) {
    HighlightColor.YELLOW -> "노랑"; HighlightColor.GREEN -> "초록"; HighlightColor.BLUE -> "파랑"; HighlightColor.PINK -> "분홍"
}

/** Color-only spans preserve the existing text, font, line count and canonical page boundaries. */
fun highlightedTxt(base: Long, text: String, entries: List<TxtAnnotation>) = buildAnnotatedString {
    append(text)
    for (span in TxtAnnotations.spans(base, text, entries))
        addStyle(SpanStyle(background = span.color.paper(), color = Color(0xFF171717)), span.start, span.end)
}

@Composable
fun ReaderAnnotationsDialog(controller: TxtReaderController, onDismiss: () -> Unit) {
    val annotations = controller.annotations
    val state = annotations.state.collectAsStateWithLifecycle().value
    val reader = controller.state.collectAsStateWithLifecycle().value
    var editing by remember { mutableStateOf<TxtAnnotation?>(null) }
    var editVersion by remember { mutableLongStateOf(state.savedVersion) }
    LaunchedEffect(annotations) { annotations.load() }
    LaunchedEffect(state.savedVersion) { if (state.savedVersion > editVersion) editing = null }
    DisposableEffect(annotations) { onDispose { annotations.cancelSelection() } }
    val enabled = state.ready && !state.writing && reader.phase == TxtReaderPhase.READY
    AlertDialog(onDismissRequest = onDismiss, title = { Text("형광펜 · 메모") }, text = {
        Column {
            if (state.loading || state.selecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.readError || state.writeError) {
                Text("원문과 기록을 확인하지 못했습니다. 기존 기록은 보존되며, 확인 전에는 표시하거나 바꾸지 않습니다.",
                    modifier = Modifier.testTag("annotation_error"), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { annotations.load(force = true) }, modifier = Modifier.testTag("annotation_retry")) { Text("다시 읽기") }
            }
            if (state.selectionError) Text("현재 구간을 읽지 못했습니다. 다시 시도해 주세요.")
            TextButton(enabled = enabled && annotations.editable && !state.selecting && state.entries.size < TxtAnnotations.MAX_ENTRIES &&
                (reader.stableOffset ?: reader.targetOffset) < reader.cache.committedLength,
                onClick = annotations::beginSelection, modifier = Modifier.testTag("annotation_add")) { Text("현재 구간에서 문장 선택") }
            if (state.ready && state.entries.isEmpty()) Text("저장한 문장이 없습니다. 문장을 선택해 색상이나 메모를 남겨 보세요.")
            if (state.entries.size == TxtAnnotations.MAX_ENTRIES) Text("이 내용 버전의 저장 한도 500개에 도달했습니다.")
            LazyColumn(Modifier.heightIn(max = 340.dp).testTag("annotation_entries")) {
                items(state.entries, key = { it.id }) { entry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(entry.quote.text, Modifier.fillMaxWidth().background(entry.color.paper()).padding(8.dp),
                            color = Color(0xFF171717), maxLines = 3)
                        if (entry.note.isNotBlank()) Text(entry.note, maxLines = 3)
                        Row {
                            TextButton(enabled = enabled, onClick = { annotations.jump(entry); onDismiss() },
                                modifier = Modifier.testTag("annotation_jump_${entry.id}")) { Text("본문으로") }
                            TextButton(enabled = enabled, onClick = { editing = entry; editVersion = state.savedVersion },
                                modifier = Modifier.testTag("annotation_edit_${entry.id}")) { Text("수정") }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("annotations_close")) { Text("닫기") } })

    val window = state.window
    if (window != null || editing != null) key(window?.token, editing?.changeId) {
        val original = editing
        var value by remember { mutableStateOf(TextFieldValue(window?.text ?: original!!.quote.text)) }
        // Keep the confirmed range while focus moves to the note field or save button.
        var chosen by remember { mutableStateOf<TextRange?>(null) }
        var color by remember { mutableStateOf(original?.color ?: HighlightColor.YELLOW) }
        var note by remember { mutableStateOf(original?.note.orEmpty()) }
        val selected = window?.let { source -> chosen?.let { range ->
            TxtAnnotations.select(source.base, source.text, range.start, range.end, reader.cache.committedLength)
        } }
        fun cancel() { if (!state.writing) { editing = null; annotations.cancelSelection() } }
        AlertDialog(onDismissRequest = ::cancel, title = { Text(if (original == null) "문장 선택" else "형광펜 · 메모 수정") },
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(.94f), properties = DialogProperties(usePlatformDefaultWidth = false), text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (original == null) {
                        Text("아래 본문을 길게 눌러 선택하세요. 현재 위치부터 일부 구간을 표시합니다.")
                        BasicTextField(value, onValueChange = { changed ->
                            if (!state.writing && changed.text == window!!.text) {
                                value = changed.copy(composition = null)
                                if (!changed.selection.collapsed) chosen = changed.selection
                            }
                        }, readOnly = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp)
                                .verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)
                                .testTag("annotation_selection"))
                        Text(selected?.let { "선택한 문장: ${it.text}" } ?: "저장할 문장을 선택하세요", maxLines = 3,
                            modifier = Modifier.testTag("annotation_selection_preview"))
                        if (chosen != null) TextButton(enabled = !state.writing, onClick = {
                            chosen = null; value = value.copy(selection = TextRange.Zero)
                        }, modifier = Modifier.testTag("annotation_clear_selection")) { Text("선택 해제") }
                    } else Text(original.quote.text, maxLines = 5)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HighlightColor.entries.forEach { candidate ->
                            FilterChip(selected = color == candidate, enabled = !state.writing, onClick = { color = candidate },
                                modifier = Modifier.testTag("annotation_color_${candidate.name}"), label = { Text(candidate.label()) },
                                leadingIcon = { Surface(color = candidate.paper()) { Text("가", color = Color(0xFF171717)) } })
                        }
                    }
                    OutlinedTextField(note, { if (it.length <= TxtAnnotations.MAX_NOTE) note = it }, enabled = !state.writing,
                        label = { Text("메모 · 선택 사항") }, supportingText = { Text("${note.length} / ${TxtAnnotations.MAX_NOTE}") },
                        modifier = Modifier.testTag("annotation_note"))
                    if (state.writeError) Text("저장하지 못했습니다. 창을 닫고 기록을 다시 읽어 주세요.", color = MaterialTheme.colorScheme.error)
                    if (original != null) TextButton(enabled = enabled, onClick = { annotations.delete(original) },
                        modifier = Modifier.testTag("annotation_delete")) { Text("형광펜과 메모 삭제") }
                }
            }, confirmButton = { TextButton(enabled = enabled && TxtAnnotations.validNote(note) && (original != null || selected != null),
                onClick = {
                    editVersion = state.savedVersion
                    if (original != null) annotations.edit(original, color, note)
                    else chosen?.let { annotations.add(requireNotNull(window), it.start, it.end, color, note) }
                }, modifier = Modifier.testTag("annotation_save")) { Text("저장") } },
            dismissButton = { TextButton(enabled = !state.writing, onClick = ::cancel, modifier = Modifier.testTag("annotation_cancel")) { Text("취소") } })
    }
}
