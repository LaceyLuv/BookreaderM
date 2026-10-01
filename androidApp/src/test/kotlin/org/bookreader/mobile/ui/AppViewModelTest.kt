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
