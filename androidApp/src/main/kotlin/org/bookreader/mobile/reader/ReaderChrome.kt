package org.bookreader.mobile.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bookreader.mobile.settings.ReaderTheme

/** Controls have their own palette; publication colors never leak into the tool panels. */
@Composable
fun ReaderControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFA7E0D3), onPrimary = Color(0xFF103C34),
        primaryContainer = Color(0xFF294E48), onPrimaryContainer = Color(0xFFD5F5EC),
        secondaryContainer = Color(0xFF35485C), onSecondaryContainer = Color(0xFFE0EBF8),
        background = Color(0xFF101720), onBackground = Color(0xFFF0F3F7),
        surface = Color(0xFF202D3B), onSurface = Color(0xFFF0F3F7),
        surfaceVariant = Color(0xFF2B3B4C), onSurfaceVariant = Color(0xFFBDCBD8),
        surfaceContainerHigh = Color(0xFF243343), surfaceContainerHighest = Color(0xFF2B3B4C),
        outline = Color(0xFF8294A7), outlineVariant = Color(0xFF485B6E),
    ), content = content)
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

/** Floating surfaces leave the content's measured viewport and canonical anchor unchanged. */
@Composable
fun ReaderChrome(
    title: String, format: String, prefix: String, progress: String,
    onClose: () -> Unit, onHide: () -> Unit, canHide: Boolean,
    previous: Boolean, next: Boolean, onPrevious: () -> Unit, onNext: () -> Unit,
    actions: @Composable () -> Unit,
) {
    ReaderControlTheme {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("${prefix}_chrome")) {
            val panelHeight = (maxHeight * .42f - 24.dp).coerceAtLeast(48.dp)
            Surface(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.heightIn(max = panelHeight).verticalScroll(rememberScrollState()).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose, modifier = Modifier.testTag("${prefix}_back")) { Text("‹ 서재") }
                    Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(format, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    }
                    if (canHide) TextButton(onClick = onHide, modifier = Modifier.testTag("${prefix}_hide")) { Text("숨기기") }
                }
            }
            Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp), shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.heightIn(max = panelHeight).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onPrevious, enabled = previous, modifier = Modifier.testTag("${prefix}_previous")) { Text("‹ 이전") }
                        Text(progress, Modifier.weight(1f).testTag("${prefix}_progress"),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = onNext, enabled = next, modifier = Modifier.testTag("${prefix}_next")) { Text("다음 ›") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    actions()
                    Text("좌우 탭으로 이동 · 가운데 탭으로 도구 열고 닫기",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
fun ReaderSettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable
fun ReaderLoadingExit(prefix: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    ReaderControlTheme {
        Surface(modifier.padding(12.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
            TextButton(onClick = onClose, modifier = Modifier.testTag("${prefix}_back")) { Text("‹ 서재") }
        }
    }
}

val ReaderPageStatusHeight = 24.dp

/** Always present, outside the book viewport, with no buttons competing with the text. */
@Composable
fun ReaderPageStatus(label: String, prefix: String, paper: Color, ink: Color) {
    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(ReaderPageStatusHeight), color = paper, contentColor = ink) {
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    modifier = Modifier.testTag("${prefix}_bottom_pages"))
            }
        }
    }
}

@Composable
fun ReaderTransitionSettings(value: org.bookreader.mobile.settings.PageTransition,
    onChange: (org.bookreader.mobile.settings.PageTransition) -> Unit) {
    ReaderSettingsSection("페이지 전환") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            org.bookreader.mobile.settings.PageTransition.entries.forEach { transition ->
                FilterChip(selected = value == transition, onClick = { onChange(transition) },
                    modifier = Modifier.testTag("reader_transition_${transition.name}"),
                    label = { Text(if (transition == org.bookreader.mobile.settings.PageTransition.NONE) "효과 없음" else "밀어서 넘기기") })
            }
        }
    }
}
