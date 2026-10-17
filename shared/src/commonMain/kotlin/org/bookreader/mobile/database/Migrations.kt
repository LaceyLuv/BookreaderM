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

/** Settings are additive; existing books, interpretation revisions and locators are untouched. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS reader_global_settings (id TEXT NOT NULL, schemaVersion INTEGER NOT NULL, settingsJson TEXT NOT NULL, PRIMARY KEY(id))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS book_reader_preferences (bookId TEXT NOT NULL, schemaVersion INTEGER NOT NULL, preferencesJson TEXT NOT NULL, PRIMARY KEY(bookId), FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS bookmarks (id TEXT NOT NULL, bookId TEXT NOT NULL, contentRevision TEXT NOT NULL, locatorType TEXT NOT NULL, locatorVersion INTEGER NOT NULL, locatorJson TEXT NOT NULL, label TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId_contentRevision_createdAt_id ON bookmarks (bookId, contentRevision, createdAt, id)")
    }
}
