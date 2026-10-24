package org.bookreader.mobile.reader

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import org.bookreader.mobile.importing.TestPickerActivity

/** Redirects only the document chooser; Android still delivers an actual separate-UID URI grant. */
fun fontPicker(instrumentation: Instrumentation, fixture: String): Instrumentation.ActivityMonitor =
    object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action == Intent.ACTION_OPEN_DOCUMENT) {
                intent.component = ComponentName(instrumentation.context, TestPickerActivity::class.java)
                intent.putExtra("fixture", fixture)
            }
            return null
        }
    }.also(instrumentation::addMonitor)
