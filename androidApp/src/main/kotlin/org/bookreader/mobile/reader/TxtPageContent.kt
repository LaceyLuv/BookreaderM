package org.bookreader.mobile.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
    modifier: Modifier = Modifier, step: PageStepRequest = PageStepRequest(),
    onAvailability: (Boolean, Boolean) -> Unit = { _, _ -> }) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val font = LocalFontFamilyResolver.current.resolve(style.fontFamily, fontWeight = style.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal).value
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    val measurer = rememberTextMeasurer(cacheSize = 4)
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
    val visible = loaded?.takeIf { it.engine === engine }
    val page = visible?.page
    var turnGeneration by remember(controller) { mutableIntStateOf(-1) }
    var turnDirection by remember(controller) { mutableIntStateOf(1) }
    var measuredGeneration by remember(engine) { mutableIntStateOf(-1) }
    var settledGeneration by remember(engine) { mutableIntStateOf(-1) }
    LaunchedEffect(measuredGeneration, settledGeneration, generation) {
        if (measuredGeneration == generation && settledGeneration == generation) controller.restored(generation)
    }
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
            if (target != null && controller.state.value.restoreGeneration == generation) {
                turnDirection = if (step.direction < 0) -1 else 1
                turnGeneration = generation + 1
                controller.navigateTo(target)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { controller.contentReadFailed() }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().testTag("reader_page_viewport").onSizeChanged { size = it }) {
            visible?.let { prepared ->
                ReaderPageTransition(prepared,
                    if (prepared.generation == turnGeneration) state.settings.pageTransition else org.bookreader.mobile.settings.PageTransition.NONE,
                    turnDirection, Modifier.fillMaxSize(), onSettled = { settledGeneration = it.generation }) { rendered ->
                val displayed = rendered.page
                // Very large accessibility fonts may exceed the viewport even for one line.
                // A bounded page stays vertically scrollable instead of clipping that line.
                androidx.compose.foundation.text.selection.SelectionContainer { Text(displayed.text, style = style, maxLines = displayed.lines,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .testTag("reader_page"),
                    onTextLayout = { actual ->
                        if (latestEngine !== rendered.engine || controller.state.value.restoreGeneration != rendered.generation) return@Text
                        if (actual.lineCount == displayed.lines &&
                            actual.getLineEnd(actual.lineCount - 1, visibleEnd = false) == displayed.text.length)
                            measuredGeneration = rendered.generation
                        else controller.restoreFailed()
                    }) }
                }
            }
        }
    }
}
