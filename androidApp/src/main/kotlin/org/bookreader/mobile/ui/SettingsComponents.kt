package org.bookreader.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsBodyContent(
    state: AppUiState,
    onThemeSelected: (ThemeMode) -> Unit,
    onBackup: (() -> Unit)? = null,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_content"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "settings_theme") {
            SettingsSectionCard(title = "화면 테마") {
                Column(Modifier.selectableGroup()) {
                    val modes = ThemeMode.entries
                    modes.forEachIndexed { index, mode ->
                        ThemeOptionRow(
                            mode = mode,
                            selected = state.theme == mode,
                            onSelect = { onThemeSelected(mode) },
                        )
                        if (index < modes.lastIndex) {
                            SettingsHairlineDivider()
                        }
                    }
                }
            }
        }

        if (state.themeError) {
            item(key = "settings_theme_error") {
                ThemeErrorBanner()
            }
        }

        item(key = "settings_formats") {
            SettingsSectionCard(title = "지원 형식") {
                SettingsInfoBlock(
                    title = "읽기 지원",
                    body = "TXT · 암호화되지 않은 EPUB 2/3 · 정적 JPEG/PNG/WebP를 담은 ZIP/CBZ",
                )
                SettingsHairlineDivider()
                SettingsInfoBlock(
                    title = "미지원 및 참고",
                    body = "DRM · 고정 레이아웃 EPUB, RAR/CBR, 애니메이션 이미지는 지원하지 않습니다. " +
                        "큰 이미지는 해상도를 낮춰 표시할 수 있습니다.",
                )
            }
        }

        item(key = "settings_retention") {
            SettingsSectionCard(title = "데이터 보관 및 정보") {
                SettingsInfoBlock(
                    title = "원본 파일 보존",
                    body = "책은 앱 내부 관리 복사본을 사용하며 원본은 변경하지 않습니다.",
                )
                SettingsHairlineDivider()
                SettingsInfoBlock(
                    title = "삭제 및 백업 안내",
                    body = "앱을 삭제하거나 앱 데이터를 지우면 내부 복사본과 독서 기록이 삭제될 수 있습니다.",
                )
                if (onBackup != null) {
                    TextButton(
                        onClick = onBackup,
                        modifier = Modifier.padding(horizontal = 8.dp).testTag("settings_backup"),
                    ) { Text("독서 기록 백업·복원") }
                }
                SettingsHairlineDivider()
                OpenSourceNotices()
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val label = when (mode) {
        ThemeMode.SYSTEM -> "시스템 설정"
        ThemeMode.LIGHT -> "밝게"
        ThemeMode.DARK -> "어둡게"
    }
    val description = when (mode) {
        ThemeMode.SYSTEM -> "기기 화면 모드에 맞춰 자동 전환"
        ThemeMode.LIGHT -> "주간 독서에 편안한 밝은 종이 톤"
        ThemeMode.DARK -> "독서 도구창과 통일된 깊은 남색 톤"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .testTag("theme_${mode.name}")
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemePreviewSwatch(mode = mode)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
private fun ThemePreviewSwatch(mode: ThemeMode) {
    val (leftColor, rightColor) = when (mode) {
        ThemeMode.SYSTEM -> Color(0xFFF6F7F9) to Color(0xFF101720)
        ThemeMode.LIGHT -> Color(0xFFFFFFFF) to Color(0xFF16685B)
        ThemeMode.DARK -> Color(0xFF101720) to Color(0xFFA7E0D3)
    }
    val shape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .size(30.dp)
            .clearAndSetSemantics {},
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(leftColor),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(rightColor),
            )
        }
    }
}

@Composable
private fun ThemeErrorBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Text(
            text = "테마 설정을 읽거나 저장하지 못했습니다. 저장된 값은 자동으로 덮어쓰지 않습니다.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("theme_error"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun SettingsInfoBlock(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHairlineDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}
