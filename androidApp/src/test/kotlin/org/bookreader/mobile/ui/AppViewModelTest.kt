package org.bookreader.mobile.ui

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.bookreader.mobile.importing.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.repository.BookRepository
import org.bookreader.mobile.repository.LibraryErrorCode
import org.bookreader.mobile.repository.LibraryState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ownedModels = ViewModelStore()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        ownedModels.clear()
        Dispatchers.resetMain()
    }

    @Test fun newModelStartsAtLibraryAndLoadingUntilQueryCompletes() = runTest(dispatcher) {
        val query = CompletableDeferred<LibraryState>()
        val themes = MemoryThemes(ThemeMode.DARK)
        val model = model(themes) { query.await() }
        assertEquals(AppTab.LIBRARY, model.state.value.tab)
        assertEquals(LibraryState.Loading, model.state.value.library)
        runCurrent()
        assertEquals(LibraryState.Loading, model.state.value.library)
        query.complete(LibraryState.Empty)
        advanceUntilIdle()
        assertEquals(LibraryState.Empty, model.state.value.library)
        assertEquals(ThemeMode.DARK, model.state.value.theme)
        assertTrue(themes.writes.isEmpty())
    }

    @Test fun readFailureRemainsErrorAndRetryCanLoadEmpty() = runTest(dispatcher) {
        var attempts = 0
        var closes = 0
        val model = model(onClose = { closes++ }) {
            attempts++
            if (attempts == 1) throw IllegalStateException("private database path")
            LibraryState.Empty
        }
        advanceUntilIdle()
        assertEquals(LibraryState.Error(LibraryErrorCode.DATABASE_UNAVAILABLE), model.state.value.library)
        assertEquals(1, closes)
        model.retryLibrary()
        assertEquals(LibraryState.Loading, model.state.value.library)
        advanceUntilIdle()
        assertEquals(LibraryState.Empty, model.state.value.library)
        assertEquals(2, closes)
    }

    @Test fun databaseOpenFailureCanRetryWithoutCrash() = runTest(dispatcher) {
        var opens = 0
        val factory = LibrarySessionFactory {
            opens++
            if (opens == 1) throw IllegalStateException("schema open failed")
            LibrarySession(repository { LibraryState.Empty }) { }
        }
        val model = AppViewModel(factory, MemoryThemes(), dispatcher)
        own(model)
        advanceUntilIdle()
        assertTrue(model.state.value.library is LibraryState.Error)
        model.retryLibrary()
        advanceUntilIdle()
        assertEquals(LibraryState.Empty, model.state.value.library)
        assertEquals(2, opens)
    }

    @Test fun invalidRecordErrorFromRepositoryIsPreserved() = runTest(dispatcher) {
        val failure = LibraryState.Error(LibraryErrorCode.INVALID_RECORD)
        val model = model { failure }
        advanceUntilIdle()
        assertEquals(failure, model.state.value.library)
    }

    @Test fun clearingViewModelCancelsQueryAndClosesOwnedDatabase() = runTest(dispatcher) {
        var closes = 0
        model(onClose = { closes++ }) { awaitCancellation() }
        runCurrent()
        assertEquals(0, closes)
        ownedModels.clear()
        advanceUntilIdle()
        assertEquals(1, closes)
    }

    @Test fun tabsAndQueryPersistWithinModelButNewModelStartsInLibrary() = runTest(dispatcher) {
        val model = model { LibraryState.Empty }
        advanceUntilIdle()
        model.selectTab(AppTab.SEARCH)
        model.updateQuery("literal_%")
        model.selectTab(AppTab.SETTINGS)
        model.selectTab(AppTab.SEARCH)
        assertEquals("literal_%", model.state.value.query)
        assertEquals(AppTab.SEARCH, model.state.value.tab)
        val newProcessModel = model { LibraryState.Empty }
        assertEquals(AppTab.LIBRARY, newProcessModel.state.value.tab)
        assertEquals("", newProcessModel.state.value.query)
        advanceUntilIdle()
    }

    @Test fun failedThemeReadUsesDisplayDefaultWithoutWritingUntilUserSelects() = runTest(dispatcher) {
        val themes = MemoryThemes().apply { readFailure = true }
        val model = model(themes) { LibraryState.Empty }
        advanceUntilIdle()
        assertTrue(model.state.value.themeError)
        assertEquals(ThemeMode.SYSTEM, model.state.value.theme)
        assertTrue(themes.writes.isEmpty())
        model.selectTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(listOf(ThemeMode.DARK), themes.writes)
        assertEquals(ThemeMode.DARK, model.state.value.theme)
        assertFalse(model.state.value.themeError)
    }

    @Test fun failedThemeCommitPreservesPreviousDisplayAndReportsError() = runTest(dispatcher) {
        val themes = MemoryThemes(ThemeMode.LIGHT).apply { writeFailure = true }
        val model = model(themes) { LibraryState.Empty }
        advanceUntilIdle()
        model.selectTheme(ThemeMode.DARK)
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, model.state.value.theme)
        assertTrue(model.state.value.themeError)
    }

    @Test fun deleteMarksBeforeStoppingReaderAndRejectsAlreadyQueuedReopen() = runTest(dispatcher) {
        val fixture = importBook()
        val events = mutableListOf<String>()
        val enteredDelete = CompletableDeferred<Unit>()
        val permitStop = CompletableDeferred<Unit>()
        val permitRemove = CompletableDeferred<Unit>()
        var removed = false
        var factoryCalls = 0
        val management = MemoryManagement().apply {
            deleteAction = { stopReading ->
                events += "tombstone"
                enteredDelete.complete(Unit)
                permitStop.await()
                stopReading()
                events += "stopped"
                permitRemove.await()
                removed = true
                events += "removed"
                DeleteResult.Deleted
            }
        }
        val directory = java.nio.file.Files.createTempDirectory("delete-reader-race").toFile()
        val progress = object : org.bookreader.mobile.reader.ReaderProgressAccess {
            override suspend fun load(bookId: String, revision: String): org.bookreader.mobile.repository.ProgressReadResult = awaitCancellation()
            override suspend fun startReadySession(bookId: String, revision: String, restoredLocator: org.bookreader.mobile.locator.ContentLocator?): org.bookreader.mobile.repository.ReadySessionResult = error("Not restored")
            override suspend fun save(event: org.bookreader.mobile.repository.ProgressWriteEvent): org.bookreader.mobile.repository.ProgressWriteResult = error("Not ready")
            override suspend fun flush() { events += "flush" }
        }
        val model = AppViewModel(LibrarySessionFactory {
            LibrarySession(repository { if (removed) LibraryState.Empty else LibraryState.Content(listOf(fixture)) }) {}
        }, MemoryThemes(), dispatcher, management, readerFactory = { book, scope ->
            factoryCalls++
            org.bookreader.mobile.reader.TxtReaderController(book, java.io.File(directory, "original"), directory, progress, scope)
        }).also(::own)
        try {
            advanceUntilIdle()
            model.openBook(fixture)
            runCurrent()
            assertEquals(1, factoryCalls)
            assertTrue(model.state.value.reader != null)
            // Queue a stale library tap first, then synchronously start deletion before it executes.
            model.openBook(fixture)
            model.requestDelete(fixture)
            model.confirmDelete()
            runCurrent()
            assertTrue(enteredDelete.isCompleted)
            assertEquals(listOf("tombstone"), events)
            assertEquals(1, factoryCalls)
            model.openBook(fixture)
            runCurrent()
            assertEquals(1, factoryCalls)
            permitStop.complete(Unit)
            runCurrent()
            assertEquals(listOf("tombstone", "flush", "stopped"), events)
            assertEquals(null, model.state.value.reader)
            permitRemove.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("tombstone", "flush", "stopped", "removed"), events)
            assertEquals(LibraryState.Empty, model.state.value.library)
        } finally { directory.deleteRecursively() }
    }

    @Test fun readerFactoryFailureShowsRecoverableErrorAndKeepsLibrary() = runTest(dispatcher) {
        val fixture = importBook()
        val expected = LibraryState.Content(listOf(fixture))
        val model = AppViewModel(LibrarySessionFactory { LibrarySession(repository { expected }) {} },
            MemoryThemes(), dispatcher, readerFactory = { _, _ -> error("Reader cannot open") }).also(::own)
        advanceUntilIdle()
        model.openBook(fixture)
        advanceUntilIdle()
        assertEquals(null, model.state.value.reader)
        assertEquals(expected, model.state.value.library)
        assertTrue(model.state.value.importing is ImportUiState.Error)
    }

    @Test fun recoveredEncodingChoiceResumesPrivateJobAndRefreshesLibraryAfterCommit() = runTest(dispatcher) {
        val fixture = importBook()
        var loaded = 0
        val management = MemoryManagement().apply {
            recovered = listOf(ImportResult.EncodingRequired("job-1", "imports/job-1.part", "legacy.txt"))
            selectedResult = ImportResult.Success(fixture, duplicate = false)
        }
        val model = AppViewModel(LibrarySessionFactory {
            LibrarySession(repository { loaded++; if (management.selections.isEmpty()) LibraryState.Empty else LibraryState.Content(listOf(fixture)) }) {}
        }, MemoryThemes(), dispatcher, management).also(::own)
        advanceUntilIdle()
        assertEquals("job-1", (model.state.value.importing as ImportUiState.SelectEncoding).jobId)
        model.selectImportEncoding("euc-kr")
        advanceUntilIdle()
        assertEquals(listOf("job-1" to "euc-kr"), management.selections)
        assertTrue(model.state.value.importing is ImportUiState.Complete)
        assertEquals(LibraryState.Content(listOf(fixture)), model.state.value.library)
        assertEquals(0, management.sourceImports)
        assertEquals(2, loaded)
    }

    @Test fun cancelEncodingDatabaseFailureKeepsPendingJobAndShowsErrorWithoutCrashing() = runTest(dispatcher) {
        val management = MemoryManagement().apply {
            recovered = listOf(ImportResult.EncodingRequired("job-1", "imports/job-1.part", "legacy.txt"))
            cancelFails = true
        }
        val model = AppViewModel(LibrarySessionFactory { LibrarySession(repository { LibraryState.Empty }) {} },
            MemoryThemes(), dispatcher, management).also(::own)
        advanceUntilIdle()
        model.cancelImport()
        advanceUntilIdle()
        assertTrue(model.state.value.importing is ImportUiState.SelectEncoding)
        assertTrue(model.state.value.notice != null)
        model.rejectDocument()
        assertTrue(model.state.value.importing is ImportUiState.SelectEncoding)
    }

    @Test fun deleteDatabaseFailurePreservesLibraryAndShowsError() = runTest(dispatcher) {
        val fixture = importBook()
        val management = MemoryManagement().apply { deleteFails = true }
        val expected = LibraryState.Content(listOf(fixture))
        val model = AppViewModel(LibrarySessionFactory { LibrarySession(repository { expected }) {} },
            MemoryThemes(), dispatcher, management).also(::own)
        advanceUntilIdle()
        model.requestDelete(fixture)
        model.confirmDelete()
        advanceUntilIdle()
        assertEquals(expected, model.state.value.library)
        assertTrue(model.state.value.importing is ImportUiState.Error)
    }

    private fun importBook() = Book(id = "book-1", format = BookFormat.TXT, title = "fixture",
        originalDisplayName = "fixture.txt", managedRelativePath = "books/book-1/original",
        sourceSha256 = "a".repeat(64), sourceByteSize = 3, currentRevision = "revision-1",
        addedAt = 1, createdAt = 1, updatedAt = 1)

    private class MemoryManagement : BookManagement {
        var recovered: List<ImportResult> = emptyList()
        var selectedResult: ImportResult = ImportResult.Failure(ImportErrorCode.INVALID_TEXT)
        val selections = mutableListOf<Pair<String, String>>()
        var sourceImports = 0
        var cancelFails = false
        var deleteFails = false
        var deleteAction: (suspend (suspend () -> Unit) -> DeleteResult)? = null
        override suspend fun recover() = recovered
        override suspend fun import(document: IncomingDocument, onProgress: suspend (ImportProgress) -> Unit): ImportResult {
            sourceImports++
            return ImportResult.Failure(ImportErrorCode.SOURCE_UNAVAILABLE)
        }
        override suspend fun selectEncoding(jobId: String, encodingId: String, onProgress: suspend (ImportProgress) -> Unit): ImportResult {
            selections += jobId to encodingId
            return selectedResult
        }
        override suspend fun preview(stagingPath: String) = emptyList<org.bookreader.mobile.encoding.TxtEncodingPreview>()
        override suspend fun cancelEncoding(jobId: String) { if (cancelFails) error("DB unavailable") }
        override suspend fun markUnavailable(book: Book, availability: BookAvailability) = Unit
        override suspend fun delete(bookId: String, stopReading: suspend () -> Unit): DeleteResult {
            if (deleteFails) error("DB unavailable")
            return deleteAction?.invoke(stopReading) ?: DeleteResult.Deleted
        }
    }

    private fun model(
        themes: ThemeStore = MemoryThemes(),
        onClose: () -> Unit = {},
        load: suspend () -> LibraryState,
    ): AppViewModel = AppViewModel(
        LibrarySessionFactory { LibrarySession(repository(load), onClose) }, themes, dispatcher,
    ).also(::own)

    private fun own(model: AppViewModel) {
        ownedModels.put("model_${System.identityHashCode(model)}", model)
    }

    private fun repository(load: suspend () -> LibraryState) = object : BookRepository {
        override suspend fun loadBooks(): LibraryState = load()
    }

    private class MemoryThemes(var value: ThemeMode = ThemeMode.SYSTEM) : ThemeStore {
        val writes = mutableListOf<ThemeMode>()
        var readFailure = false
        var writeFailure = false
        override fun read(): Result<ThemeMode> = if (readFailure) Result.failure(Exception()) else Result.success(value)
        override fun write(mode: ThemeMode): Result<Unit> {
            writes += mode
            if (writeFailure) return Result.failure(Exception())
            value = mode
            return Result.success(Unit)
        }
    }
}
