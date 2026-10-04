package org.bookreader.mobile.importing;

import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Runs under the provider APK UID; Android itself delivers the temporary read grant. */
public final class TestSenderActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String fixture = getIntent().getStringExtra("fixture");
        if (fixture == null) fixture = "utf8";
        Uri uri = "segment".equals(fixture) ? Uri.parse("content://org.bookreader.mobile.tests.assets/segment")
            : DocumentsContract.buildDocumentUri("org.bookreader.mobile.tests.documents", fixture);
        String action = getIntent().getStringExtra("sendAction");
        if (action == null) action = Intent.ACTION_VIEW;
        Intent incoming = new Intent(action);
        incoming.setComponent(new ComponentName("org.bookreader.mobile", "org.bookreader.mobile.MainActivity"));
        if (Intent.ACTION_SEND.equals(action)) {
            incoming.setType("text/plain");
            incoming.putExtra(Intent.EXTRA_STREAM, uri);
        } else incoming.setDataAndType(uri, "text/plain");
        incoming.setClipData(ClipData.newRawUri("TXT fixture", uri));
        incoming.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(incoming);
        finish();
    }
}
