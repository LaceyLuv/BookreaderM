package org.bookreader.mobile.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers

/** JVM target exists to test the same Room schema against real file-backed bundled SQLite. */
fun createJvmDatabase(databasePath: Path): BookReaderDatabase =
    Room.databaseBuilder<BookReaderDatabase>(name = databasePath.toAbsolutePath().toString())
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
