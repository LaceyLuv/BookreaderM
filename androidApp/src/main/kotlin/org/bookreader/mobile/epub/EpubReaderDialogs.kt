package org.bookreader.mobile.epub

import android.content.Intent
import org.bookreader.mobile.R
import org.bookreader.mobile.reader.ReaderToolButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.bookreader.mobile.reader.ReaderControlTheme
import org.bookreader.mobile.reader.ReaderPositionSlider
import org.bookreader.mobile.reader.ReaderSeekPreview
import org.bookreader.mobile.reader.isDarkBackground
import org.bookreader.mobile.settings.ReaderTheme
import org.readium.r2.shared.publication.Link

@Composable
internal fun EpubMenuDialog(
    state: EpubReaderUi,
    currentFraction: Float,
    ready: Boolean,
    seek: ReaderSeekPreview,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    onAnnotations: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = state.settings.theme
    val displayFraction = seek.fraction ?: currentFraction
    val percent = (displayFraction * 100).toInt().coerceIn(0, 100)

    ReaderControlTheme(darkTheme = theme.isDarkBackground()) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "EPUB 빠른 이동 · 도구",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "독서 메뉴",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "${theme.epubThemeLabel()} 테마",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
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
                                    text = "읽기 위치",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${if (seek.fraction == null) "현재" else "이동 미리보기"} $percent%",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (seek.fraction != null) colors.primary else colors.onSurface,
                                )
                            }

                            ReaderPositionSlider(currentFraction, ready, seek, "epub_seek")

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "0%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = colors.onSurfaceVariant,
                                )
                                Text(
                                    text = if (seek.fraction != null) {
                                        "놓으면 이동 · 취소 시 복원"
                                    } else {
                                        "드래그하여 위치 미리보기"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                                Text(
                                    text = "100%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // 2x2 Quick tools grid preserving epub_toc / epub_search / epub_bookmarks / epub_settings
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EpubMenuToolTile(
                                label = "목차",
                                icon = EpubTocIcon,
                                enabled = ready,
                                tag = "epub_toc",
                                onClick = onToc,
                                modifier = Modifier.weight(1f),
                            )
                            EpubMenuToolTile(
                                label = "검색",
                                icon = EpubSearchIcon,
                                enabled = ready,
                                tag = "epub_search",
                                onClick = onSearch,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EpubMenuToolTile(
                                label = "북마크",
                                icon = EpubBookmarkIcon,
                                enabled = ready,
                                tag = "epub_bookmarks",
                                onClick = onBookmarks,
                                modifier = Modifier.weight(1f),
                            )
                            EpubMenuToolTile(
                                label = "설정",
                                icon = EpubSettingsIcon,
                                enabled = ready,
                                tag = "epub_settings",
                                onClick = onSettings,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        ReaderToolButton("형광펜", R.drawable.ic_reader_highlight,
                            "epub_annotations", ready, onAnnotations)
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("epub_menu_close"),
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
private fun EpubMenuToolTile(
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun EpubSearchDialog(
    controller: EpubReaderController,
    state: EpubReaderUi,
    onDismiss: () -> Unit,
) {
    var query by remember(controller) { mutableStateOf(state.query) }
    val theme = state.settings.theme

    ReaderControlTheme(darkTheme = theme.isDarkBackground()) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "대소문자 구분 · 최대 256자",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "본문 검색",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (state.results.isNotEmpty() || state.query.isNotEmpty()) {
                        Text(
                            text = "${state.results.size}건",
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { if (it.length <= 256) query = it },
                            label = { Text("찾을 문장 · 대소문자 구분") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(
                                            imageVector = EpubCloseIcon,
                                            contentDescription = "검색어 지우기",
                                            tint = colors.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("epub_search_query"),
                        )
                        Button(
                            onClick = { controller.search(query) },
                            enabled = query.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .testTag("epub_search_start"),
                        ) {
                            Text("찾기", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.searching) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color = colors.primary,
                            trackColor = colors.outlineVariant.copy(alpha = 0.5f),
                        )
                    }

                    if (state.searchError) {
                        EpubErrorBanner(
                            message = "검색 한도에 도달했거나 일부 본문을 읽지 못했습니다. 표시된 결과만 확인할 수 있습니다.",
                        )
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
                                .heightIn(max = 360.dp),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (state.results.isEmpty() && !state.searching) {
                                item(key = "epub_search_empty") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 22.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = if (state.query.isNotEmpty()) {
                                                "일치하는 본문 결과가 없습니다"
                                            } else {
                                                "찾을 문장을 입력하고 찾기를 눌러주세요"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.onSurface,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            text = "EPUB 본문 검색은 대소문자를 구분합니다.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                items(state.results) { result ->
                                    val highlightText = result.text.highlight.orEmpty()
                                    val afterText = result.text.after.orEmpty()
                                    val chapterTitle = result.title?.take(80)
                                    Surface(
                                        onClick = {
                                            controller.navigate(result)
                                            onDismiss()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("epub_search_result"),
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.surface,
                                        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f)),
                                        tonalElevation = 0.dp,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (!chapterTitle.isNullOrBlank()) {
                                                Text(
                                                    text = chapterTitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Text(
                                                text = buildAnnotatedString {
                                                    if (highlightText.isNotEmpty()) {
                                                        withStyle(
                                                            SpanStyle(
                                                                background = colors.primaryContainer,
                                                                color = colors.onPrimaryContainer,
                                                                fontWeight = FontWeight.SemiBold,
                                                            ),
                                                        ) {
                                                            append(highlightText)
                                                        }
                                                    }
                                                    if (highlightText.isNotEmpty() && afterText.isNotEmpty()) {
                                                        append(" · ")
                                                    }
                                                    append(afterText)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurface,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
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
internal fun EpubTocDialog(
    controller: EpubReaderController,
    theme: ReaderTheme,
    onDismiss: () -> Unit,
) {
    val links = remember(controller) { flattenToc(controller.publication?.tableOfContents.orEmpty()) }
    val entries = links.first
    val truncated = links.second

    ReaderControlTheme(darkTheme = theme.isDarkBackground()) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (entries.isNotEmpty()) {
                                "총 ${entries.size}개 항목 · ${theme.epubThemeLabel()} 테마"
                            } else {
                                "목차 항목 없음"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "목차",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Icon(
                        imageVector = EpubTocIcon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            text = {
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
                            .heightIn(max = 400.dp),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (entries.isEmpty()) {
                            item(key = "epub_toc_empty") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "표시할 목차가 없습니다",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface,
                                    )
                                    Text(
                                        text = "위치 이동 슬라이더나 본문 검색으로 이동할 수 있습니다.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        } else {
                            items(entries) { (link, depth) ->
                                val isTopChapter = depth == 0
                                val indentDp = (depth.coerceAtMost(6) * 14).dp
                                Surface(
                                    onClick = {
                                        controller.publication?.locatorFromLink(link)?.let { controller.navigate(it) }
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("epub_toc_item"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isTopChapter) colors.surface else Color.Transparent,
                                    border = if (isTopChapter) {
                                        BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.75f))
                                    } else {
                                        null
                                    },
                                    tonalElevation = 0.dp,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 12.dp + indentDp,
                                                end = 12.dp,
                                                top = 10.dp,
                                                bottom = 10.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(if (isTopChapter) 6.dp else 4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isTopChapter) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.65f),
                                                ),
                                        )
                                        Text(
                                            text = link.title?.take(200) ?: "이름 없는 장",
                                            style = if (isTopChapter) {
                                                MaterialTheme.typography.bodyMedium
                                            } else {
                                                MaterialTheme.typography.bodySmall
                                            },
                                            fontWeight = if (isTopChapter) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isTopChapter) colors.onSurface else colors.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        if (truncated) {
                            item(key = "epub_toc_truncated") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surface,
                                    border = BorderStroke(1.dp, colors.outlineVariant),
                                    tonalElevation = 0.dp,
                                ) {
                                    Text(
                                        text = "목차 표시 한도에 도달했습니다. 일부 항목은 본문 이동으로 확인할 수 있습니다.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
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
internal fun EpubBookmarksDialog(
    controller: EpubReaderController,
    state: EpubReaderUi,
    onDismiss: () -> Unit,
) {
    val theme = state.settings.theme

    ReaderControlTheme(darkTheme = theme.isDarkBackground()) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
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
                    Text(
                        text = "${theme.epubThemeLabel()} 테마",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
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
                            .testTag("epub_bookmark_add"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = EpubBookmarkIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("현재 위치 추가", fontWeight = FontWeight.Bold)
                    }

                    if (state.bookmarkError) {
                        EpubErrorBanner(
                            message = "북마크를 읽거나 변경하지 못했습니다. 기존 기록은 유지됩니다.",
                        )
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
                                item(key = "epub_bookmarks_empty") {
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
                                            text = "상단 버튼으로 현재 읽기 위치를 남겨둘 수 있습니다.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                items(state.bookmarks, key = { it.id }) { bookmark ->
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
                                                    .testTag("epub_bookmark_item"),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = EpubBookmarkIcon,
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
                    // Exact text "닫기" required by EpubReaderFlowTest:115
                    Text("닫기", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }
}

@Composable
internal fun EpubExternalLinkDialog(
    url: String,
    theme: ReaderTheme,
    onOpenFailed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ReaderControlTheme(darkTheme = theme.isDarkBackground()) {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
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
                        imageVector = EpubExternalLinkIcon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "외부 링크 열기",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "기기 기본 브라우저로 이동합니다",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = colors.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    Text(
                        text = "신뢰할 수 있는 주소인지 확인한 뒤 브라우저로 열어주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        } catch (_: android.content.ActivityNotFoundException) {
                            onOpenFailed()
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Text("브라우저로 열기", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("취소", color = colors.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun EpubErrorBanner(message: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.errorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.4f)),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private fun ReaderTheme.epubThemeLabel(): String = when (this) {
    ReaderTheme.LIGHT -> "밝게"
    ReaderTheme.SEPIA -> "세피아"
    ReaderTheme.DARK -> "어둡게"
    ReaderTheme.OLED -> "검정"
}

private fun flattenToc(links: List<Link>): Pair<List<Pair<Link, Int>>, Boolean> {
    val pending = ArrayDeque<Pair<Link, Int>>()
    links.asReversed().forEach { pending.addLast(it to 0) }
    val result = mutableListOf<Pair<Link, Int>>()
    var truncated = false
    while (pending.isNotEmpty() && result.size < 1000) {
        val entry = pending.removeLast()
        result += entry
        if (entry.second < 32) {
            entry.first.children.asReversed().forEach { pending.addLast(it to entry.second + 1) }
        } else if (entry.first.children.isNotEmpty()) {
            truncated = true
        }
    }
    return result to (truncated || pending.isNotEmpty())
}

private val EpubTocIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EpubToc",
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
            moveTo(4f, 6f)
            horizontalLineTo(20f)
            moveTo(8f, 12f)
            horizontalLineTo(20f)
            moveTo(8f, 18f)
            horizontalLineTo(20f)
            moveTo(4f, 12f)
            horizontalLineTo(4.01f)
            moveTo(4f, 18f)
            horizontalLineTo(4.01f)
        }
    }.build()
}

private val EpubSearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EpubSearch",
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
        }
    }.build()
}

private val EpubBookmarkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EpubBookmark",
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

private val EpubSettingsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EpubSettings",
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

private val EpubExternalLinkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EpubExternalLink",
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
            moveTo(15f, 4f)
            horizontalLineTo(20f)
            verticalLineTo(9f)
            moveTo(10f, 14f)
            lineTo(20f, 4f)
            moveTo(18f, 13f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            verticalLineTo(6f)
            horizontalLineTo(11f)
        }
    }.build()
}

private val EpubCloseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EpubClose",
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
