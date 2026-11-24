package org.bookreader.mobile.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.bookreader.mobile.settings.ReaderTheme

@Composable
fun ReaderMenuDialog(
    state: TxtReaderState,
    seek: ReaderSeekPreview,
    previous: Boolean,
    next: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val offset = state.stableOffset ?: state.targetOffset
    val current = if (state.cache.complete && state.cache.committedLength > 0) {
        (offset.toDouble() / state.cache.committedLength).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val readyForTools = state.phase == TxtReaderPhase.READY
    val seekEnabled = state.cache.complete && state.phase in setOf(TxtReaderPhase.READY, TxtReaderPhase.REFLOWING)
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
                            text = "빠른 위치 이동 · 도구",
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
                        text = "${theme.readerThemeLabel()} 테마",
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
                    // Position seek & previous/next card
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
                                    text = if (!state.cache.complete) {
                                        "위치 계산 중"
                                    } else {
                                        seek.fraction?.let { "이동 미리보기 ${(it * 100).toInt()}%" }
                                            ?: "현재 ${(current * 100).toInt()}%"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = if (seek.fraction != null) colors.primary else colors.onSurface,
                                    modifier = Modifier.testTag("reader_seek_preview"),
                                )
                            }

                            ReaderPositionSlider(
                                current = current,
                                enabled = seekEnabled,
                                seek = seek,
                                tag = "reader_seek",
                            )

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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onPrevious,
                                    enabled = previous,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .testTag("reader_menu_previous"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.outlineVariant),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = colors.surface,
                                        contentColor = colors.onSurface,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = ReaderMenuChevronLeftIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("이전", fontWeight = FontWeight.SemiBold)
                                }
                                OutlinedButton(
                                    onClick = onNext,
                                    enabled = next,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .testTag("reader_menu_next"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.outlineVariant),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = colors.surface,
                                        contentColor = colors.onSurface,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text("다음", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = ReaderMenuChevronRightIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Quick reader tools list card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            ReaderMenuActionRow(
                                label = "본문 검색",
                                icon = ReaderMenuSearchIcon,
                                enabled = readyForTools,
                                onClick = onSearch,
                            )
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                            ReaderMenuActionRow(
                                label = "북마크",
                                icon = ReaderMenuBookmarkIcon,
                                enabled = readyForTools,
                                onClick = onBookmarks,
                            )
                            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                            ReaderMenuActionRow(
                                label = "읽기 설정",
                                icon = ReaderMenuSettingsIcon,
                                enabled = true,
                                onClick = onSettings,
                            )
                        }
                    }

                    // Separate exit action row (preserved inside verticalScroll for performScrollTo tests)
                    Surface(
                        onClick = onExit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .testTag("reader_menu_exit"),
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = ReaderMenuExitIcon,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "서재로 나가기",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.onSurface,
                                )
                            }
                            Icon(
                                imageVector = ReaderMenuChevronRightIcon,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Subtle gesture hint strip
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = "좌우 탭: 이전·다음 · 가운데 탭: 도구",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("reader_menu_close"),
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
private fun ReaderMenuActionRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else 0.42f
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary.copy(alpha = contentAlpha),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface.copy(alpha = contentAlpha),
                )
            }
            Icon(
                imageVector = ReaderMenuChevronRightIcon,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

internal fun ReaderTheme.readerThemeLabel(): String = when (this) {
    ReaderTheme.LIGHT -> "밝게"
    ReaderTheme.SEPIA -> "세피아"
    ReaderTheme.DARK -> "어둡게"
    ReaderTheme.OLED -> "검정"
}

/** Shared cancellation-aware position control; dragging previews without changing the record. */
@Composable
fun ReaderPositionSlider(current: Float, enabled: Boolean, seek: ReaderSeekPreview, tag: String) {
    val source = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> seek.dragStarted()
                is DragInteraction.Stop -> seek.dragStopped()
                is DragInteraction.Cancel -> seek.cancel()
            }
        }
    }
    DisposableEffect(seek) { onDispose { seek.cancel() } }
    Slider(
        value = seek.fraction ?: current,
        onValueChange = seek::change,
        enabled = enabled,
        interactionSource = source,
        colors = SliderDefaults.colors(
            thumbColor = colors.primary,
            activeTrackColor = colors.primary,
            inactiveTrackColor = colors.outlineVariant,
            disabledThumbColor = colors.onSurface.copy(alpha = 0.32f),
            disabledActiveTrackColor = colors.onSurface.copy(alpha = 0.24f),
            disabledInactiveTrackColor = colors.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = "독서 위치" },
        onValueChangeFinished = {
            val version = seek.version
            // Foundation 1.9.3 emits Stop/Cancel before onDragStopped; that callback also
            // runs on cancellation. Let the interaction collector settle before the tap/
            // accessibility fallback. A cancelled drag never commits its preview.
            scope.launch { yield(); seek.finishNonDrag(version) }
        },
    )
}

private val ReaderMenuChevronLeftIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderMenuChevronLeft",
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
            moveTo(15f, 18f)
            lineTo(9f, 12f)
            lineTo(15f, 6f)
        }
    }.build()
}

private val ReaderMenuChevronRightIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderMenuChevronRight",
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
            moveTo(9f, 18f)
            lineTo(15f, 12f)
            lineTo(9f, 6f)
        }
    }.build()
}

private val ReaderMenuSearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderMenuSearch",
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

private val ReaderMenuBookmarkIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderMenuBookmark",
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

private val ReaderMenuSettingsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderMenuSettings",
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

private val ReaderMenuExitIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderMenuExit",
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
            moveTo(9f, 4f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            horizontalLineTo(9f)
            moveTo(15f, 8f)
            lineTo(20f, 12f)
            lineTo(15f, 16f)
            moveTo(20f, 12f)
            horizontalLineTo(9f)
        }
    }.build()
}
