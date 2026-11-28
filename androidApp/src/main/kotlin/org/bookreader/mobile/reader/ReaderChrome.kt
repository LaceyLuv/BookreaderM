package org.bookreader.mobile.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import org.bookreader.mobile.settings.PageTransition
import org.bookreader.mobile.settings.ReaderTheme

private val ReaderLightControlColors = lightColorScheme(
    primary = Color(0xFF16685B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFF1EC),
    onPrimaryContainer = Color(0xFF0E3F37),
    secondaryContainer = Color(0xFFE7EAE4),
    onSecondaryContainer = Color(0xFF1F272D),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFFAF9F6),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFEEEDE8),
    onSurfaceVariant = Color(0xFF5F6770),
    surfaceContainerHigh = Color(0xFFF2F1EC),
    surfaceContainerHighest = Color(0xFFE9E8E2),
    outline = Color(0xFF9AA2AA),
    outlineVariant = Color(0xFFE1E3DE),
)

private val ReaderDarkControlColors = darkColorScheme(
    primary = Color(0xFF58C4B0),
    onPrimary = Color(0xFF082721),
    primaryContainer = Color(0xFF163A34),
    onPrimaryContainer = Color(0xFFC7F2E8),
    secondaryContainer = Color(0xFF24292E),
    onSecondaryContainer = Color(0xFFE2E6EA),
    background = Color(0xFF101214),
    onBackground = Color(0xFFF3F4F2),
    surface = Color(0xFF141618),
    onSurface = Color(0xFFF3F4F2),
    surfaceVariant = Color(0xFF1E2226),
    onSurfaceVariant = Color(0xFF9BA3AC),
    surfaceContainerHigh = Color(0xFF191C20),
    surfaceContainerHighest = Color(0xFF23282D),
    outline = Color(0xFF525B65),
    outlineVariant = Color(0xFF2B3138),
)

private const val ChromeColorTransitionMs = 260
private const val ChromeSlideEnterMs = 240
private const val ChromeSlideExitMs = 200

fun ReaderTheme.isDarkBackground(): Boolean =
    this == ReaderTheme.DARK || this == ReaderTheme.OLED

@Composable
private fun animatedChromeColor(light: Color, dark: Color, darkTheme: Boolean, label: String): Color {
    val color by animateColorAsState(
        targetValue = if (darkTheme) dark else light,
        animationSpec = tween(durationMillis = ChromeColorTransitionMs, easing = FastOutSlowInEasing),
        label = label,
    )
    return color
}

@Composable
private fun animatedReaderControlScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) ReaderDarkControlColors else ReaderLightControlColors
    return base.copy(
        primary = animatedChromeColor(ReaderLightControlColors.primary, ReaderDarkControlColors.primary, darkTheme, "primary"),
        onPrimary = animatedChromeColor(ReaderLightControlColors.onPrimary, ReaderDarkControlColors.onPrimary, darkTheme, "onPrimary"),
        primaryContainer = animatedChromeColor(ReaderLightControlColors.primaryContainer, ReaderDarkControlColors.primaryContainer, darkTheme, "primaryContainer"),
        onPrimaryContainer = animatedChromeColor(ReaderLightControlColors.onPrimaryContainer, ReaderDarkControlColors.onPrimaryContainer, darkTheme, "onPrimaryContainer"),
        secondaryContainer = animatedChromeColor(ReaderLightControlColors.secondaryContainer, ReaderDarkControlColors.secondaryContainer, darkTheme, "secondaryContainer"),
        onSecondaryContainer = animatedChromeColor(ReaderLightControlColors.onSecondaryContainer, ReaderDarkControlColors.onSecondaryContainer, darkTheme, "onSecondaryContainer"),
        background = animatedChromeColor(ReaderLightControlColors.background, ReaderDarkControlColors.background, darkTheme, "background"),
        onBackground = animatedChromeColor(ReaderLightControlColors.onBackground, ReaderDarkControlColors.onBackground, darkTheme, "onBackground"),
        surface = animatedChromeColor(ReaderLightControlColors.surface, ReaderDarkControlColors.surface, darkTheme, "surface"),
        onSurface = animatedChromeColor(ReaderLightControlColors.onSurface, ReaderDarkControlColors.onSurface, darkTheme, "onSurface"),
        surfaceVariant = animatedChromeColor(ReaderLightControlColors.surfaceVariant, ReaderDarkControlColors.surfaceVariant, darkTheme, "surfaceVariant"),
        onSurfaceVariant = animatedChromeColor(ReaderLightControlColors.onSurfaceVariant, ReaderDarkControlColors.onSurfaceVariant, darkTheme, "onSurfaceVariant"),
        surfaceContainerHigh = animatedChromeColor(ReaderLightControlColors.surfaceContainerHigh, ReaderDarkControlColors.surfaceContainerHigh, darkTheme, "surfaceContainerHigh"),
        surfaceContainerHighest = animatedChromeColor(ReaderLightControlColors.surfaceContainerHighest, ReaderDarkControlColors.surfaceContainerHighest, darkTheme, "surfaceContainerHighest"),
        outline = animatedChromeColor(ReaderLightControlColors.outline, ReaderDarkControlColors.outline, darkTheme, "outline"),
        outlineVariant = animatedChromeColor(ReaderLightControlColors.outlineVariant, ReaderDarkControlColors.outlineVariant, darkTheme, "outlineVariant"),
    )
}

