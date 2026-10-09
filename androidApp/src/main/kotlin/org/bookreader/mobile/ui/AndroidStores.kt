package org.bookreader.mobile.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import org.bookreader.mobile.database.AndroidDatabaseOwner
import org.bookreader.mobile.repository.DatabaseFailureDiagnostic
import org.bookreader.mobile.repository.DatabaseFailureStage
import org.bookreader.mobile.repository.failureTypes
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
        val database = AndroidDatabaseOwner.borrow(appContext)
        LibrarySession(RoomBookRepository(database), close = {}).apply {
            progressRepository = org.bookreader.mobile.repository.RoomProgressRepository(database)
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
    @Volatile private var lastFailure: DatabaseFailureDiagnostic? = null
    private val onDatabaseFailure: (DatabaseFailureStage, Exception) -> Unit = { stage, failure ->
        lastFailure = DatabaseFailureDiagnostic(stage, failureTypes(failure))
    }
    override fun lastDatabaseFailure() = lastFailure
    fun register(book: org.bookreader.mobile.model.Book) { books[book.id to requireNotNull(book.currentRevision)] = book }
    private suspend fun <T> database(stage: DatabaseFailureStage,
        action: suspend (org.bookreader.mobile.database.BookReaderDatabase) -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            mutex.lock()
            try {
                lastFailure = null
                var failureStage = DatabaseFailureStage.OPEN_DATABASE
                try {
                    val db = AndroidDatabaseOwner.borrow(context)
                    failureStage = stage
                    action(db)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    onDatabaseFailure(failureStage, failure)
                    throw failure
                }
            } finally { mutex.unlock() }
        }
    override suspend fun load(bookId: String, revision: String) = database(DatabaseFailureStage.READ_PROGRESS) {
        org.bookreader.mobile.repository.RoomProgressRepository(it, onDatabaseFailure = onDatabaseFailure).loadProgress(bookId, revision)
    }
    override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: org.bookreader.mobile.locator.ContentLocator?) = database(DatabaseFailureStage.VERIFY_AVAILABILITY) {
        // The controller calls this only after the original's full SHA/size and actual layout restoration.
        books[bookId to revision]?.let { book ->
            it.bookDao().restoreVerifiedAvailability(bookId, revision, book.sourceSha256, book.sourceByteSize, System.currentTimeMillis())
        }
        org.bookreader.mobile.repository.RoomProgressWriter(it, onDatabaseFailure, System::currentTimeMillis).startReadySession(bookId, revision, restoredLocator)
    }
    override suspend fun save(event: org.bookreader.mobile.repository.ProgressWriteEvent) = database(DatabaseFailureStage.SAVE_COMMIT) {
        org.bookreader.mobile.repository.RoomProgressWriter(it, onDatabaseFailure, System::currentTimeMillis).save(event)
    }
    override suspend fun flush() { mutex.lock(); mutex.unlock() }
}
