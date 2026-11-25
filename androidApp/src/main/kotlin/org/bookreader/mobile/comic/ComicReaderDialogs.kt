package org.bookreader.mobile.comic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bookreader.mobile.reader.ReaderControlTheme
import org.bookreader.mobile.reader.ReaderPositionSlider
import org.bookreader.mobile.reader.ReaderSeekPreview
import org.bookreader.mobile.reader.ReaderSettingsSection
import org.bookreader.mobile.reader.ReaderTransitionSettings
import org.bookreader.mobile.settings.BookReaderPreferences
import org.bookreader.mobile.settings.ComicDirection
import org.bookreader.mobile.settings.ComicFit
import org.bookreader.mobile.settings.ReaderSettings
import org.bookreader.mobile.settings.TxtReadingMode

@Composable
internal fun ComicMenuDialog(
    state: ComicReaderUi,
    ready: Boolean,
    seek: ReaderSeekPreview,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = state.manifest?.entryKeys?.size ?: 0
    val current = if (count > 1) state.index.toFloat() / (count - 1) else 0f
    val previewPage = ((seek.fraction ?: current) * (count - 1).coerceAtLeast(0)).toInt() + 1

    ReaderControlTheme(darkTheme = true) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "만화 페이지 이동 · 도구",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "독서 메뉴",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "페이지 이동",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${if (seek.fraction == null) "현재" else "이동 미리보기"} $previewPage / $count",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (seek.fraction != null) colors.primary else colors.onSurface,
                                )
                            }

                            ReaderPositionSlider(current, ready && count > 1, seek, "comic_seek")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "1쪽",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = colors.onSurfaceVariant,
                                )
                                Text(
                                    text = if (seek.fraction != null) {
                                        "놓으면 이동 · 취소 시 복원"
                                    } else {
                                        "드래그하여 페이지 미리보기"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                                Text(
                                    text = "${count.coerceAtLeast(1)}쪽",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ComicMenuToolTile(
                            label = "설정",
                            icon = ComicSettingsIcon,
                            enabled = ready,
                            tag = "comic_settings",
                            onClick = onSettings,
                            modifier = Modifier.weight(1f),
                        )
                        ComicMenuToolTile(
                            label = "북마크",
                            icon = ComicBookmarkIcon,
                            enabled = ready,
                            tag = "comic_bookmarks",
                            onClick = onBookmarks,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("comic_menu_close"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.surfaceContainerHighest,
                        contentColor = colors.onSurface,
                    ),
                ) {
                    Text("닫기 / 이동 취소", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
private fun ComicMenuToolTile(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.42f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.dp, colors.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary.copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface.copy(alpha = alpha),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComicSettingsDialog(
    controller: ComicReaderController,
    dismiss: () -> Unit,
) {
    val state = controller.state.collectAsStateWithLifecycle().value
    val loaded = controller.loadedSettings
    var global by remember(loaded) { mutableStateOf(loaded?.global ?: ReaderSettings()) }
    var book by remember(loaded) { mutableStateOf(loaded?.book ?: BookReaderPreferences()) }

    val effectiveMode = book.readingMode ?: global.readingMode
    val effectiveDirection = book.comicDirection ?: global.comicDirection
    val effectiveFit = book.comicFit ?: global.comicFit
    val webtoonActive =
        effectiveMode == TxtReadingMode.SCROLL &&
            effectiveFit == ComicFit.WIDTH &&
            global.comicGapDp == 0

    val summary = buildString {
        append(if (effectiveMode == TxtReadingMode.PAGES) "한 장 보기" else "연속 보기")
        append(" · ")
        append(if (effectiveDirection == ComicDirection.LTR) "좌→우" else "우→좌")
        append(" · ")
        append(
            when (effectiveFit) {
                ComicFit.WIDTH -> "너비"
                ComicFit.HEIGHT -> "높이"
                ComicFit.CONTAIN -> "전체"
            },
        )
        if (global.comicGapDp == 0) append(" · 간격 0")
    }

    ReaderControlTheme(darkTheme = true) {
        val colors = MaterialTheme.colorScheme
        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface,
            labelColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
        )
        val chipBorder = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = false,
            borderColor = colors.outlineVariant,
            selectedBorderColor = colors.primary,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.dp,
        )

        AlertDialog(
            onDismissRequest = dismiss,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = ComicSettingsIcon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "만화 설정",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            text = {
                // Single verticalScroll Column preserved for ComicReaderFlowTest performScrollTo() contract.
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.settingsError) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.errorContainer,
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.45f)),
                            tonalElevation = 0.dp,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "설정 오류입니다. 기존 값을 유지합니다.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = controller::retrySettings) {
                                    Text("다시 읽기", color = colors.onErrorContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Highlighted one-tap Webtoon preset card preserving comic_webtoon testTag
                    Surface(
                        onClick = {
                            book = book.copy(readingMode = TxtReadingMode.SCROLL, comicFit = ComicFit.WIDTH)
                            global = global.copy(comicGapDp = 0)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("comic_webtoon"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (webtoonActive) colors.primaryContainer else colors.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (webtoonActive) colors.primary else colors.outlineVariant,
                        ),
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = ComicSparkleIcon,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "웹툰 · 연속 / 너비 / 간격 0",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (webtoonActive) colors.onPrimaryContainer else colors.onSurface,
                                    )
                                    Text(
                                        text = "세로 스크롤 웹툰에 최적화된 프리셋을 한 번에 설정",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (webtoonActive) {
                                            colors.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            colors.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                            if (webtoonActive) {
                                Text(
                                    text = "적용됨",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary,
                                )
                            }
                        }
                    }

                    ReaderSettingsSection("읽기 방식") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TxtReadingMode.entries.forEach { mode ->
                                val selected = effectiveMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = { book = book.copy(readingMode = mode) },
                                    label = {
                                        Text(
                                            text = if (mode == TxtReadingMode.PAGES) "한 장 보기" else "연속 보기",
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                    colors = chipColors,
                                    border = if (selected) {
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = true,
                                            borderColor = colors.primary,
                                            selectedBorderColor = colors.primary,
                                        )
                                    } else {
                                        chipBorder
                                    },
                                    modifier = Modifier.testTag("comic_mode_${mode.name}"),
                                )
                            }
                        }
                    }

                    ReaderSettingsSection("넘기는 방향") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ComicDirection.entries.forEach { value ->
                                val selected = effectiveDirection == value
                                FilterChip(
                                    selected = selected,
                                    onClick = { book = book.copy(comicDirection = value) },
                                    label = {
                                        Text(
                                            text = if (value == ComicDirection.LTR) "왼쪽 → 오른쪽" else "오른쪽 → 왼쪽",
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                    colors = chipColors,
                                    border = if (selected) {
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = true,
                                            borderColor = colors.primary,
                                            selectedBorderColor = colors.primary,
                                        )
                                    } else {
                                        chipBorder
                                    },
                                    modifier = Modifier.testTag("comic_direction_$value"),
                                )
                            }
                        }
                    }

                    ReaderSettingsSection("이미지 맞춤") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ComicFit.entries.forEach { value ->
                                val selected = effectiveFit == value
                                FilterChip(
                                    selected = selected,
                                    onClick = { book = book.copy(comicFit = value) },
                                    label = {
                                        Text(
                                            text = when (value) {
                                                ComicFit.WIDTH -> "너비"
                                                ComicFit.HEIGHT -> "높이"
                                                ComicFit.CONTAIN -> "전체"
                                            },
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                    colors = chipColors,
                                    border = if (selected) {
                                        FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = true,
                                            borderColor = colors.primary,
                                            selectedBorderColor = colors.primary,
                                        )
                                    } else {
                                        chipBorder
                                    },
                                    modifier = Modifier.testTag("comic_fit_$value"),
                                )
                            }
                        }
                    }

                    ReaderTransitionSettings(global.pageTransition) {
                        global = global.copy(pageTransition = it)
                    }

                    OutlinedButton(
                        onClick = { book = book.copy(readingMode = null, comicFit = null, comicDirection = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.onSurfaceVariant,
                        ),
                    ) {
                        Text("기본값 상속", fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = loaded != null && !state.settingsError && !state.settingsSaving,
                    onClick = {
                        controller.saveSettings(global, book)
                        dismiss()
                    },
                    modifier = Modifier.testTag("comic_settings_apply"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Text("적용", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) {
                    Text("닫기", color = colors.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
internal fun ComicBookmarksDialog(
    controller: ComicReaderController,
    state: ComicReaderUi,
    onDismiss: () -> Unit,
) {
    ReaderControlTheme(darkTheme = true) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "저장된 위치 ${state.bookmarks.size}개",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = "북마크",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = controller::addBookmark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("comic_bookmark_add"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = ComicBookmarkIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("현재 위치 추가", fontWeight = FontWeight.Bold)
                    }

                    if (state.bookmarkError) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = colors.errorContainer,
                            border = BorderStroke(1.dp, colors.error.copy(alpha = 0.45f)),
                            tonalElevation = 0.dp,
                        ) {
                            Text(
                                text = "북마크 오류입니다. 기존 기록은 유지됩니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (state.bookmarks.isEmpty()) {
                                item(key = "comic_bookmarks_empty") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "저장된 북마크가 없습니다",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface,
                                        )
                                        Text(
                                            text = "상단 버튼으로 현재 페이지를 북마크할 수 있습니다.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(state.bookmarks, key = { _, it -> it.id }) { _, bookmark ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.surface,
                                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f)),
                                        tonalElevation = 0.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Surface(
                                                onClick = {
                                                    controller.goBookmark(bookmark)
                                                    onDismiss()
                                                },
                                                color = Color.Transparent,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("comic_bookmark_item"),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = ComicBookmarkIcon,
                                                        contentDescription = null,
                                                        tint = colors.primary,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                    Text(
                                                        text = bookmark.label ?: "저장한 위치",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium,
                                                        color = colors.onSurface,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { controller.deleteBookmark(bookmark.id) },
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, colors.error.copy(alpha = 0.45f)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = colors.errorContainer.copy(alpha = 0.35f),
                                                    contentColor = colors.error,
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.heightIn(min = 36.dp),
                                            ) {
                                                Text(
                                                    text = "삭제",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.bookmarkCursor != null) {
                            OutlinedButton(
                                onClick = { controller.loadBookmarks(true) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("다음 50개", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        TextButton(onClick = { controller.loadBookmarks() }) {
                            Text(
                                text = "처음 / 다시 읽기",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.surfaceContainerHighest,
                        contentColor = colors.onSurface,
                    ),
                ) {
                    Text("닫기", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
internal fun ComicZoomOverlayDialog(
    entryLabel: String,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ReaderControlTheme(darkTheme = true) {
            val colors = MaterialTheme.colorScheme
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("comic_zoom_dialog"),
                color = Color(0xFF0E141C),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF141D27),
                        tonalElevation = 0.dp,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = ComicZoomIcon,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "확대 보기",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "두 손가락으로 확대·이동 · $entryLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.testTag("comic_zoom_close"),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = colors.primaryContainer,
                                        contentColor = colors.onPrimaryContainer,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = ComicCloseIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("확대 보기 닫기", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        content = content,
                    )
                }
            }
        }
    }
}

private val ComicSettingsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ComicSettings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 7f)
            horizontalLineTo(10f)
            moveTo(14f, 7f)
            horizontalLineTo(20f)
            moveTo(12f, 5f)
            verticalLineTo(9f)
            moveTo(4f, 17f)
            horizontalLineTo(14f)
            moveTo(18f, 17f)
            horizontalLineTo(20f)
            moveTo(16f, 15f)
            verticalLineTo(19f)
        }
    }.build()
}

private val ComicBookmarkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ComicBookmark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 4f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            lineTo(12f, 16f)
            lineTo(6f, 20f)
            close()
        }
    }.build()
}

private val ComicSparkleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ComicSparkle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            lineTo(14.2f, 9.8f)
            lineTo(21f, 12f)
            lineTo(14.2f, 14.2f)
            lineTo(12f, 21f)
            lineTo(9.8f, 14.2f)
            lineTo(3f, 12f)
            lineTo(9.8f, 9.8f)
            close()
        }
    }.build()
}

private val ComicZoomIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ComicZoom",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(11f, 4f)
            curveTo(14.87f, 4f, 18f, 7.13f, 18f, 11f)
            curveTo(18f, 14.87f, 14.87f, 18f, 11f, 18f)
            curveTo(7.13f, 18f, 4f, 14.87f, 4f, 11f)
            curveTo(4f, 7.13f, 7.13f, 4f, 11f, 4f)
            close()
            moveTo(20f, 20f)
            lineTo(16.2f, 16.2f)
            moveTo(11f, 8f)
            verticalLineTo(14f)
            moveTo(8f, 11f)
            horizontalLineTo(14f)
        }
    }.build()
}

private val ComicCloseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ComicClose",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }.build()
}
