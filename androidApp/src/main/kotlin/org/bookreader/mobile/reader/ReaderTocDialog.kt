package org.bookreader.mobile.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bookreader.mobile.toc.TxtToc

@Composable
fun ReaderTocDialog(controller: TxtReaderController, onDismiss: () -> Unit) {
    val toc = controller.toc
    val state = toc.state.collectAsStateWithLifecycle().value
    val reader = controller.state.collectAsStateWithLifecycle().value
    val offset = reader.stableOffset ?: reader.targetOffset
    val current = remember(state.entries, offset) { state.entries.lastOrNull { it.offset <= offset }?.offset }
    var editing by remember { mutableStateOf<Long?>(null) }
    var title by remember { mutableStateOf("") }
    var editVersion by remember { mutableLongStateOf(0) }
    var showExcluded by remember { mutableStateOf(false) }
    LaunchedEffect(toc) { toc.load() }
    DisposableEffect(toc) { onDispose { toc.cancelLoad() } }
    LaunchedEffect(state.savedVersion) { if (state.savedVersion > editVersion) editing = null }
    val enabled = state.ready && !state.writing && reader.phase == TxtReaderPhase.READY
    fun edit(at: Long, value: String) { editing = at; title = value; editVersion = state.savedVersion }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("목차") }, text = {
        Column(Modifier.fillMaxWidth()) {
            if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("회차를 찾는 중 · 닫아도 계속 읽을 수 있어요") }
            if (state.readError) {
                Text("목차를 읽지 못했습니다. 기존 목차와 독서 기록은 보존됩니다.", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = toc::load, modifier = Modifier.testTag("toc_retry")) { Text("다시 읽기") }
            }
            if (state.writeError) {
                Text("목차를 저장하지 못했습니다. 다시 읽은 뒤 시도해 주세요.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("toc_write_error"))
                TextButton(onClick = toc::load) { Text("다시 읽기") }
            }
            if (state.truncated) Text("자동 목차는 처음 5,000개까지 표시합니다.")
            if (state.ready && state.entries.isEmpty()) Text("감지한 회차가 없습니다. 현재 위치에 목차를 추가할 수 있어요.")
            if (toc.editable) TextButton(enabled = enabled && offset < reader.cache.committedLength,
                onClick = { edit(offset, state.entries.firstOrNull { it.offset == offset }?.title ?: "") },
                modifier = Modifier.testTag("toc_add_current")) { Text("현재 위치에 추가") }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).testTag("toc_entries")) {
                items(state.entries, key = { it.offset }) { entry ->
                    Row(Modifier.fillMaxWidth().semantics { selected = entry.offset == current }) {
                        TextButton(enabled = enabled, onClick = { toc.jump(entry.offset); onDismiss() },
                            modifier = Modifier.weight(1f).testTag("toc_jump_${entry.offset}")) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(entry.title)
                                if (entry.offset == current) Text("읽는 회차", style = MaterialTheme.typography.labelSmall)
                                else if (!entry.automatic) Text("직접 추가", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (toc.editable) TextButton(enabled = enabled, onClick = { edit(entry.offset, entry.title) },
                            modifier = Modifier.testTag("toc_edit_${entry.offset}")) { Text("수정") }
                    }
                }
            }
            if (state.edits.any { it.excluded }) {
                TextButton(onClick = { showExcluded = !showExcluded }, modifier = Modifier.testTag("toc_excluded")) { Text(if (showExcluded) "제외한 항목 닫기" else "제외한 항목 보기") }
                if (showExcluded) LazyColumn(Modifier.heightIn(max = 120.dp)) {
                    items(state.edits.filter { it.excluded }, key = { it.offset }) { entry ->
                        TextButton(enabled = enabled, onClick = { toc.clearEdit(entry.offset) }, modifier = Modifier.testTag("toc_restore_${entry.offset}")) {
                            Text("복구 · ${state.headings.firstOrNull { it.offset == entry.offset }?.title ?: "이전 목차"}")
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("toc_close")) { Text("닫기") } })
    editing?.let { at ->
        val automatic = state.headings.any { it.offset == at }
        val hasEdit = state.edits.any { it.offset == at }
        AlertDialog(onDismissRequest = { if (!state.writing) editing = null }, title = { Text(if (automatic || hasEdit) "목차 수정" else "목차 추가") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { if (it.length <= 120) title = it }, label = { Text("회차 제목") },
                    singleLine = true, enabled = !state.writing, modifier = Modifier.testTag("toc_title"))
                if (automatic) TextButton(enabled = enabled, onClick = { toc.exclude(at) }, modifier = Modifier.testTag("toc_exclude")) { Text("목차에서 제외") }
                if (hasEdit) TextButton(enabled = enabled, onClick = { toc.clearEdit(at) }, modifier = Modifier.testTag("toc_clear_edit")) { Text(if (automatic) "자동 제목으로 되돌리기" else "직접 추가한 목차 삭제") }
                if (state.writeError) Text("저장하지 못했습니다. 창을 닫고 목차를 다시 읽어 주세요.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(enabled = enabled && TxtToc.validTitle(title.trim()), onClick = { toc.renameOrAdd(at, title) }, modifier = Modifier.testTag("toc_save")) { Text("저장") } },
            dismissButton = { TextButton(enabled = !state.writing, onClick = { editing = null }) { Text("취소") } })
    }
}
