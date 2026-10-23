package org.bookreader.mobile.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.bookreader.mobile.settings.*
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsDialog(controller: TxtReaderController, state: TxtReaderState, onDismiss: () -> Unit) =
    ReaderSettingsDialog(state, controller::retrySettings, controller::saveSettings, onDismiss = onDismiss)

@Composable
fun ReaderSettingsDialog(state: TxtReaderState, onRetry: () -> Unit,
    onSave: (ReaderSettings, BookReaderPreferences) -> Unit, epubMode: Boolean = false, onDismiss: () -> Unit) {
    val loaded = state.settingsLoaded
    var global by remember(loaded) { mutableStateOf(loaded?.global ?: ReaderSettings()) }
    var preferences by remember(loaded) { mutableStateOf(loaded?.book ?: BookReaderPreferences()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Column {
        Text("읽기 설정")
        Text("책은 편안하게, 도구는 분명하게", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("reader_settings_dialog")) {
                if (state.settingsError) {
                    Text("설정을 읽거나 저장하지 못했습니다. 기존 설정은 보존됩니다.")
                    TextButton(onClick = onRetry) { Text("다시 읽기") }
                }
                ReaderSettingsSection("이 책의 읽기 방식") {
                FlowRow {
                    TxtReadingMode.entries.forEach { mode ->
                        FilterChip(selected = (preferences.readingMode ?: global.readingMode) == mode,
                            onClick = { preferences = preferences.copy(readingMode = mode) },
                            label = { Text(if (mode == TxtReadingMode.PAGES) "페이지" else "스크롤") },
                            modifier = Modifier.testTag("reader_mode_${mode.name}"))
                    }
                }
                TextButton(onClick = { preferences = BookReaderPreferences() }) { Text("읽기 방식 기본값 상속") }
                }
                ReaderSettingsSection("글자와 간격 · 모든 책의 기본값") {
                Surface(color = global.theme.paperColor(), contentColor = global.theme.inkColor(), shape = RoundedCornerShape(12.dp)) {
                    Text("책 속으로, 한 걸음 더.\n편안한 속도로 읽어 보세요.", Modifier.fillMaxWidth().padding(16.dp).testTag("reader_settings_preview"),
                        fontSize = global.fontSizeSp.sp, lineHeight = (global.fontSizeSp * global.lineHeight).sp,
                        fontFamily = if (global.font == ReaderFont.SERIF) FontFamily.Serif else FontFamily.SansSerif)
                }
                FlowRow {
                    ReaderFont.entries.forEach { font -> FilterChip(selected = global.font == font,
                        onClick = { global = global.copy(font = font) },
                        modifier = Modifier.testTag("reader_font_${font.name}"),
                        label = { Text(if (font == ReaderFont.SANS) "기본 고딕" else "기본 명조") }) }
                }
                SettingStepper(if (epubMode) "글자 배율" else "글자 크기", if (epubMode) "${(global.fontSizeSp / 18f * 100).roundToInt()}%" else "${global.fontSizeSp.roundToInt()} sp", "font_size",
                    global.fontSizeSp > 12, global.fontSizeSp < 36,
                    { global = global.copy(fontSizeSp = (global.fontSizeSp - 1).coerceAtLeast(12f)) },
                    { global = global.copy(fontSizeSp = (global.fontSizeSp + 1).coerceAtMost(36f)) })
                SettingStepper("행간", "${(global.lineHeight * 100).roundToInt()}%", "line_height",
                    global.lineHeight > 1, global.lineHeight < 2,
                    { global = global.copy(lineHeight = (global.lineHeight - .1f).coerceAtLeast(1f)) },
                    { global = global.copy(lineHeight = (global.lineHeight + .1f).coerceAtMost(2f)) })
                SettingStepper("자간", "${(global.letterSpacingEm * 100).roundToInt()}%", "letter_spacing",
                    global.letterSpacingEm > (if (epubMode) 0f else -.02f), global.letterSpacingEm < .10f,
                    { global = global.copy(letterSpacingEm = (global.letterSpacingEm - .01f).coerceAtLeast(if (epubMode) 0f else -.02f)) },
                    { global = global.copy(letterSpacingEm = (global.letterSpacingEm.coerceAtLeast(if (epubMode) 0f else -.02f) + .01f).coerceAtMost(.10f)) })
                if (epubMode) {
                    WindowToggle("출판사 스타일 유지", "epub_publisher_styles", global.epubPublisherStyles) {
                        global = global.copy(epubPublisherStyles = it)
                    }
                    if (global.epubPublisherStyles) Text("출판사 스타일을 끄면 글꼴·행간·자간 설정을 우선 적용합니다.")
                }
                if (epubMode && global.letterSpacingEm < 0) Text("이 EPUB의 자간은 0으로 표시합니다. 저장된 TXT 기본값은 유지됩니다.")
                SettingStepper(if (epubMode) "여백 배율" else "좌우 여백", if (epubMode) "${(global.marginDp / 16f * 100).roundToInt()}%" else "${global.marginDp} dp", "margin",
                    global.marginDp > 8, global.marginDp < 40,
                    { global = global.copy(marginDp = (global.marginDp - 4).coerceAtLeast(8)) },
                    { global = global.copy(marginDp = (global.marginDp + 4).coerceAtMost(40)) })
                }
                ReaderSettingsSection("본문 색상") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTheme.entries.forEach { theme ->
                            val label = when (theme) { ReaderTheme.LIGHT -> "밝게"; ReaderTheme.SEPIA -> "세피아"; ReaderTheme.DARK -> "어둡게"; ReaderTheme.OLED -> "검정" }
                            FilterChip(selected = global.theme == theme, onClick = { global = global.copy(theme = theme) },
                                modifier = Modifier.testTag("reader_theme_${theme.name}"),
                                leadingIcon = { Surface(color = theme.paperColor(), contentColor = theme.inkColor(), shape = RoundedCornerShape(4.dp)) {
                                    Text("가", Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.labelMedium)
                                } }, label = { Text(label) })
                        }
                    }
                }
                ReaderSettingsSection("기본 동작") {
                Text("기본 읽기 방식")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TxtReadingMode.entries.forEach { mode -> FilterChip(selected = global.readingMode == mode,
                    onClick = { global = global.copy(readingMode = mode) },
                    label = { Text(if (mode == TxtReadingMode.PAGES) "페이지" else "스크롤") }) } }
                Text("화면", style = MaterialTheme.typography.titleSmall)
                WindowToggle("전체화면", "reader_fullscreen_option", global.window.fullscreen) {
                    global = global.copy(window = global.window.copy(fullscreen = it)) }
                WindowToggle("읽는 동안 화면 켜짐 유지", "reader_keep_screen_on_option", global.window.keepScreenOn) {
                    global = global.copy(window = global.window.copy(keepScreenOn = it)) }
                WindowToggle("현재 방향 유지 요청", "reader_rotation_lock_option", global.window.lockRotation) {
                    global = global.copy(window = global.window.copy(lockRotation = it)) }
                Text("큰 화면에서는 기기 설정에 따라 회전 요청이 적용되지 않을 수 있습니다.")
                SettingStepper("창 밝기", global.window.brightness?.let { "${(it * 100).roundToInt()}%" } ?: "시스템 기본값",
                    "window_brightness", (global.window.brightness ?: .5f) > .05f, (global.window.brightness ?: .5f) < 1f,
                    { global = global.copy(window = global.window.copy(brightness = ((global.window.brightness ?: .5f) - .1f).coerceAtLeast(.05f))) },
                    { global = global.copy(window = global.window.copy(brightness = ((global.window.brightness ?: .5f) + .1f).coerceAtMost(1f))) })
                TextButton(onClick = { global = global.copy(window = global.window.copy(brightness = null)) }) { Text("시스템 밝기 사용") }
                }
            }
        },
        confirmButton = { Button(enabled = loaded != null && !state.settingsError && !state.settingsSaving &&
            state.phase == TxtReaderPhase.READY, modifier = Modifier.testTag("reader_settings_apply"),
            onClick = { onSave(global, preferences); onDismiss() }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

@Composable
private fun SettingStepper(label: String, value: String, tag: String, canDecrease: Boolean, canIncrease: Boolean,
    decrease: () -> Unit, increase: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = decrease, enabled = canDecrease,
                modifier = Modifier.testTag("${tag}_decrease").semantics { contentDescription = "$label 줄이기" },
                contentPadding = PaddingValues(8.dp)) { Text("−") }
            Text(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalButton(onClick = increase, enabled = canIncrease,
                modifier = Modifier.testTag("${tag}_increase").semantics { contentDescription = "$label 늘리기" },
                contentPadding = PaddingValues(8.dp)) { Text("+") }
        }
    }
}

@Composable
private fun WindowToggle(label: String, tag: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Text(label)
    }
}
