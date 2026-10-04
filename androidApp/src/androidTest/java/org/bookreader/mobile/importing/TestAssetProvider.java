package org.bookreader.mobile.importing;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Separate-UID provider returning only a segment of a larger backing file. */
public final class TestAssetProvider extends ContentProvider {
    public static final String TEXT = "선택된 자산 영역의 TXT 본문입니다.\n";
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/plain"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE });
        cursor.addRow(new Object[] { "segment.txt", TEXT.getBytes(StandardCharsets.UTF_8).length });
        return cursor;
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal)
        throws java.io.FileNotFoundException {
        if (!"r".equals(mode)) throw new IllegalArgumentException("Read-only fixture");
        byte[] prefix = { 0x50, 0x4b, 0x03, 0x04 };
        byte[] body = TEXT.getBytes(StandardCharsets.UTF_8);
        File file = new File(getContext().getCacheDir(), "segment-fixture");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(prefix); output.write(body); output.write(new byte[] { 0, 0, 0 });
        } catch (IOException failure) { throw new java.io.FileNotFoundException("Unable to create asset fixture"); }
        return new AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), prefix.length, body.length);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only fixture"); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException("Read-only fixture"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only fixture");
    }
}