/**
 * Controls use a bright editorial palette by default and smoothly animate to
 * a deep charcoal palette only when the reader background is dark.
 */
@Composable
fun ReaderControlTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = animatedReaderControlScheme(darkTheme = darkTheme),
        content = content,
    )
}

fun ReaderTheme.paperColor(): Color = when (this) {
    ReaderTheme.LIGHT -> Color(0xFFFFFDF8)
    ReaderTheme.SEPIA -> Color(0xFFF1E5CA)
    ReaderTheme.DARK -> Color(0xFF222222)
    ReaderTheme.OLED -> Color.Black
}

fun ReaderTheme.inkColor(): Color = when (this) {
    ReaderTheme.LIGHT, ReaderTheme.SEPIA -> Color(0xFF202020)
    ReaderTheme.DARK, ReaderTheme.OLED -> Color(0xFFE8E8E8)
}

/**
 * Edge-docked reader chrome with top/bottom slide animations.
 * The bottom dock extends flush to the bottom edge (covering the 24dp page status strip while open)
 * without altering the underlying content viewport or saved reading position.
 */
@Composable
fun ReaderChromeOverlay(
    visible: Boolean,
    title: String,
    format: String,
    prefix: String,
    progress: String,
    onClose: () -> Unit,
    onHide: () -> Unit,
    canHide: Boolean,
    previous: Boolean,
    next: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: () -> Unit,
    darkTheme: Boolean = false,
    actions: @Composable () -> Unit,
) {
    ReaderControlTheme(darkTheme = darkTheme) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(durationMillis = ChromeSlideEnterMs, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = ChromeSlideExitMs, easing = FastOutSlowInEasing)),
        ) {
            ReaderChromeContent(
                title = title,
                format = format,
                prefix = prefix,
                progress = progress,
                onClose = onClose,
                onHide = onHide,
                canHide = canHide,
                previous = previous,
                next = next,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                actions = actions,
            )
        }
    }
}

/** Backwards-compatible entry point with built-in slide-in enter animation. */
@Composable
fun ReaderChrome(
    title: String,
    format: String,
    prefix: String,
    progress: String,
    onClose: () -> Unit,
    onHide: () -> Unit,
    canHide: Boolean,
    previous: Boolean,
    next: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: () -> Unit,
    darkTheme: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    ReaderControlTheme(darkTheme = darkTheme) {
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(durationMillis = ChromeSlideEnterMs, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = ChromeSlideExitMs, easing = FastOutSlowInEasing)),
        ) {
            ReaderChromeContent(
                title = title,
                format = format,
                prefix = prefix,
                progress = progress,
                onClose = onClose,
                onHide = onHide,
                canHide = canHide,
                previous = previous,
                next = next,
                onPrevious = onPrevious,
                onNext = onNext,
                onSeek = onSeek,
                actions = actions,
            )
        }
    }
}

