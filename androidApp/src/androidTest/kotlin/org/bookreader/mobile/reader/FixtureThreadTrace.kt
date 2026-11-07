package org.bookreader.mobile.reader

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Timer
import java.util.TimerTask
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Bounded code-only diagnostics for a stalled fixture run; never changes its result or timeout. */
class FixtureThreadTrace : TestWatcher() {
    private var timer: Timer? = null
    override fun starting(description: Description) {
        if (InstrumentationRegistry.getArguments().getString("captureFixtureScreenshots") != "true") return
        val owner = Timer("bookreader-fixture-trace", true)
        timer = owner
        owner.scheduleAtFixedRate(object : TimerTask() {
            private var samples = 0
            override fun run() {
                val runtime = Runtime.getRuntime()
                Log.i("BookReaderFixtureTrace", "${description.className}.${description.methodName}: sample=${++samples}, heapUsed=${runtime.totalMemory() - runtime.freeMemory()}")
                // UTP streams logcat to the host even if the test process never finishes.
                // Only code frames and the main/instrumentation thread identities are recorded.
                Thread.getAllStackTraces().entries.filter { (thread, _) ->
                    thread.name == "main" || thread.name.startsWith("Instr:")
                }.forEach { (thread, frames) ->
                    Log.i("BookReaderFixtureTrace", "thread=${thread.name}, state=${thread.state}")
                    frames.take(80).forEach { Log.i("BookReaderFixtureTrace", "  at $it") }
                }
                if (samples >= 3) owner.cancel()
            }
        }, 60_000, 60_000)
    }
    override fun finished(description: Description) { timer?.cancel(); timer = null }
}
