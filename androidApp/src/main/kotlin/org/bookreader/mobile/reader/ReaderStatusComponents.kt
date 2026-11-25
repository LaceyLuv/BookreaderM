package org.bookreader.mobile.reader

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private data class ReaderAlertPalette(
    val container: Color,
    val border: Color,
    val content: Color,
)

private fun readerAlertPalette(darkTheme: Boolean): ReaderAlertPalette =
    if (darkTheme) {
        ReaderAlertPalette(
            container = Color(0xFF3B181A),
            border = Color(0xFF7C3237),
            content = Color(0xFFFFB4AB),
        )
    } else {
        ReaderAlertPalette(
            container = Color(0xFFFCE8E8),
            border = Color(0xFFF2B8B5),
            content = Color(0xFF9C1F1C),
        )
    }

data class ReaderInlineAlertEntry(
    val message: String,
    val tag: String? = null,
)

/**
 * Unified fatal error state card shared across TXT, EPUB, and Comic readers.
 * Preserves high contrast on both bright and dark/OLED paper themes and keeps
 * retry/error testTags intact.
 */
@Composable
fun ReaderErrorStateCard(
    formatLabel: String,
    message: String,
    errorTag: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryTag: String? = null,
    onClose: (() -> Unit)? = null,
    darkTheme: Boolean = false,
) {
    ReaderControlTheme(darkTheme = darkTheme) {
        val colors = MaterialTheme.colorScheme
        val alert = readerAlertPalette(darkTheme)
        Box(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, colors.outlineVariant),
                tonalElevation = 0.dp,
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = alert.container,
                            border = BorderStroke(1.dp, alert.border),
                            tonalElevation = 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = ReaderStatusAlertIcon,
                                    contentDescription = null,
                                    tint = alert.content,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "$formatLabel 리더 열기 오류",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = alert.content,
                            )
                            Text(
                                text = "책을 불러오지 못했습니다",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceContainerHigh,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag(errorTag),
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .let { base -> if (retryTag != null) base.testTag(retryTag) else base },
                        ) {
                            Icon(
                                imageVector = ReaderStatusRefreshIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "다시 시도",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (onClose != null) {
                            OutlinedButton(
                                onClick = onClose,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    contentColor = colors.onSurface,
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp),
                            ) {
                                Text(
                                    text = "서재로",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Unified initial/page loading card shared across TXT, EPUB, and Comic readers.
 */
@Composable
fun ReaderLoadingStateCard(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    darkTheme: Boolean = false,
) {
    ReaderControlTheme(darkTheme = darkTheme) {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 210.dp, max = 320.dp),
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    tonalElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.4.dp,
                            color = colors.primary,
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Non-intrusive top alert banner for progress save or settings read failures.
 * Avoids blocking the center reading prose while preserving every error testTag.
 */
@Composable
fun ReaderInlineAlertBanner(
    entries: List<ReaderInlineAlertEntry>,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
) {
    if (entries.isEmpty()) return
    ReaderControlTheme(darkTheme = darkTheme) {
        val alert = readerAlertPalette(darkTheme)
        Surface(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = alert.container,
            contentColor = alert.content,
            border = BorderStroke(1.dp, alert.border),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = ReaderStatusAlertIcon,
                    contentDescription = null,
                    tint = alert.content,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(16.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) {
                            HorizontalDivider(color = alert.border.copy(alpha = 0.7f))
                        }
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = alert.content,
                            modifier = if (entry.tag != null) Modifier.testTag(entry.tag) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified dark fallback card for individual comic page decode failures across
 * both single-page and continuous scrolling modes.
 */
@Composable
fun ComicImageErrorCard(
    canSkip: Boolean,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    entryLabel: String? = null,
) {
    ReaderControlTheme(darkTheme = true) {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 336.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF19222D),
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, Color(0xFF2E3B4A)),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF243140),
                        border = BorderStroke(1.dp, Color(0xFF354659)),
                        tonalElevation = 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = ReaderStatusImageOffIcon,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (!entryLabel.isNullOrBlank()) {
                                "이미지 디코딩 실패 · $entryLabel"
                            } else {
                                "이미지 디코딩 실패"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "이미지를 표시하지 못했습니다. 원본과 다른 페이지는 유지됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                            modifier = Modifier.testTag("comic_image_error"),
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp),
                    ) {
                        Icon(
                            imageVector = ReaderStatusRefreshIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "다시 시도",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    OutlinedButton(
                        onClick = onSkip,
                        enabled = canSkip,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF354659)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF232D38),
                            contentColor = colors.onSurface,
                            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .testTag("comic_skip"),
                    ) {
                        Icon(
                            imageVector = ReaderStatusSkipIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "다음 이미지로 건너뛰기",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-contrast dark pill banner shown when comic resolution is capped for memory safety.
 */
@Composable
fun ReaderResolutionHintBanner(
    message: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xE60E141C),
        contentColor = Color(0xFFDCE5EE),
        border = BorderStroke(1.dp, Color(0xFF2E3B4A)),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = ReaderStatusInfoIcon,
                contentDescription = null,
                tint = Color(0xFF58C4B0),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFDCE5EE),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

private val ReaderStatusAlertIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderStatusAlert",
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
            moveTo(12f, 3.5f)
            lineTo(21f, 19.5f)
            horizontalLineTo(3f)
            close()
            moveTo(12f, 9.5f)
            verticalLineTo(13.5f)
            moveTo(12f, 16.7f)
            verticalLineTo(17.1f)
        }
    }.build()
}

private val ReaderStatusRefreshIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderStatusRefresh",
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
            moveTo(20f, 11f)
            curveTo(19.5f, 6.8f, 16f, 4f, 12f, 4f)
            curveTo(8.8f, 4f, 6.1f, 5.8f, 4.8f, 8.5f)
            moveTo(4f, 4.5f)
            verticalLineTo(8.5f)
            horizontalLineTo(8f)
            moveTo(4f, 13f)
            curveTo(4.5f, 17.2f, 8f, 20f, 12f, 20f)
            curveTo(15.2f, 20f, 17.9f, 18.2f, 19.2f, 15.5f)
            moveTo(20f, 19.5f)
            verticalLineTo(15.5f)
            horizontalLineTo(16f)
        }
    }.build()
}

private val ReaderStatusImageOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderStatusImageOff",
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
            moveTo(4f, 5f)
            horizontalLineTo(20f)
            verticalLineTo(19f)
            horizontalLineTo(4f)
            close()
            moveTo(4f, 16f)
            lineTo(9f, 11f)
            lineTo(13f, 15f)
            lineTo(16f, 12.5f)
            lineTo(20f, 16.5f)
            moveTo(3f, 3f)
            lineTo(21f, 21f)
        }
    }.build()
}

private val ReaderStatusSkipIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderStatusSkip",
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
            moveTo(5f, 5.5f)
            lineTo(14f, 12f)
            lineTo(5f, 18.5f)
            close()
            moveTo(18.5f, 5.5f)
            verticalLineTo(18.5f)
        }
    }.build()
}

private val ReaderStatusInfoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderStatusInfo",
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
            moveTo(12f, 3.5f)
            curveTo(16.7f, 3.5f, 20.5f, 7.3f, 20.5f, 12f)
            curveTo(20.5f, 16.7f, 16.7f, 20.5f, 12f, 20.5f)
            curveTo(7.3f, 20.5f, 3.5f, 16.7f, 3.5f, 12f)
            curveTo(3.5f, 7.3f, 7.3f, 3.5f, 12f, 3.5f)
            close()
            moveTo(12f, 11f)
            verticalLineTo(16f)
            moveTo(12f, 8f)
            verticalLineTo(8.4f)
        }
    }.build()
}