@Composable
private fun AnimatedVisibilityScope.ReaderChromeContent(
    title: String,
    format: String,
    prefix: String,
    progress: String,
    onClose: () -> Unit,
    onHide: () -> Unit,
    canHide: Boolean,
    previous: Boolean,
    next: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val progressFraction = remember(progress) { parseReaderProgressFraction(progress) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("${prefix}_chrome")) {
        val panelHeight = (maxHeight * .42f - 24.dp).coerceAtLeast(48.dp)

        // Top edge-docked slim bar: slides down from top edge to maximize central reading canvas.
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .animateEnterExit(
                    enter = slideInVertically(
                        animationSpec = tween(durationMillis = ChromeSlideEnterMs, easing = FastOutSlowInEasing),
                    ) { -it },
                    exit = slideOutVertically(
                        animationSpec = tween(durationMillis = ChromeSlideExitMs, easing = FastOutSlowInEasing),
                    ) { -it },
                ),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = panelHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("${prefix}_back"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = ReaderChevronLeftIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("서재", fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = format,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (canHide) {
                        FilledTonalButton(
                            onClick = onHide,
                            modifier = Modifier.testTag("${prefix}_hide"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(
                                imageVector = ReaderHideIcon,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("숨기기", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // Bottom edge-docked bar: slides up from bottom edge and covers the 24dp ReaderPageStatus strip.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .animateEnterExit(
                    enter = slideInVertically(
                        animationSpec = tween(durationMillis = ChromeSlideEnterMs, easing = FastOutSlowInEasing),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(durationMillis = ChromeSlideExitMs, easing = FastOutSlowInEasing),
                    ) { it },
                ),
            shape = RectangleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                if (progressFraction != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = panelHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = onPrevious,
                            enabled = previous,
                            modifier = Modifier.testTag("${prefix}_previous"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            ),
                        ) {
                            Icon(
                                imageVector = ReaderChevronLeftIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (previous) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                },
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("이전", fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = onSeek,
                            modifier = Modifier.weight(1f).testTag("${prefix}_menu"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = progress,
                                    modifier = Modifier.testTag("${prefix}_progress"),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text("위치 이동", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        TextButton(
                            onClick = onNext,
                            enabled = next,
                            modifier = Modifier.testTag("${prefix}_next"),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            ),
                        ) {
                            Text("다음", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                imageVector = ReaderChevronRightIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (next) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                },
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    actions()
                    if (canHide) {
                        Text(
                            text = "좌우 스와이프·탭으로 이동 · 가운데 탭으로 도구 열고 닫기",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun ReaderLoadingExit(
    prefix: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
) {
    ReaderControlTheme(darkTheme = darkTheme) {
        Surface(
            modifier = modifier.padding(12.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
        ) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.testTag("${prefix}_back"),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = ReaderChevronLeftIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(2.dp))
                Text("서재", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

val ReaderPageStatusHeight = 24.dp

/** Always present, outside the book viewport, with no buttons competing with the text. */
@Composable
fun ReaderPageStatus(label: String, prefix: String, paper: Color, ink: Color) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(ReaderPageStatusHeight),
            color = paper,
            contentColor = ink.copy(alpha = 0.68f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("${prefix}_bottom_pages"),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderTransitionSettings(
    value: PageTransition,
    onChange: (PageTransition) -> Unit,
) {
    ReaderSettingsSection("페이지 전환") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTransition.entries.forEach { transition ->
                FilterChip(
                    selected = value == transition,
                    onClick = { onChange(transition) },
                    modifier = Modifier.testTag("reader_transition_${transition.name}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    label = {
                        Text(if (transition == PageTransition.NONE) "효과 없음" else "밀어서 넘기기")
                    },
                )
            }
        }
    }
}

private val PercentRegex = Regex("""(\d+(?:\.\d+)?)\s*%""")
private val PageRatioRegex = Regex("""(\d+)\s*/\s*(\d+)""")

private fun parseReaderProgressFraction(progress: String): Float? {
    PercentRegex.find(progress)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { pct ->
        return (pct / 100f).coerceIn(0f, 1f)
    }
    val match = PageRatioRegex.find(progress) ?: return null
    val current = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: return null
    val total = match.groupValues.getOrNull(2)?.toFloatOrNull() ?: return null
    if (total <= 0f) return null
    return (current / total).coerceIn(0f, 1f)
}

private val ReaderChevronLeftIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderChevronLeft",
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
            moveTo(15f, 6f)
            lineTo(9f, 12f)
            lineTo(15f, 18f)
        }
    }.build()
}

private val ReaderChevronRightIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderChevronRight",
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
            moveTo(9f, 6f)
            lineTo(15f, 12f)
            lineTo(9f, 18f)
        }
    }.build()
}

private val ReaderHideIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReaderHide",
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
            moveTo(3f, 3f)
            lineTo(21f, 21f)
            moveTo(10.58f, 10.58f)
            arcTo(2f, 2f, 0f, false, false, 13.42f, 13.42f)
            moveTo(9.88f, 5.09f)
            quadTo(10.91f, 4.8f, 12f, 4.8f)
            curveTo(16.6f, 4.8f, 20.1f, 8.2f, 21.5f, 12f)
            curveTo(20.9f, 13.6f, 19.9f, 15.1f, 18.6f, 16.2f)
            moveTo(6.35f, 6.35f)
            curveTo(4.5f, 7.7f, 3.2f, 9.7f, 2.5f, 12f)
            curveTo(3.9f, 15.8f, 7.4f, 19.2f, 12f, 19.2f)
            curveTo(13.8f, 19.2f, 15.5f, 18.7f, 16.9f, 17.8f)
        }
    }.build()
}
