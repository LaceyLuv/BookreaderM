package org.bookreader.mobile.importing

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Real provider whose selected text is a segment inside a larger backing file. */
class TestAssetProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/plain"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf("segment.txt", TEXT.toByteArray().size))
        }
    override fun openAssetFile(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor {
        check(mode == "r")
        val prefix = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val body = TEXT.toByteArray()
        val file = File(requireNotNull(context).cacheDir, "segment-fixture")
        file.writeBytes(prefix + body + byteArrayOf(0, 0, 0))
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), prefix.size.toLong(), body.size.toLong())
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only fixture")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Read-only fixture")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Read-only fixture")
    companion object { const val TEXT = "선택된 자산 영역의 TXT 본문입니다.\n" }
}
