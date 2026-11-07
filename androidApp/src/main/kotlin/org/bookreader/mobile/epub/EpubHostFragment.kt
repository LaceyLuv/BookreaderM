package org.bookreader.mobile.epub

import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.html.HtmlDecorationTemplates
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.bookreader.mobile.R
import org.bookreader.mobile.ui.AppViewModel
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression
import org.bookreader.mobile.reader.ArchiveReaderPhase
import org.bookreader.mobile.reader.assetPath
import org.bookreader.mobile.reader.epubFamily
import org.bookreader.mobile.settings.ReaderFont
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError

/** Default-constructible wrapper installs the stable SDK factory before child restoration. */
@OptIn(ExperimentalReadiumApi::class)
class EpubHostFragment : Fragment() {
    var onCenterTap: () -> Unit = {}
    var touchExploration = false
    private var controller: EpubReaderController? = null
    private var navigator: EpubNavigatorFragment? = null
    private var token = 0
    private var lastRendered: Locator? = null
    private val inputListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            val owner = controller ?: return false
            val host = navigator ?: return false
            if (touchExploration || owner.progress.state.value.phase != ArchiveReaderPhase.READY) return false
            val width = host.publicationView.width.coerceAtLeast(1)
            val fraction = event.point.x / width
            // Readium dispatches this only after links, selection and publication handlers.
            // Physical left/right follow the publication's reading direction, including RTL.
            if (fraction < .25f || fraction > .75f) {
                val right = fraction > .75f
                val forward = right == (host.overflow.value.readingProgression == ReadingProgression.LTR)
                owner.next(if (forward) 1 else -1)
            } else onCenterTap()
            return true
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val current = ViewModelProvider(requireActivity())[AppViewModel::class.java].state.value.epubReader
        controller = current
        val publication = current?.publication
        if (current == null || publication == null) {
            // A fresh process owns no Reader. Avoid SDK constructor restoration and let Main
            // render its normal cold Library; never reconstruct navigation from a saved book id.
            childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            return
        }
        childFragmentManager.fragmentFactory = EpubNavigatorFactory(publication).createFragmentFactory(
            current.initialLocator(), initialPreferences = current.preferences(),
            configuration = EpubNavigatorFragment.Configuration(servedAssets = listOf("reader-fonts/.*"),
                selectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        menu.add(0, 0x426d0001, 0, "형광펜 · 메모").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                        menu.add(0, android.R.id.copy, 1, "복사")
                        menu.add(0, android.R.id.selectAll, 2, "모두 선택")
                        return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                    override fun onDestroyActionMode(mode: ActionMode) {}
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        val live = navigator ?: return false
                        when (item.itemId) {
                            0x426d0001 -> current.annotations.capture { mode.finish() }
                            android.R.id.copy -> viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                val raw = live.evaluateJavascript("(() => { const s=window.getSelection(); if(!s || s.isCollapsed) return null; const r=s.getRangeAt(0); const t=r.toString(); return t.length<=262144?t:null; })()")
                                val selected = raw?.let { org.json.JSONTokener(it).nextValue() as? String }
                                if (!selected.isNullOrEmpty() && navigator === live) {
                                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("선택한 문장", selected))
                                    live.clearSelection(); mode.finish()
                                }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    if (isAdded) android.widget.Toast.makeText(requireContext(), "선택한 문장을 복사하지 못했습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            android.R.id.selectAll -> viewLifecycleOwner.lifecycleScope.launch {
                                live.evaluateJavascript("document.execCommand('selectAll');")
                            }
                            else -> return false
                        }
                        return true
                    }
                }, decorationTemplates = HtmlDecorationTemplates.defaultTemplates().apply {
                    set(Decoration.Style.Highlight::class, HtmlDecorationTemplate(HtmlDecorationTemplate.Layout.BOXES,
                        element = { decoration ->
                            val tint = (decoration.style as Decoration.Style.Highlight).tint
                            val rgb = "${android.graphics.Color.red(tint)},${android.graphics.Color.green(tint)},${android.graphics.Color.blue(tint)}"
                            // Validated UUID only; publication text is never interpolated into HTML.
                            """<div data-bookreader-annotation="${decoration.id}" style="background-color:rgba($rgb,0.55)!important;border-radius:2px;"/>"""
                        }))
                }).apply {
                ReaderFont.entries.forEach { font -> font.assetPath?.let { path ->
                    addFontFamilyDeclaration(font.epubFamily()) { addFontFace { addSource(path, preload = font == current.state.value.settings.font) } }
                } }
                current.customFont()?.let { font ->
                    addFontFamilyDeclaration(org.readium.r2.navigator.preferences.FontFamily("USER_${font.id}")) {
                        addFontFace { addSource(requireNotNull(Url("https://readium/publication/_bookreader_fonts/${font.id}.ttf")), preload = true) }
                    }
                }
            },
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) { current.externalLink(url) }
                override fun shouldFollowInternalLink(link: Link, context: HyperlinkNavigator.LinkContext?): Boolean {
                    current.beforeInternalLink(); return true
                }
                override fun onResourceLoadFailed(href: Url, error: ReadError) { current.fail("EPUB 리소스를 읽지 못했습니다. 기존 독서 기록은 유지됩니다.") }
            }, paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                    lastRendered = locator
                    navigator?.let { current.pageChanged(it, token, locator) }
                }
            })
        super.onCreate(savedInstanceState)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        object : FrameLayout(requireContext()) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) controller?.armMovement()
                return super.dispatchTouchEvent(event)
            }
        }.apply { id = R.id.epub_product_navigator }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val owner = controller ?: return
        if (owner.publication == null) return
        val existing = childFragmentManager.findFragmentByTag("epub-navigator") as? EpubNavigatorFragment
        val fragment = existing ?: childFragmentManager.fragmentFactory.instantiate(
            requireContext().classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
        navigator = fragment
        fragment.addInputListener(inputListener)
        token = owner.attach(fragment) {
            if (!childFragmentManager.isDestroyed) childFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
        }
        lastRendered?.let { owner.pageChanged(fragment, token, it) }
        if (existing == null) childFragmentManager.beginTransaction().replace(R.id.epub_product_navigator, fragment, "epub-navigator").commitNow()
    }
    override fun onDestroyView() {
        navigator?.let { it.removeInputListener(inputListener); controller?.detach(it) }
        onCenterTap = {}
        navigator = null
        super.onDestroyView()
    }
}
