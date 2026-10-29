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

/** Old journals remain TXT; books/progress/bookmarks/settings are not rewritten. */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE import_jobs ADD COLUMN format TEXT NOT NULL DEFAULT 'TXT'")
    }
}

/** Catalog side tables only: every original row, including unknown locators/settings, is preserved. */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS library_book_details (bookId TEXT NOT NULL, metadataEdits INTEGER NOT NULL, metadataScanned INTEGER NOT NULL, readingStatus TEXT, coverPreset INTEGER NOT NULL, PRIMARY KEY(bookId), FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS library_shelves (id TEXT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, PRIMARY KEY(id))")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_library_shelves_nameKey ON library_shelves (nameKey)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS library_memberships (bookId TEXT NOT NULL, shelfId TEXT NOT NULL, PRIMARY KEY(bookId, shelfId), FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(shelfId) REFERENCES library_shelves(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_library_memberships_shelfId ON library_memberships (shelfId)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS library_preferences (id TEXT NOT NULL, sort TEXT NOT NULL, view TEXT NOT NULL, filter TEXT NOT NULL, shelfId TEXT, PRIMARY KEY(id))")
    }
}

/** User TOC edits only; automatic candidates are a disposable derived index. */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS txt_toc_overrides (bookId TEXT NOT NULL, contentRevision TEXT NOT NULL, offset INTEGER NOT NULL, schemaVersion INTEGER NOT NULL, title TEXT, excluded INTEGER NOT NULL, changeId TEXT NOT NULL, PRIMARY KEY(bookId, contentRevision, offset), FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
