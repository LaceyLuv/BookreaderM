package org.bookreader.mobile.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.*
import org.bookreader.mobile.settings.ReaderFontWeight
import org.bookreader.mobile.settings.ReaderSettings

@Composable
fun CustomFontSettings(settings: ReaderSettings, appliedFontId: String?, epubMode: Boolean,
    onBusy: (Boolean) -> Unit, onChange: (ReaderSettings) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { UserFontStore(context) }
    val scope = rememberCoroutineScope()
    var fonts by remember { mutableStateOf<List<UserFont>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<UserFont?>(null) }
    val current by rememberUpdatedState(settings)
    LaunchedEffect(store) { fonts = withContext(Dispatchers.IO) { store.list() } }
    fun select(id: String?) {
        onChange(current.copy(customFontId = id, epubPublisherStyles = if (epubMode) false else current.epubPublisherStyles))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true; onBusy(true); message = null
            try {
                val imported = store.import(uri)
                fonts = withContext(Dispatchers.IO) { store.list() }
                select(imported.id)
                message = "${imported.label} 등록 완료. 적용을 누르면 본문에 반영됩니다."
            } catch (_: TimeoutCancellationException) { message = "파일을 읽는 시간이 초과됐습니다. 기기에 저장된 글꼴을 선택하세요." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = if (failure is IllegalArgumentException || failure is IllegalStateException)
                failure.message else "글꼴을 가져오지 못했습니다. 파일과 저장 공간을 확인하세요." }
            finally { busy = false; onBusy(false) }
        }
    }
    Text("내 글꼴")
    Text("TTF·OTF 파일 · 파일당 20MB 이하", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(enabled = !busy, modifier = Modifier.testTag("reader_font_import"), onClick = {
        try { picker.launch(arrayOf("*/*")) }
        catch (_: Exception) { message = "파일 선택기를 열 수 없습니다." }
    }) { Text(if (busy) "글꼴 가져오는 중…" else "글꼴 파일 추가") }
    fonts.forEach { font ->
        Row(Modifier.fillMaxWidth()) {
            FilterChip(selected = settings.customFontId == font.id, enabled = !busy,
                onClick = { select(font.id) }, label = { Text(font.label) },
                modifier = Modifier.weight(1f).testTag("reader_custom_font_${font.id}"))
            TextButton(enabled = !busy && font.id != appliedFontId, onClick = { remove = font },
                modifier = Modifier.testTag("reader_font_remove_${font.id}")) { Text("삭제") }
        }
    }
    if (appliedFontId != null) Text("사용 중인 글꼴은 다른 글꼴을 적용한 뒤 삭제할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
    if (settings.customFontId != null && fonts.none { it.id == settings.customFontId })
        Text("등록된 글꼴을 찾을 수 없어 기본 글꼴로 표시합니다.")
    message?.let { Text(it, Modifier.testTag("reader_font_message")) }
    Text("글자 굵기")
    FlowRow {
        ReaderFontWeight.entries.forEach { weight ->
            FilterChip(selected = settings.fontWeight == weight,
                modifier = Modifier.testTag("reader_weight_${weight.name}"),
                onClick = { onChange(settings.copy(fontWeight = weight,
                    epubPublisherStyles = if (epubMode) false else settings.epubPublisherStyles)) },
                label = { Text(if (weight == ReaderFontWeight.NORMAL) "보통" else "굵게", fontWeight = FontWeight(weight.value)) })
        }
    }
    remove?.let { font -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("글꼴 삭제") },
        text = { Text("${font.label}을 앱에서 삭제합니다. 원본 파일은 유지됩니다.") },
        confirmButton = { TextButton(modifier = Modifier.testTag("reader_font_confirm_remove"), onClick = {
            remove = null
            scope.launch {
                busy = true; onBusy(true)
                try {
                    store.remove(font.id)
                    if (current.customFontId == font.id) select(null)
                    fonts = withContext(Dispatchers.IO) { store.list() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "글꼴을 삭제하지 못했습니다." }
                finally { busy = false; onBusy(false) }
            }
        }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { remove = null }) { Text("취소") } }) }
}
