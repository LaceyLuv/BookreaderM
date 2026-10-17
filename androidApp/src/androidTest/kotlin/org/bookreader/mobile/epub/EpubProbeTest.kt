package org.bookreader.mobile.epub

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.junit.Assert.*
import org.junit.Test

/** Real stable Fragment/WebView, generated books only; Activity recreation is not process death. */
class EpubProbeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun launch(name: String) = ActivityScenario.launch<EpubProbeActivity>(
        Intent(instrumentation.targetContext, EpubProbeActivity::class.java).putExtra("fixture", name))
    private fun waitFor(scenario: ActivityScenario<EpubProbeActivity>, condition: (EpubProbeActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            scenario.onActivity { activity ->
                assertNull("Probe host failed", activity.failure)
                done = condition(activity)
            }
            if (done) return
            SystemClock.sleep(100)
        }
        fail("EPUB probe timed out")
    }
    private fun js(scenario: ActivityScenario<EpubProbeActivity>, chapter: String, expression: String): String {
        var value = "null"
        val completed = CountDownLatch(1)
        scenario.onActivity { activity ->
            fun views(view: View): List<WebView> = if (view is WebView) listOf(view)
                else if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
            val candidates = views(activity.window.decorView).filter { it.url?.contains("/$chapter.xhtml") == true }
            val web = candidates.firstOrNull { it.isShown } ?: candidates.firstOrNull()
            assertNotNull("Loaded chapter WebView", web)
            web!!.evaluateJavascript(expression) { value = it; completed.countDown() }
        }
        assertTrue(completed.await(10, TimeUnit.SECONDS))
        return value
    }
    @Test fun stableNavigatorLoadsEpubTwoAndThreeImagesTocAndRestoresCompleteLocator() {
        for (fixture in listOf("epub2", "epub3")) launch(fixture).use { scenario ->
            waitFor(scenario) { it.loadedPages > 0 }
            assertEquals("true", js(scenario, "one", "document.getElementById('fixture-image').naturalWidth === 2"))
            assertEquals("true", js(scenario, "one", "document.body.innerText.includes('자체 작성')"))
            assertEquals("true", js(scenario, "one", "document.querySelector('meta[http-equiv=\"Content-Security-Policy\"]') !== null"))
            scenario.onActivity { activity ->
                val publication = requireNotNull(activity.publication)
                assertEquals(2, publication.readingOrder.size)
                assertTrue(publication.tableOfContents.isNotEmpty())
                assertTrue(publication.tableOfContents.first().children.isNotEmpty())
                assertTrue(requireNotNull(activity.navigator).go(publication.readingOrder[1], animated = false))
            }
            waitFor(scenario) { it.navigator?.currentLocator?.value?.href?.toString()?.endsWith("two.xhtml") == true && it.loadedPages >= 2 }
            var serialized = ""
            scenario.onActivity { activity ->
                val navigator = requireNotNull(activity.navigator)
                serialized = navigator.currentLocator.value.toJSON().toString()
                val original = JSONObject(serialized)
                val roundTrip = JSONObject(EpubSdkProbe.roundTrip(serialized))
                assertEquals(original.getString("href"), roundTrip.getString("href"))
                assertEquals(original.getJSONObject("locations").toString(), roundTrip.getJSONObject("locations").toString())
                navigator.submitPreferences(EpubPreferences(fontSize = 1.3, theme = Theme.DARK))
            }
            waitFor(scenario) { it.navigator?.currentLocator?.value?.href?.toString()?.endsWith("two.xhtml") == true }
            scenario.recreate()
            waitFor(scenario) { it.loadedPages > 0 && it.navigator?.currentLocator?.value?.href?.toString()?.endsWith("two.xhtml") == true }
            assertEquals("true", js(scenario, "two", "document.body.innerText.includes('two 문단')"))
            scenario.onActivity { activity ->
                val restored = requireNotNull(activity.navigator).currentLocator.value.toJSON()
                assertEquals(JSONObject(serialized).getString("href"), restored.getString("href"))
                assertEquals(JSONObject(serialized).getJSONObject("locations").optDouble("progression", 0.0),
                    restored.getJSONObject("locations").optDouble("progression", 0.0), .03)
            }
        }
    }
    @Test fun activePublicationContentIsRejectedBeforeCreatingAnyNavigator() {
        for (fixture in listOf("active-script", "external-image")) launch(fixture).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 20_000
            var rejected = false
            while (!rejected && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    rejected = it.failure != null
                    assertNull(it.navigator)
                    assertEquals(0, it.loadedPages)
                }
                SystemClock.sleep(100)
            }
            assertTrue("Unsafe publication must be rejected", rejected)
        }
    }
    @Test fun cspBlocksExternalCssResourcesWhileEngineCanNavigateOffline() {
        launch("css-external").use { scenario ->
            waitFor(scenario) { it.loadedPages > 0 }
            assertEquals("true", js(scenario, "one", "document.getElementById('fixture-image').naturalWidth === 2"))
            // Observe CSP enforcement in the actual SDK WebView, not just a transformed string.
            assertEquals("true", js(scenario, "one", "(() => { window.probeViolations = []; document.addEventListener('securitypolicyviolation', e => window.probeViolations.push(e.violatedDirective)); let i = document.createElement('img'); i.src = 'https://example.invalid/bookreader-probe'; document.body.appendChild(i); return true; })()"))
            val deadline = SystemClock.uptimeMillis() + 10_000
            var blocked = false
            while (!blocked && SystemClock.uptimeMillis() < deadline) {
                blocked = js(scenario, "one", "window.probeViolations.includes('img-src')") == "true"
                SystemClock.sleep(100)
            }
            assertTrue("WebView must report the img-src CSP rejection", blocked)
            scenario.onActivity { assertTrue(requireNotNull(it.navigator).goForward(animated = false)) }
        }
    }
}
