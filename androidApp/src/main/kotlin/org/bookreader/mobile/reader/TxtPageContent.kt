package org.bookreader.mobile.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Style includes font/size/line/letter/locale/break/hyphenation/font-padding policy. */
private data class TxtPageLayoutKey(
    val revision: String, val engine: String = "compose-1.9.3-page-1",
    val style: TextStyle, val resolvedFont: Any, val density: Float, val fontScale: Float,
    val viewport: IntSize, val margin: Int, val insets: List<Int>, val locale: String,
    val direction: androidx.compose.ui.unit.LayoutDirection,
)
private data class LoadedPage(val engine: TxtPageEngine, val generation: Int, val page: TxtMeasuredPage)
data class PageStepRequest(val id: Long = 0, val direction: Int = 0)

@Composable
fun TxtPageContent(controller: TxtReaderController, state: TxtReaderState, style: TextStyle,
    modifier: Modifier = Modifier, step: PageStepRequest = PageStepRequest(), showControls: Boolean = true,
    onAvailability: (Boolean, Boolean) -> Unit = { _, _ -> }) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val font = LocalFontFamilyResolver.current.resolve(style.fontFamily).value
    val insets = WindowInsets.safeDrawing
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val scope = rememberCoroutineScope()
    var size by remember(controller) { mutableStateOf(IntSize.Zero) }
    val key = TxtPageLayoutKey(requireNotNull(controller.book.currentRevision), style = style,
        resolvedFont = font, density = density.density, fontScale = density.fontScale, viewport = size,
        margin = state.settings.marginDp, insets = listOf(insets.getLeft(density, direction),
            insets.getTop(density), insets.getRight(density, direction), insets.getBottom(density)),
        locale = configuration.locales.toLanguageTags(), direction = direction)
    val engine = remember(key, state.cache.complete) {
        if (size.width > 0 && size.height > 0 && state.cache.complete)
            TxtPageEngine(state.cache.committedLength, size.height, controller::range) { text ->
                measurer.measure(AnnotatedString(text), style, constraints = Constraints(maxWidth = size.width))
            } else null
    }
    val latestEngine by rememberUpdatedState(engine)
    LaunchedEffect(key) { controller.beginReflow() }
    val generation = state.restoreGeneration
    val loaded by produceState<LoadedPage?>(null, engine, generation, state.targetOffset) {
        val active = engine ?: return@produceState
        try {
            val page = if (state.targetOffset == state.cache.committedLength)
                active.previous(state.targetOffset) else active.next(state.targetOffset)
            value = LoadedPage(active, generation, page)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { controller.contentReadFailed() }
    }
    val page = loaded?.takeIf { it.engine === engine && it.generation == generation }?.page
    val canPrevious = state.phase == TxtReaderPhase.READY && (page?.range?.start ?: 0) > 0
    val canNext = state.phase == TxtReaderPhase.READY && page != null && page.range.end < state.cache.committedLength
    SideEffect { onAvailability(canPrevious, canNext) }
    LaunchedEffect(step.id) {
        if (step.id == 0L || state.phase != TxtReaderPhase.READY) return@LaunchedEffect
        val displayed = page ?: return@LaunchedEffect
        try {
            val target = if (step.direction < 0 && displayed.range.start > 0)
                engine?.previous(displayed.range.start)?.range?.start
            else if (step.direction > 0 && displayed.range.end < state.cache.committedLength) displayed.range.end else null
            if (target != null && controller.state.value.restoreGeneration == generation) controller.navigateTo(target)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { controller.contentReadFailed() }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().testTag("reader_page_viewport").onSizeChanged { size = it }) {
            page?.let { displayed ->
                // Very large accessibility fonts may exceed the viewport even for one line.
                // A bounded page stays vertically scrollable instead of clipping that line.
                androidx.compose.foundation.text.selection.SelectionContainer { Text(displayed.text, style = style, maxLines = displayed.lines,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .testTag("reader_page"),
                    onTextLayout = { actual ->
                        if (latestEngine !== engine) return@Text
                        if (actual.lineCount == displayed.lines &&
                            actual.getLineEnd(actual.lineCount - 1, visibleEnd = false) == displayed.text.length)
                            controller.restored(generation)
                        else controller.restoreFailed()
                    }) }
            }
        }
        if (showControls) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = state.phase == TxtReaderPhase.READY && (page?.range?.start ?: 0) > 0,
                modifier = Modifier.testTag("reader_previous"), onClick = {
                    val current = page ?: return@TextButton
                    scope.launch {
                        try {
                            val previous = engine?.previous(current.range.start) ?: return@launch
                            if (controller.state.value.restoreGeneration == generation)
                                controller.navigateTo(previous.range.start)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { controller.contentReadFailed() }
                    }
                }) { Text("이전") }
            TextButton(enabled = state.phase == TxtReaderPhase.READY && page != null &&
                requireNotNull(page).range.end < state.cache.committedLength,
                modifier = Modifier.testTag("reader_next"), onClick = {
                    page?.let { controller.navigateTo(it.range.end) }
                }) { Text("다음") }
        }
    }
}
