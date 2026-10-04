package org.bookreader.mobile.ui

import android.annotation.SuppressLint
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

    // KTX edit returns Unit; the commit Boolean is required to report failed durable writes.
    @SuppressLint("UseKtx")
    override fun write(mode: ThemeMode): Result<Unit> = runCatching {
        check(preferences.edit().putString("app_theme", mode.name).commit())
    }
}

fun androidLibrarySessionFactory(context: Context): LibrarySessionFactory {
    val appContext = context.applicationContext
    return LibrarySessionFactory {
        val database = createAndroidDatabase(appContext)
        try {
            LibrarySession(RoomBookRepository(database), database::close).apply {
                progressRepository = org.bookreader.mobile.repository.RoomProgressRepository(database)
            }
        } catch (failure: Exception) {
            database.close()
            throw failure
        }
    }
}


fun androidReaderFactory(context: Context): (org.bookreader.mobile.model.Book, kotlinx.coroutines.CoroutineScope) -> org.bookreader.mobile.reader.TxtReaderController {
    val app = context.applicationContext
    val progressAccess = AndroidReaderProgressAccess(app)
    return { book, scope ->
        progressAccess.register(book)
        org.bookreader.mobile.reader.TxtReaderController(book,
            java.io.File(java.io.File(app.filesDir, "managed"), book.managedRelativePath),
            java.io.File(app.cacheDir, "txt"), progressAccess, scope)
    }
}

private class AndroidReaderProgressAccess(private val context: Context) : org.bookreader.mobile.reader.ReaderProgressAccess {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val books = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, org.bookreader.mobile.model.Book>()
    fun register(book: org.bookreader.mobile.model.Book) { books[book.id to requireNotNull(book.currentRevision)] = book }
    private suspend fun <T> database(action: suspend (org.bookreader.mobile.database.BookReaderDatabase) -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mutex.lock()
            try {
                val db = createAndroidDatabase(context)
                try { action(db) } finally { db.close() }
            } finally { mutex.unlock() }
        }
    override suspend fun load(bookId: String, revision: String) = database {
        org.bookreader.mobile.repository.RoomProgressRepository(it).loadProgress(bookId, revision)
    }
    override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: org.bookreader.mobile.locator.ContentLocator?) = database {
        // The controller calls this only after the original's full SHA/size and actual layout restoration.
        books[bookId to revision]?.let { book ->
            it.bookDao().restoreVerifiedAvailability(bookId, revision, book.sourceSha256, book.sourceByteSize, System.currentTimeMillis())
        }
        org.bookreader.mobile.repository.RoomProgressWriter(it, System::currentTimeMillis).startReadySession(bookId, revision, restoredLocator)
    }
    override suspend fun save(event: org.bookreader.mobile.repository.ProgressWriteEvent) = database {
        org.bookreader.mobile.repository.RoomProgressWriter(it, System::currentTimeMillis).save(event)
    }
    override suspend fun flush() { mutex.lock(); mutex.unlock() }
}
