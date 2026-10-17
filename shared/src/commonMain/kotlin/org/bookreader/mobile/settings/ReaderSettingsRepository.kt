package org.bookreader.mobile.settings

import kotlinx.coroutines.CancellationException
import androidx.room.useReaderConnection
import androidx.room.deferredTransaction
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.database.BookReaderPreferencesEntity
import org.bookreader.mobile.database.ReaderGlobalSettingsEntity
import org.bookreader.mobile.database.ReaderSettingsRows

data class LoadedReaderSettings(
    val bookId: String,
    val global: ReaderSettings,
    val book: BookReaderPreferences,
    val rows: ReaderSettingsRows,
) {
    val effective: ReaderSettings get() = global.copy(readingMode = book.readingMode ?: global.readingMode)
}

sealed interface ReaderSettingsRead {
    data class Ready(val value: LoadedReaderSettings) : ReaderSettingsRead
    data object Error : ReaderSettingsRead
}
enum class ReaderSettingsWrite { SAVED, CONFLICT, ERROR }

class ReaderSettingsRepository(private val database: BookReaderDatabase) {
    suspend fun load(bookId: String): ReaderSettingsRead = try {
        val rows = database.useReaderConnection { connection ->
            connection.deferredTransaction { database.readerSettingsDao().read(bookId) }
        }
        ReaderSettingsRead.Ready(LoadedReaderSettings(bookId,
            rows.global?.let { ReaderSettingsCodec.readGlobal(it.schemaVersion, it.settingsJson) } ?: ReaderSettings(),
            rows.book?.let { ReaderSettingsCodec.readBook(it.schemaVersion, it.preferencesJson) } ?: BookReaderPreferences(), rows))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { ReaderSettingsRead.Error }

    suspend fun save(expected: LoadedReaderSettings, global: ReaderSettings, book: BookReaderPreferences): ReaderSettingsWrite = try {
        val updated = database.readerSettingsDao().save(expected.bookId, expected.rows,
            ReaderGlobalSettingsEntity(schemaVersion = ReaderSettingsCodec.VERSION, settingsJson = ReaderSettingsCodec.write(global)),
            if (book.readingMode == null) null else BookReaderPreferencesEntity(expected.bookId,
                ReaderSettingsCodec.VERSION, ReaderSettingsCodec.write(book)))
        if (updated) ReaderSettingsWrite.SAVED else ReaderSettingsWrite.CONFLICT
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { ReaderSettingsWrite.ERROR }
}
