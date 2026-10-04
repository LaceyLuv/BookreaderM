package org.bookreader.mobile.importing;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Real result delivery/grant for fault fixtures under the ACTION_OPEN_DOCUMENT contract. */
public final class TestPickerActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Intent.ACTION_OPEN_DOCUMENT.equals(getIntent().getAction()) ||
            !getIntent().hasCategory(Intent.CATEGORY_OPENABLE)) {
            throw new IllegalStateException("Expected SAF picker request");
        }
        String fixture = getIntent().getStringExtra("fixture");
        if (fixture == null) fixture = "utf8";
        Uri uri = "segment".equals(fixture) ? Uri.parse("content://org.bookreader.mobile.tests.assets/segment")
            : DocumentsContract.buildDocumentUri("org.bookreader.mobile.tests.documents", fixture);
        Intent result = new Intent();
        result.setData(uri);
        result.setClipData(ClipData.newRawUri("TXT fixture", uri));
        result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        setResult(RESULT_OK, result);
        finish();
    }
}
