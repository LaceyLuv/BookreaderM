package org.bookreader.mobile.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Additive only: version-1 Book/progress rows and their identities remain untouched. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS import_jobs (id TEXT NOT NULL, proposedBookId TEXT NOT NULL, stagingPath TEXT NOT NULL, finalRelativePath TEXT NOT NULL, state TEXT NOT NULL, displayName TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, sourceUri TEXT, expectedBytes INTEGER, bytesCopied INTEGER NOT NULL, sourceSha256 TEXT, encodingId TEXT, normalizationVersion INTEGER, committedBookId TEXT, failureCode TEXT, PRIMARY KEY(id))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS app_metadata (`key` TEXT NOT NULL, value INTEGER NOT NULL, PRIMARY KEY(`key`))")
        connection.execSQL("INSERT INTO app_metadata (`key`, value) SELECT 'read_order', COALESCE(MAX(readOrder), 0) FROM books")
    }
}
