package org.bookreader.mobile.ui

import android.content.Context
import android.content.SharedPreferences
import org.bookreader.mobile.database.createAndroidDatabase
import org.bookreader.mobile.repository.RoomBookRepository

class AndroidThemeStore(private val preferences: SharedPreferences) : ThemeStore {
    override fun read(): Result<ThemeMode> = runCatching {
        val stored = preferences.getString("app_theme", null) ?: return@runCatching ThemeMode.SYSTEM
        ThemeMode.entries.firstOrNull { it.name == stored }
            ?: error("Unsupported stored theme")
    }

    override fun write(mode: ThemeMode): Result<Unit> = runCatching {
        check(preferences.edit().putString("app_theme", mode.name).commit())
    }
}

fun androidLibrarySessionFactory(context: Context): LibrarySessionFactory {
    val appContext = context.applicationContext
    return LibrarySessionFactory {
        val database = createAndroidDatabase(appContext)
        try {
            LibrarySession(RoomBookRepository(database), database::close)
        } catch (failure: Exception) {
            database.close()
            throw failure
        }
    }
}
