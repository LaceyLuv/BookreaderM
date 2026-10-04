package org.bookreader.mobile.importing

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/** Debug-only security fixture: the importer must reject providers sharing its own UID. */
class PrivateFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = error("Self provider must never be queried")
    override fun getType(uri: Uri) = "text/plain"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read-only test fixture")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Read-only test fixture")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Read-only test fixture")
}
