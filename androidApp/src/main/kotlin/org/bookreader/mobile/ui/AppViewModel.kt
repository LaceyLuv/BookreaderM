package org.bookreader.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.repository.BookRepository
import org.bookreader.mobile.repository.LibraryErrorCode
import org.bookreader.mobile.repository.LibraryState

enum class AppTab { LIBRARY, SEARCH, SETTINGS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface ThemeStore {
    fun read(): Result<ThemeMode>
    fun write(mode: ThemeMode): Result<Unit>
}

/** Each opened session owns its database; failed opens never replace user data. */
class LibrarySession(val repository: BookRepository, val close: () -> Unit)

fun interface LibrarySessionFactory {
    fun open(): LibrarySession
}

data class AppUiState(
    val tab: AppTab = AppTab.LIBRARY,
    val library: LibraryState = LibraryState.Loading,
    val query: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val themeError: Boolean = false,
)

class AppViewModel(
    private val sessions: LibrarySessionFactory,
    private val themes: ThemeStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var libraryJob: Job? = null
    private val themeMutex = Mutex()

    init {
        // Navigation intentionally has no saved Reader/back-stack state. A new process starts here.
        viewModelScope.launch {
            themeMutex.withLock {
                val stored = withContext(ioDispatcher) { themes.read() }
                mutableState.update {
                    it.copy(theme = stored.getOrDefault(ThemeMode.SYSTEM), themeError = stored.isFailure)
                }
            }
        }
        retryLibrary()
    }

    fun selectTab(tab: AppTab) { mutableState.update { it.copy(tab = tab) } }
    fun updateQuery(query: String) { mutableState.update { it.copy(query = query) } }

    fun selectTheme(mode: ThemeMode) {
        viewModelScope.launch {
            themeMutex.withLock {
                // Only explicit user selections write settings. A failed read never writes defaults.
                val saved = withContext(ioDispatcher) { themes.write(mode) }
                mutableState.update {
                    if (saved.isSuccess) it.copy(theme = mode, themeError = false)
                    else it.copy(themeError = true)
                }
            }
        }
    }

    fun retryLibrary() {
        if (libraryJob?.isActive == true) return
        mutableState.update { it.copy(library = LibraryState.Loading) }
        libraryJob = viewModelScope.launch {
            val loaded = try {
                withContext(ioDispatcher) {
                    val active = sessions.open()
                    try {
                        active.repository.loadBooks()
                    } finally {
                        // One load owns one handle. Cancellation and read failure both close it,
                        // after the query exits; onCleared never races the query with close().
                        active.close()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LibraryState.Error(LibraryErrorCode.DATABASE_UNAVAILABLE)
            }
            mutableState.update { it.copy(library = loaded) }
        }
    }

}

fun filterLibraryBooks(books: List<Book>, query: String): List<Book> {
    if (query.isBlank()) return books
    return books.filter { book ->
        book.title.contains(query, ignoreCase = true) ||
            book.author?.contains(query, ignoreCase = true) == true ||
            book.originalDisplayName.contains(query, ignoreCase = true)
    }
}
