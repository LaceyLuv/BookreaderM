package org.bookreader.mobile.epub

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONTokener

/** SDK page-loaded callbacks also include preloaded chapters; wait for this document instead. */
internal fun <A : Activity> evaluateLoadedChapter(
    scenario: ActivityScenario<A>,
    chapter: String,
    expression: String,
    hostReady: (A) -> Boolean = { true },
): String {
    val deadline = SystemClock.uptimeMillis() + 10_000
    var lastState = "no visible chapter WebView"
    fun webViews(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { webViews(view.getChildAt(it)) }
        else -> emptyList()
    }
    while (SystemClock.uptimeMillis() < deadline) {
        val completed = CountDownLatch(1)
        var result: String? = null
        scenario.onActivity { activity ->
            val web = webViews(activity.window.decorView).firstOrNull {
                it.isAttachedToWindow && it.isShown && it.url?.endsWith("/$chapter.xhtml") == true
            }
            if (web == null || !hostReady(activity)) {
                lastState = if (web == null) "no visible chapter WebView" else "host restoring"
                completed.countDown()
            } else {
                web.evaluateJavascript("""
                    (() => {
                        if (document.readyState !== 'complete' || !document.body ||
                            !location.pathname.endsWith('/$chapter.xhtml')) return JSON.stringify({ready:false});
                        return JSON.stringify({ready:true, value:JSON.stringify(($expression))});
                    })()
                """.trimIndent()) {
                    if (web.isAttachedToWindow && web.isShown && hostReady(activity)) result = it
                    completed.countDown()
                }
            }
        }
        check(completed.await((deadline - SystemClock.uptimeMillis()).coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
            "Chapter $chapter JavaScript callback timed out"
        }
        result?.let {
            // A null/invalid result after readiness is an evaluation failure, never a retry/pass.
            val envelope = JSONObject(JSONTokener(it).nextValue() as? String
                ?: error("Chapter $chapter JavaScript evaluation failed: $it"))
            if (envelope.getBoolean("ready")) return envelope.getString("value")
            lastState = "document loading"
        }
        SystemClock.sleep(100)
    }
    error("Chapter $chapter did not become ready within 10 seconds: $lastState")
}
