package org.bookreader.mobile.settings

import androidx.room.useWriterConnection
import androidx.room.execSQL
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import org.bookreader.mobile.database.*
import org.bookreader.mobile.locator.*
import kotlin.test.*

class ReaderSettingsRepositoryTest {
    @Test fun settingsRoundTripInheritAndRejectStaleEditorsWithoutChangingProgress() = fixture { db ->
        val repository = ReaderSettingsRepository(db)
        val first = assertIs<ReaderSettingsRead.Ready>(repository.load("a")).value
        assertEquals(ReaderSettings(), first.effective)
        val book = db.bookDao().findBook("a")
        val progress = db.progressDao().findProgress("a", "revision")
        val global = ReaderSettings(font = ReaderFont.SERIF, fontSizeSp = 24f, theme = ReaderTheme.SEPIA)
        assertEquals(ReaderSettingsWrite.SAVED, repository.save(first, global, BookReaderPreferences(TxtReadingMode.PAGES)))
        val saved = assertIs<ReaderSettingsRead.Ready>(repository.load("a")).value
        assertEquals(global.copy(readingMode = TxtReadingMode.PAGES), saved.effective)
        assertEquals(ReaderSettingsWrite.CONFLICT, repository.save(first, ReaderSettings(), BookReaderPreferences()))
        assertEquals(saved, assertIs<ReaderSettingsRead.Ready>(repository.load("a")).value)
        assertEquals(ReaderSettingsWrite.SAVED, repository.save(saved, global, BookReaderPreferences()))
        assertEquals(global, assertIs<ReaderSettingsRead.Ready>(repository.load("a")).value.effective)
        assertNull(db.readerSettingsDao().book("a"))
        assertEquals(book, db.bookDao().findBook("a"))
        assertEquals(progress, db.progressDao().findProgress("a", "revision"))
    }

    @Test fun malformedFutureAndOutOfRangeSettingsStayByteExactAndPreventStaleSave() = fixture { db ->
        val repository = ReaderSettingsRepository(db)
        val before = assertIs<ReaderSettingsRead.Ready>(repository.load("a")).value
        val progress = db.progressDao().findProgress("a", "revision")
        for (raw in listOf(ReaderGlobalSettingsEntity(schemaVersion = 2, settingsJson = "{}"),
            ReaderGlobalSettingsEntity(schemaVersion = 1, settingsJson = "{bad"),
            ReaderGlobalSettingsEntity(schemaVersion = 1, settingsJson = "{\"fontSizeSp\":999}"),
            ReaderGlobalSettingsEntity(schemaVersion = 1, settingsJson = "{\"futureField\":true}"))) {
            db.readerSettingsDao().putGlobal(raw)
            assertEquals(ReaderSettingsRead.Error, repository.load("a"))
            assertEquals(ReaderSettingsWrite.CONFLICT, repository.save(before, ReaderSettings(), BookReaderPreferences()))
            assertEquals(raw, db.readerSettingsDao().global())
            assertEquals(progress, db.progressDao().findProgress("a", "revision"))
        }
    }

    @Test fun deletionCascadesOnlyBookOverridesAndLateSaveCannotRecreateBook() = fixture { db ->
        val repository = ReaderSettingsRepository(db)
        val before = assertIs<ReaderSettingsRead.Ready>(repository.load("a")).value
        assertEquals(ReaderSettingsWrite.SAVED, repository.save(before, ReaderSettings(), BookReaderPreferences(TxtReadingMode.PAGES)))
        val saved = assertIs<ReaderSettingsRead.Ready>(repository.load("a")).value
        db.useWriterConnection { it.execSQL("DELETE FROM books WHERE id = 'a'") }
        assertNull(db.readerSettingsDao().book("a"))
        assertNotNull(db.readerSettingsDao().global())
        assertEquals(ReaderSettingsWrite.CONFLICT, repository.save(saved, ReaderSettings(fontSizeSp = 30f), BookReaderPreferences()))
        assertNull(db.bookDao().findBook("a"))
        assertEquals(saved.rows.global, db.readerSettingsDao().global())
    }

    private fun fixture(block: suspend (BookReaderDatabase) -> Unit) = runBlocking {
        val folder = Files.createTempDirectory("reader-settings-")
        val db = createJvmDatabase(folder.resolve("reader.db"))
        try {
            db.bookDao().insertBook(BookEntity("a", "TXT", "Own fixture", "a.txt", "books/a/original", "a".repeat(64),
                100, 1, 1, 1, currentRevision = "revision", encodingId = "utf-8", normalizationVersion = 1))
            db.progressDao().insertProgress(ReadingProgressEntity("a", "revision", "txt", 1,
                LocatorCodec().encode(TxtLocator("revision", TxtLocatorPayload(25))), 25.0, 1, 1, 1))
            block(db)
        } finally { db.close(); Files.walk(folder).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
