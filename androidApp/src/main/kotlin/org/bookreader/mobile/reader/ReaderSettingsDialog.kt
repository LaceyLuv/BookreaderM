package org.bookreader.mobile.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.bookreader.mobile.settings.BookReaderPreferences
import org.bookreader.mobile.settings.ReaderFont
import org.bookreader.mobile.settings.ReaderSettings
import org.bookreader.mobile.settings.ReaderTheme
import org.bookreader.mobile.settings.TxtReadingMode
import kotlin.math.roundToInt

@Composable
fun ReaderSettingsDialog(
    controller: TxtReaderController,
    state: TxtReaderState,
    onDismiss: () -> Unit,
) = ReaderSettingsDialog(
    state = state,
    onRetry = controller::retrySettings,
    onSave = controller::saveSettings,
    onDismiss = onDismiss,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderSettingsDialog(
    state: TxtReaderState,
    onRetry: () -> Unit,
    onSave: (ReaderSettings, BookReaderPreferences) -> Unit,
    epubMode: Boolean = false,
    onDismiss: () -> Unit,
) {
    val loaded = state.settingsLoaded
    var global by remember(loaded) { mutableStateOf(loaded?.global ?: ReaderSettings()) }
    var preferences by remember(loaded) { mutableStateOf(loaded?.book ?: BookReaderPreferences()) }
    var fontBusy by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }

    val fontSizeLabel = if (epubMode) {
        "${(global.fontSizeSp / 18f * 100).roundToInt()}%"
    } else {
        "${global.fontSizeSp.roundToInt()} sp"
    }
    val lineHeightLabel = "${(global.lineHeight * 100).roundToInt()}%"
    val letterSpacingLabel = "${(global.letterSpacingEm * 100).roundToInt()}%"
    val marginLabel = if (epubMode) {
        "${(global.marginDp / 16f * 100).roundToInt()}%"
    } else {
        "${global.marginDp} dp"
    }
    val effectiveLetterSpacing = if (epubMode) {
        global.letterSpacingEm.coerceAtLeast(0f)
    } else {
        global.letterSpacingEm
    }

    ReaderControlTheme(darkTheme = global.theme.isDarkBackground()) {
        val chipColors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        val chipBorder = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        )

        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "읽기 설정",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "글자·색상·읽기 방식을 조절하세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = "${if (epubMode) "EPUB" else "TXT"} · ${readerThemeLabel(global.theme)}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(.94f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .testTag("reader_settings_dialog"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.settingsError) {
                        SettingsErrorBanner(onRetry = onRetry)
                    }

                    // Hero live preview synchronized with theme, font, weight, size, line height, spacing, and margin
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = global.theme.paperColor(),
                        contentColor = global.theme.inkColor(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "실시간 미리보기",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = global.theme.inkColor().copy(alpha = 0.62f),
                                )
                                Text(
                                    text = "$fontSizeLabel · $lineHeightLabel · $marginLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = global.theme.inkColor().copy(alpha = 0.68f),
                                )
                            }
                            HorizontalDivider(
                                color = global.theme.inkColor().copy(alpha = 0.12f),
                            )
                            Text(
                                text = "책 속으로, 한 걸음 더.\n편안한 속도로 읽어 보세요.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = global.marginDp.coerceIn(12, 32).dp,
                                        vertical = 14.dp,
                                    )
                                    .testTag("reader_settings_preview"),
                                fontSize = global.fontSizeSp.sp,
                                lineHeight = (global.fontSizeSp * global.lineHeight).sp,
                                letterSpacing = effectiveLetterSpacing.em,
                                fontFamily = global.composeFamily(),
                                fontWeight = FontWeight(global.fontWeight.value),
                            )
                        }
                    }

                    ReaderSettingsSection("본문 색상") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReaderTheme.entries.forEach { theme ->
                                val selected = global.theme == theme
                                FilterChip(
                                    selected = selected,
                                    onClick = { global = global.copy(theme = theme) },
                                    modifier = Modifier.testTag("reader_theme_${theme.name}"),
                                    colors = chipColors,
                                    border = if (selected) {
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = true,
                                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            selectedBorderWidth = 1.dp,
                                        )
                                    } else {
                                        chipBorder
                                    },
                                    leadingIcon = {
                                        Surface(
                                            color = theme.paperColor(),
                                            contentColor = theme.inkColor(),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                            ),
                                        ) {
                                            Text(
                                                text = "가",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    },
                                    label = { Text(readerThemeLabel(theme)) },
                                )
                            }
                        }
                    }

                    ReaderSettingsSection("이 책에만 · 읽기 방식") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TxtReadingMode.entries.forEach { mode ->
                                val selected = (preferences.readingMode ?: global.readingMode) == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { preferences = preferences.copy(readingMode = mode) },
                                    label = { Text(if (mode == TxtReadingMode.PAGES) "페이지" else "스크롤") },
                                    colors = chipColors,
                                    border = if (selected) {
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = true,
                                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            selectedBorderWidth = 1.dp,
                                        )
                                    } else {
                                        chipBorder
                                    },
                                    modifier = Modifier.testTag("reader_mode_${mode.name}"),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (preferences.readingMode == null) {
                                    "기본 읽기 방식 사용 중"
                                } else {
                                    "이 책에만 별도 적용됨"
                                },
                                modifier = Modifier.testTag("reader_mode_scope"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { preferences = preferences.copy(readingMode = null) },
                                modifier = Modifier.testTag("reader_mode_inherit"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("전체 기본값 따르기")
                            }
                        }
                    }

                    Text(
                        "아래 항목은 모든 책의 기본값입니다",
                        modifier = Modifier.testTag("reader_global_scope"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ReaderSettingsSection("글자와 간격 · 모든 책의 기본값") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReaderFont.entries.forEach { font ->
                                val selected = global.customFontId == null && global.font == font
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        global = global.copy(
                                            font = font,
                                            customFontId = null,
                                            epubPublisherStyles = if (epubMode) false else global.epubPublisherStyles,
                                        )
                                    },
                                    colors = chipColors,
                                    border = if (selected) {
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = true,
                                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                            selectedBorderWidth = 1.dp,
                                        )
                                    } else {
                                        chipBorder
                                    },
                                    modifier = Modifier.testTag("reader_font_${font.name}"),
                                    label = { Text(font.label, fontFamily = font.composeFamily()) },
                                )
                            }
                        }

                        if (global.customFontId != null) {
                            Text("내 글꼴 사용 중 · 추가 설정에서 관리", style = MaterialTheme.typography.bodySmall)
                        }
                        ReaderFontWeightSettings(global, epubMode) { global = it }
                        if (epubMode) Text(
                            if (global.epubPublisherStyles) {
                                "출판사 스타일 사용 중 · 글꼴·굵기·행간을 바꾸면 내 설정을 적용합니다."
                            } else "내 글자 설정 사용 중",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        SettingsControlGroup {
                            SettingStepper(
                                label = if (epubMode) "글자 배율" else "글자 크기",
                                value = fontSizeLabel,
                                tag = "font_size",
                                canDecrease = global.fontSizeSp > 12,
                                canIncrease = global.fontSizeSp < 36,
                                decrease = {
                                    global = global.copy(fontSizeSp = (global.fontSizeSp - 1).coerceAtLeast(12f))
                                },
                                increase = {
                                    global = global.copy(fontSizeSp = (global.fontSizeSp + 1).coerceAtMost(36f))
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            )
                            SettingStepper(
                                label = "행간",
                                value = lineHeightLabel,
                                tag = "line_height",
                                canDecrease = global.lineHeight > 1,
                                canIncrease = global.lineHeight < 2,
                                decrease = {
                                    global = global.copy(
                                        lineHeight = (global.lineHeight - .1f).coerceAtLeast(1f),
                                        epubPublisherStyles = if (epubMode) false else global.epubPublisherStyles,
                                    )
                                },
                                increase = {
                                    global = global.copy(
                                        lineHeight = (global.lineHeight + .1f).coerceAtMost(2f),
                                        epubPublisherStyles = if (epubMode) false else global.epubPublisherStyles,
                                    )
                                },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            )
                            SettingStepper(
                                label = if (epubMode) "여백 배율" else "좌우 여백",
                                value = marginLabel,
                                tag = "margin",
                                canDecrease = global.marginDp > 8,
                                canIncrease = global.marginDp < 40,
                                decrease = {
                                    global = global.copy(marginDp = (global.marginDp - 4).coerceAtLeast(8))
                                },
                                increase = {
                                    global = global.copy(marginDp = (global.marginDp + 4).coerceAtMost(40))
                                },
                            )
                        }


                    }

                    ReaderTransitionSettings(global.pageTransition) {
                        global = global.copy(pageTransition = it)
                    }

                    ReaderSettingsSection("화면 · 집중 모드") {
                        SettingsControlGroup {
                            WindowToggle(
                                label = "집중 모드 (상단·하단 시스템바 숨김)",
                                tag = "reader_fullscreen_option",
                                checked = global.window.fullscreen,
                            ) {
                                global = global.copy(window = global.window.copy(fullscreen = it))
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            )
                            SettingStepper(
                                label = "창 밝기",
                                value = global.window.brightness?.let { "${(it * 100).roundToInt()}%" } ?: "시스템 기본값",
                                tag = "window_brightness",
                                canDecrease = (global.window.brightness ?: .5f) > .05f,
                                canIncrease = (global.window.brightness ?: .5f) < 1f,
                                decrease = {
                                    global = global.copy(
                                        window = global.window.copy(
                                            brightness = ((global.window.brightness ?: .5f) - .1f).coerceAtLeast(.05f),
                                        ),
                                    )
                                },
                                increase = {
                                    global = global.copy(
                                        window = global.window.copy(
                                            brightness = ((global.window.brightness ?: .5f) + .1f).coerceAtMost(1f),
                                        ),
                                    )
                                },
                            )
                        }

                        TextButton(
                            onClick = {
                                global = global.copy(window = global.window.copy(brightness = null))
                            },
                            modifier = Modifier.testTag("window_brightness_system"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("시스템 밝기 사용")
                        }
                    }
                    TextButton(
                        onClick = { advanced = !advanced },
                        enabled = !fontBusy,
                        modifier = Modifier.fillMaxWidth().testTag("reader_settings_advanced"),
                    ) {
                        Text(if (advanced) "추가 설정 접기" else "추가 설정 · 내 글꼴·자간·화면 동작")
                    }
                    if (advanced) {
                        ReaderSettingsSection("내 글꼴 · 모든 책의 기본값") {
                            CustomFontSettings(
                                settings = global,
                                appliedFontId = loaded?.global?.customFontId,
                                epubMode = epubMode,
                                onBusy = { fontBusy = it },
                            ) { global = it }
                        }
                        ReaderSettingsSection("세부 글자 설정") {
                            SettingsControlGroup {
                                SettingStepper(
                                    label = "자간",
                                    value = letterSpacingLabel,
                                    tag = "letter_spacing",
                                    canDecrease = global.letterSpacingEm > (if (epubMode) 0f else -.02f),
                                    canIncrease = global.letterSpacingEm < .10f,
                                    decrease = {
                                        global = global.copy(
                                            letterSpacingEm = (global.letterSpacingEm - .01f)
                                                .coerceAtLeast(if (epubMode) 0f else -.02f),
                                            epubPublisherStyles = if (epubMode) false else global.epubPublisherStyles,
                                        )
                                    },
                                    increase = {
                                        global = global.copy(
                                            letterSpacingEm = (
                                                global.letterSpacingEm.coerceAtLeast(if (epubMode) 0f else -.02f) + .01f
                                                ).coerceAtMost(.10f),
                                            epubPublisherStyles = if (epubMode) false else global.epubPublisherStyles,
                                        )
                                    },
                                )
                            }
                            if (epubMode) {
                                SettingsControlGroup {
                                    WindowToggle(
                                        label = "출판사 스타일 유지",
                                        tag = "epub_publisher_styles",
                                        checked = global.epubPublisherStyles,
                                    ) {
                                        global = global.copy(epubPublisherStyles = it)
                                    }
                                }
                                if (global.epubPublisherStyles) {
                                    Text(
                                        text = "출판사 스타일을 끄면 글꼴·굵기·행간·자간 설정을 우선 적용합니다.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (epubMode && global.letterSpacingEm < 0) {
                                Text(
                                    text = "이 EPUB의 자간은 0으로 표시합니다. 저장된 TXT 기본값은 유지됩니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        ReaderSettingsSection("기본 동작") {
                            Text(
                                text = "기본 읽기 방식",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TxtReadingMode.entries.forEach { mode ->
                                    val selected = global.readingMode == mode
                                    FilterChip(
                                        selected = selected,
                                        onClick = { global = global.copy(readingMode = mode) },
                                        colors = chipColors,
                                        border = if (selected) {
                                            FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = true,
                                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                                selectedBorderWidth = 1.dp,
                                            )
                                        } else {
                                            chipBorder
                                        },
                                        label = { Text(if (mode == TxtReadingMode.PAGES) "페이지" else "스크롤") },
                                    )
                                }
                            }

                            Text(
                                text = "화면",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp),
                            )

                            SettingsControlGroup {
                                WindowToggle(
                                    label = "읽는 동안 화면 켜짐 유지",
                                    tag = "reader_keep_screen_on_option",
                                    checked = global.window.keepScreenOn,
                                ) {
                                    global = global.copy(window = global.window.copy(keepScreenOn = it))
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                )
                                WindowToggle(
                                    label = "현재 방향 유지 요청",
                                    tag = "reader_rotation_lock_option",
                                    checked = global.window.lockRotation,
                                ) {
                                    global = global.copy(window = global.window.copy(lockRotation = it))
                                }
                            }

                            Text(
                                text = "큰 화면에서는 기기 설정에 따라 회전 요청이 적용되지 않을 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )


                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = loaded != null &&
                        !state.settingsError &&
                        !state.settingsSaving &&
                        !fontBusy &&
                        state.phase == TxtReaderPhase.READY,
                    modifier = Modifier.testTag("reader_settings_apply"),
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        onSave(global, preferences)
                        onDismiss()
                    },
                ) {
                    Text("적용", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("reader_settings_close")) {
                    Text("취소")
                }
            },
        )
    }
}

private fun readerThemeLabel(theme: ReaderTheme): String = when (theme) {
    ReaderTheme.LIGHT -> "밝게"
    ReaderTheme.SEPIA -> "세피아"
    ReaderTheme.DARK -> "어둡게"
    ReaderTheme.OLED -> "검정"
}

@Composable
private fun SettingsErrorBanner(onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "설정을 읽거나 저장하지 못했습니다. 기존 설정은 보존됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "다시 읽기",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SettingsControlGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingStepper(
    label: String,
    value: String,
    tag: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    decrease: () -> Unit,
    increase: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FilledTonalButton(
                    onClick = decrease,
                    enabled = canDecrease,
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                        .testTag("${tag}_decrease")
                        .semantics { contentDescription = "$label 줄이기" },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("−", fontWeight = FontWeight.Bold)
                }
                Text(
                    text = value,
                    modifier = Modifier
                        .widthIn(min = 58.dp)
                        .padding(horizontal = 4.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                FilledTonalButton(
                    onClick = increase,
                    enabled = canIncrease,
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                        .testTag("${tag}_increase")
                        .semantics { contentDescription = "$label 늘리기" },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("+", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun WindowToggle(
    label: String,
    tag: String,
    checked: Boolean,
    change: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag)
            .toggleable(checked, role = Role.Checkbox, onValueChange = change)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
    }
}
