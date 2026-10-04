package org.bookreader.mobile.reader

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal object ReaderFixtureScreenshot {
    fun capture(name: String) {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("captureFixtureScreenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(arguments.getString("additionalTestOutputDir")
            ?: File(instrumentation.targetContext.getExternalFilesDir(null), "test-evidence").absolutePath)
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, name).outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally { bitmap.recycle() }
    }
}
