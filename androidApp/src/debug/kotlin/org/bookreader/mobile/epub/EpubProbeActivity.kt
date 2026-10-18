package org.bookreader.mobile.epub

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.bookreader.mobile.R
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** Debug-only generated-fixture host, never reachable through the product's import UI. */
@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
class EpubProbeActivity : FragmentActivity() {
    var publication: Publication? = null
        private set
    var navigator: EpubNavigatorFragment? = null
        private set
    var failure: String? = null
        private set
    var loadedPages = 0
        private set
    var renderedPages = 0
        private set
    var renderedLocator: Locator? = null
        private set
    private var preferences = EpubPreferences()
    fun submitPreferences(value: EpubPreferences) {
        preferences = value
        requireNotNull(navigator).submitPreferences(value)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        // The probe reopens its publication and explicitly restores the full public Locator.
        super.onCreate(null)
        setContentView(FrameLayout(this).apply { id = R.id.epub_probe_container })
        val fixture = intent.getStringExtra("fixture") ?: "epub3"
        require(fixture in setOf("epub2", "epub3", "active-script", "external-image", "css-external", "disguised-markup", "search-limit"))
        val restored = savedInstanceState?.getString("locator")?.let { Locator.fromJSON(JSONObject(it)) }
        preferences = savedInstanceState?.getString("preferences")?.let {
            kotlinx.serialization.json.Json.decodeFromString(EpubPreferences.serializer(), it)
        } ?: EpubPreferences()
        lifecycleScope.launch {
            try {
                val file = File(cacheDir, "epub-probe-$fixture.epub")
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    assets.open("epub-fixtures/$fixture.epub").use { source -> file.outputStream().use(source::copyTo) }
                }
                val opened = SafeEpub.open(file)
                publication = opened
                val factory = EpubNavigatorFactory(opened).createFragmentFactory(restored,
                    initialPreferences = preferences,
                    paginationListener = object : EpubNavigatorFragment.PaginationListener {
                        override fun onPageLoaded() { loadedPages++ }
                        override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
                            renderedLocator = locator
                            renderedPages++
                        }
                    })
                supportFragmentManager.fragmentFactory = factory
                val fragment = factory.instantiate(classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                navigator = fragment
                supportFragmentManager.beginTransaction().replace(R.id.epub_probe_container, fragment).commitNow()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error.javaClass.simpleName }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        navigator?.currentLocator?.value?.let { outState.putString("locator", it.toJSON().toString()) }
        outState.putString("preferences", kotlinx.serialization.json.Json.encodeToString(EpubPreferences.serializer(), preferences))
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
        publication = null
    }
}
