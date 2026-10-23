package org.bookreader.mobile.epub

import android.os.Bundle
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
