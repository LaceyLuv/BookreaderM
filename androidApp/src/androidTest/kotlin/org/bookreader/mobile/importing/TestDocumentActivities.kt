package org.bookreader.mobile.importing

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract

/** Executes in the provider APK's UID so Android itself delivers temporary grants. */
class TestSenderActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixture = intent.getStringExtra("fixture") ?: "utf8"
        val uri = if (fixture == "segment") android.net.Uri.parse("content://org.bookreader.mobile.tests.assets/segment")
            else DocumentsContract.buildDocumentUri("org.bookreader.mobile.tests.documents", fixture)
        val action = intent.getStringExtra("sendAction") ?: Intent.ACTION_VIEW
        startActivity(Intent(action).apply {
            component = ComponentName("org.bookreader.mobile", "org.bookreader.mobile.MainActivity")
            if (action == Intent.ACTION_SEND) { type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri) }
            else setDataAndType(uri, "text/plain")
            clipData = ClipData.newRawUri("TXT fixture", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}

/** Real result delivery and grant from a selectable test document, under the SAF intent contract. */
class TestPickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(intent.action == Intent.ACTION_OPEN_DOCUMENT)
        check(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        val fixture = intent.getStringExtra("fixture") ?: "utf8"
        val uri = if (fixture == "segment") android.net.Uri.parse("content://org.bookreader.mobile.tests.assets/segment")
            else DocumentsContract.buildDocumentUri("org.bookreader.mobile.tests.documents", fixture)
        setResult(RESULT_OK, Intent().apply {
            data = uri
            clipData = ClipData.newRawUri("TXT fixture", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
