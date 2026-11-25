package org.bookreader.mobile.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bookreader.mobile.locator.TxtLocator

@Composable
fun ReaderSearchDialog(controller: TxtReaderController, onDismiss: () -> Unit) {
    val state = controller.tools.state.collectAsStateWithLifecycle().value
    val reader = controller.state.collectAsStateWithLifecycle().value
    // Text can arrive while a rotation/font/viewport change is restoring its anchor.
    // Keep the latest query and start it once the readable content is ready again.
    LaunchedEffect(reader.phase, reader.cache.complete, state.query, state.ignoreAsciiCase) {
        if (
            reader.phase == TxtReaderPhase.READY &&
            reader.cache.complete &&
            state.query.isNotEmpty() &&
            state.nextSearchOffset == 0L &&
            !state.searching &&
            !state.searchComplete &&
            !state.searchError
        ) {
            controller.tools.moreSearch()
        }
    }

    val theme = reader.settings.theme
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
                            text = "현재 책 본문 검색",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "책 안에서 찾기",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "${theme.readerThemeLabel()} 테마",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { controller.tools.search(it.take(257), state.ignoreAsciiCase) },
                        label = { Text("검색어 · 최대 256자") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = ReaderToolsSearchIcon,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        trailingIcon = if (state.query.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { controller.tools.search("", state.ignoreAsciiCase) },
                                ) {
                                    Icon(
                                        imageVector = ReaderToolsClearIcon,
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
                            .fillMaxWidth()
                            .testTag("reader_search_query"),
                    )

                    // Case-insensitive option row
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .toggleable(
                                    value = state.ignoreAsciiCase,
                                    role = Role.Checkbox,
                                    onValueChange = { controller.tools.search(state.query, it) },
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "영문 대소문자 구분 안 함",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(
                                checked = state.ignoreAsciiCase,
                                onCheckedChange = null,
                            )
                        }
                    }

                    if (state.searching) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 2.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary,
                            )
                            Text(
                                text = "본문에서 찾는 중",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.searchError) {
                        ReaderToolsErrorBanner(
                            message = "검색하지 못했습니다. 검색어 길이를 확인하고 다시 시도해 주세요.",
                        )
                    }

                    // Summary count header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${state.hits.size}개${if (state.searchComplete) " · 검색 완료" else ""}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant,
                        )
                        if (state.searchCapped) {
                            Text(
                                text = "상위 1,000개 제한",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    if (state.searchComplete && state.hits.isEmpty() && !state.searchError) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceContainerHigh,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            tonalElevation = 0.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "검색 결과가 없습니다",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text = "다른 단어를 입력하거나 대소문자 옵션을 변경해 보세요.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Always keep reader_search_results LazyColumn mounted for androidTest stability
                    val highlightBg = colors.primaryContainer
                    val highlightInk = colors.onPrimaryContainer
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.hits.isNotEmpty()) colors.surface else Color.Transparent,
                        border = if (state.hits.isNotEmpty()) {
                            BorderStroke(1.dp, colors.outlineVariant)
                        } else {
                            null
                        },
                        tonalElevation = 0.dp,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .testTag("reader_search_results"),
                        ) {
                            itemsIndexed(state.hits, key = { _, hit -> hit.offset }) { index, hit ->
                                val highlighted = remember(
                                    hit.snippet,
                                    state.query,
                                    state.ignoreAsciiCase,
                                    highlightBg,
                                    highlightInk,
                                ) {
                                    buildHighlightedSnippet(
                                        snippet = hit.snippet,
                                        query = state.query,
                                        ignoreAsciiCase = state.ignoreAsciiCase,
                                        highlightBg = highlightBg,
                                        highlightInk = highlightInk,
                                    )
                                }
                                Surface(
                                    onClick = {
                                        controller.tools.jumpFromSearch(hit.offset)
                                        onDismiss()
                                    },
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reader_search_hit_${hit.offset}"),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = highlighted,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.onSurface,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (index < state.hits.lastIndex) {
                                    HorizontalDivider(
                                        color = colors.outlineVariant.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        }
                    }

                    if (state.searchCapped) {
                        Text(
                            text = "처음 1,000개를 표시했습니다. 검색어를 구체적으로 입력해 주세요.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    } else if (state.nextSearchOffset != null && !state.searching && state.query.isNotEmpty()) {
                        OutlinedButton(
                            onClick = controller.tools::moreSearch,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.outlineVariant),
                        ) {
                            Text("계속 찾기", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("reader_search_close"),
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
fun ReaderBookmarksDialog(controller: TxtReaderController, onDismiss: () -> Unit) {
    val state = controller.tools.state.collectAsStateWithLifecycle().value
    val reader = controller.state.collectAsStateWithLifecycle().value
    var label by remember { mutableStateOf("") }
    LaunchedEffect(controller) { controller.tools.loadBookmarks() }

    val theme = reader.settings.theme
    val canMutate = !state.bookmarkWriting && state.bookmarksReadReady

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
                            text = "저장된 읽기 위치",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "북마크",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = "${theme.readerThemeLabel()} 테마",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Add current position card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it.take(200) },
                                label = { Text("이름 (선택)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reader_bookmark_label"),
                            )
                            Button(
                                enabled = canMutate,
                                onClick = { controller.tools.addBookmark(label) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp)
                                    .testTag("reader_bookmark_add"),
                            ) {
                                Icon(
                                    imageVector = ReaderToolsBookmarkIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("현재 위치 저장", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (state.bookmarksLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 2.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary,
                            )
                            Text(
                                text = "북마크를 읽는 중",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.bookmarkReadError) {
                        ReaderToolsErrorBanner(
                            message = "북마크를 읽지 못했습니다. 기존 북마크는 보존됩니다.",
                            actionLabel = "다시 읽기",
                            onAction = { controller.tools.loadBookmarks() },
                        )
                    }

                    if (state.bookmarkWriteError) {
                        ReaderToolsErrorBanner(
                            message = "변경을 저장하지 못했습니다. 다시 시도해 주세요.",
                        )
                    }

                    Text(
                        text = "저장된 북마크 ${state.bookmarks.size}개",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )

                    if (!state.bookmarksLoading && !state.bookmarkReadError && state.bookmarks.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceContainerHigh,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            tonalElevation = 0.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp),
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
                                    text = "위 버튼으로 현재 읽기 위치를 기록해 두세요.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Always keep reader_bookmarks_list LazyColumn mounted for androidTest stability
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (state.bookmarks.isNotEmpty()) colors.surface else Color.Transparent,
                        border = if (state.bookmarks.isNotEmpty()) {
                            BorderStroke(1.dp, colors.outlineVariant)
                        } else {
                            null
                        },
                        tonalElevation = 0.dp,
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .testTag("reader_bookmarks_list"),
                        ) {
                            itemsIndexed(state.bookmarks, key = { _, bm -> bm.id }) { index, bookmark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        onClick = {
                                            (bookmark.locator as? TxtLocator)?.let {
                                                controller.navigateTo(it.payload.utf16Offset)
                                            }
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("reader_bookmark_${bookmark.id}"),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector = ReaderToolsBookmarkIcon,
                                                contentDescription = null,
                                                tint = colors.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = bookmark.label ?: "저장된 위치",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    TextButton(
                                        enabled = canMutate,
                                        onClick = { controller.tools.deleteBookmark(bookmark.id) },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = colors.error,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Icon(
                                            imageVector = ReaderToolsTrashIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("삭제", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (index < state.bookmarks.lastIndex) {
                                    HorizontalDivider(
                                        color = colors.outlineVariant.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        }
                    }

                    if (state.bookmarks.size >= 1000 && state.bookmarkCursor != null) {
                        Text(
                            text = "처음 1,000개를 표시했습니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    } else if (state.bookmarkCursor != null && !state.bookmarksLoading) {
                        OutlinedButton(
                            onClick = { controller.tools.loadBookmarks(more = true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.outlineVariant),
                        ) {
                            Text("더 보기", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("reader_bookmark_close"),
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
private fun ReaderToolsErrorBanner(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.errorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.45f)),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = ReaderToolsAlertIcon,
                    contentDescription = null,
                    tint = colors.onErrorContainer,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onErrorContainer,
                )
            }
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.onErrorContainer,
                    ),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun buildHighlightedSnippet(
    snippet: String,
    query: String,
    ignoreAsciiCase: Boolean,
    highlightBg: Color,
    highlightInk: Color,
): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(snippet)
    return buildAnnotatedString {
        append(snippet)
        var start = 0
        while (start < snippet.length) {
            val found = snippet.indexOf(query, startIndex = start, ignoreCase = ignoreAsciiCase)
            if (found < 0) break
            val end = (found + query.length).coerceAtMost(snippet.length)
            addStyle(
                style = SpanStyle(
                    background = highlightBg,
                    color = highlightInk,
                    fontWeight = FontWeight.SemiBold,
                ),
                start = found,
                end = end,
            )
            start = end
        }
    }
}

private val ReaderToolsSearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderToolsSearch",
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

private val ReaderToolsClearIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderToolsClear",
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
            moveTo(18f, 6f)
            lineTo(6f, 18f)
            moveTo(6f, 6f)
            lineTo(18f, 18f)
        }
    }.build()
}

private val ReaderToolsBookmarkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderToolsBookmark",
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

private val ReaderToolsTrashIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderToolsTrash",
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
            horizontalLineTo(20f)
            moveTo(9f, 7f)
            verticalLineTo(5f)
            horizontalLineTo(15f)
            verticalLineTo(7f)
            moveTo(7f, 7f)
            lineTo(8f, 20f)
            horizontalLineTo(16f)
            lineTo(17f, 7f)
        }
    }.build()
}

private val ReaderToolsAlertIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderToolsAlert",
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
            curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
            curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
            curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
            curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
            close()
            moveTo(12f, 8f)
            verticalLineTo(13f)
            moveTo(12f, 16.5f)
            verticalLineTo(16.6f)
        }
    }.build()
}
