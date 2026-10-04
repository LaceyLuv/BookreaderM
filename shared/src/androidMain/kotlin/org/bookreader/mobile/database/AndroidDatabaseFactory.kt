package org.bookreader.mobile.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Caller owns this handle. Database lives in private durable storage, never cacheDir. */
fun createAndroidDatabase(context: Context): BookReaderDatabase {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath("bookreader.db")
    return Room.databaseBuilder<BookReaderDatabase>(
        context = appContext,
        name = databaseFile.absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
