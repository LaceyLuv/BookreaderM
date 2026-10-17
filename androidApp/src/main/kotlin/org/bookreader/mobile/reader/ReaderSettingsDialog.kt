package org.bookreader.mobile.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.bookreader.mobile.settings.*
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsDialog(controller: TxtReaderController, state: TxtReaderState, onDismiss: () -> Unit) {
    val loaded = state.settingsLoaded
    var global by remember(loaded) { mutableStateOf(loaded?.global ?: ReaderSettings()) }
    var preferences by remember(loaded) { mutableStateOf(loaded?.book ?: BookReaderPreferences()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("읽기 설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("reader_settings_dialog")) {
                if (state.settingsError) {
                    Text("설정을 읽거나 저장하지 못했습니다. 기존 설정은 보존됩니다.")
                    TextButton(onClick = controller::retrySettings) { Text("다시 읽기") }
                }
                Text("이 책의 읽기 방식")
                FlowRow {
                    TxtReadingMode.entries.forEach { mode ->
                        FilterChip(selected = (preferences.readingMode ?: global.readingMode) == mode,
                            onClick = { preferences = preferences.copy(readingMode = mode) },
                            label = { Text(if (mode == TxtReadingMode.PAGES) "페이지" else "스크롤") },
                            modifier = Modifier.testTag("reader_mode_${mode.name}"))
                    }
                }
                TextButton(onClick = { preferences = BookReaderPreferences() }) { Text("읽기 방식 기본값 상속") }
                Text("모든 책의 기본 설정")
                FlowRow {
                    ReaderFont.entries.forEach { font -> FilterChip(selected = global.font == font,
                        onClick = { global = global.copy(font = font) },
                        modifier = Modifier.testTag("reader_font_${font.name}"),
                        label = { Text(if (font == ReaderFont.SANS) "기본 고딕" else "기본 명조") }) }
                }
                SettingStepper("글자 크기", "${global.fontSizeSp.roundToInt()} sp", "font_size",
                    global.fontSizeSp > 12, global.fontSizeSp < 36,
                    { global = global.copy(fontSizeSp = (global.fontSizeSp - 1).coerceAtLeast(12f)) },
                    { global = global.copy(fontSizeSp = (global.fontSizeSp + 1).coerceAtMost(36f)) })
                SettingStepper("행간", "${(global.lineHeight * 100).roundToInt()}%", "line_height",
                    global.lineHeight > 1, global.lineHeight < 2,
                    { global = global.copy(lineHeight = (global.lineHeight - .1f).coerceAtLeast(1f)) },
                    { global = global.copy(lineHeight = (global.lineHeight + .1f).coerceAtMost(2f)) })
                SettingStepper("자간", "${(global.letterSpacingEm * 100).roundToInt()}%", "letter_spacing",
                    global.letterSpacingEm > -.02f, global.letterSpacingEm < .10f,
                    { global = global.copy(letterSpacingEm = (global.letterSpacingEm - .01f).coerceAtLeast(-.02f)) },
                    { global = global.copy(letterSpacingEm = (global.letterSpacingEm + .01f).coerceAtMost(.10f)) })
                SettingStepper("좌우 여백", "${global.marginDp} dp", "margin",
                    global.marginDp > 8, global.marginDp < 40,
                    { global = global.copy(marginDp = (global.marginDp - 4).coerceAtLeast(8)) },
                    { global = global.copy(marginDp = (global.marginDp + 4).coerceAtMost(40)) })
                ReaderTheme.entries.forEach { theme ->
                    FilterChip(selected = global.theme == theme, onClick = { global = global.copy(theme = theme) },
                        label = { Text(when (theme) { ReaderTheme.LIGHT -> "밝게"; ReaderTheme.SEPIA -> "세피아"
                            ReaderTheme.DARK -> "어둡게"; ReaderTheme.OLED -> "검정" }) })
                }
                Text("기본 읽기 방식")
                TxtReadingMode.entries.forEach { mode -> FilterChip(selected = global.readingMode == mode,
                    onClick = { global = global.copy(readingMode = mode) },
                    label = { Text(if (mode == TxtReadingMode.PAGES) "페이지" else "스크롤") }) }
                Text("Reader 창")
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
        },
        confirmButton = { TextButton(enabled = loaded != null && !state.settingsError && !state.settingsSaving &&
            state.phase == TxtReaderPhase.READY, modifier = Modifier.testTag("reader_settings_apply"),
            onClick = { controller.saveSettings(global, preferences); onDismiss() }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

@Composable
private fun SettingStepper(label: String, value: String, tag: String, canDecrease: Boolean, canIncrease: Boolean,
    decrease: () -> Unit, increase: () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label $value")
        FlowRow {
            TextButton(onClick = decrease, enabled = canDecrease, modifier = Modifier.testTag("${tag}_decrease")) { Text("$label 줄이기") }
            TextButton(onClick = increase, enabled = canIncrease, modifier = Modifier.testTag("${tag}_increase")) { Text("$label 늘리기") }
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
