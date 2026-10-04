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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import org.bookreader.mobile.reader.TxtReaderController
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.importing.*
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
class LibrarySession(val repository: BookRepository, val close: () -> Unit) {
    var progressRepository: org.bookreader.mobile.repository.ProgressRepository? = null
}

fun interface LibrarySessionFactory {
    fun open(): LibrarySession
}

data class AppUiState(
    val tab: AppTab = AppTab.LIBRARY,
    val library: LibraryState = LibraryState.Loading,
    val query: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val themeError: Boolean = false,
    val importing: ImportUiState = ImportUiState.Idle,
    val selectedBook: Book? = null,
    val deletingBook: Book? = null,
    val reader: TxtReaderController? = null,
    val notice: String? = null,
    val continuePercent: Double? = null,
)

class AppViewModel(
    private val sessions: LibrarySessionFactory,
    private val themes: ThemeStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val management: BookManagement? = null,
    private val readerFactory: ((Book, CoroutineScope) -> TxtReaderController)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var libraryJob: Job? = null
    private val themeMutex = Mutex()
    private var importJob: Job? = null
    private val recoveryDone = kotlinx.coroutines.CompletableDeferred<Unit>()

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
        viewModelScope.launch {
            try {
                val recovered = withContext(ioDispatcher) { management?.recover().orEmpty() }
                val pending = recovered.filterIsInstance<ImportResult.EncodingRequired>().firstOrNull()
                if (pending != null) {
                    val previews = withContext(ioDispatcher) { management?.preview(pending.stagingPath).orEmpty() }
                    mutableState.update { it.copy(importing = ImportUiState.SelectEncoding(pending.jobId, DocumentOrigin.PICKER, previews)) }
                }
                else if (recovered.any { it is ImportResult.Failure }) {
                    mutableState.update { it.copy(importing = ImportUiState.Error("중단된 가져오기를 정리했습니다. 임시 권한이 만료된 파일은 다시 선택해 주세요.")) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(importing = ImportUiState.Error("이전 파일 작업을 복구하지 못했습니다. 다시 시도해 주세요.")) }
            } finally { recoveryDone.complete(Unit) }
            retryLibrary()
        }
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
                recoveryDone.await()
                withContext(ioDispatcher) {
                    val active = sessions.open()
                    try {
                        val loaded = active.repository.loadBooks()
                        val candidate = (loaded as? LibraryState.Content)?.books?.let(::continueBook)
                        val progress = candidate?.currentRevision?.let { revision ->
                            active.progressRepository?.loadProgress(candidate.id, revision)
                        }
                        currentCoroutineContext().ensureActive()
                        mutableState.update { it.copy(continuePercent = (progress as? org.bookreader.mobile.repository.ProgressReadResult.Found)?.progress?.percent) }
                        loaded
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

    fun receiveDocument(document: IncomingDocument) {
        viewModelScope.launch {
            recoveryDone.await()
            if (importJob?.isActive == true || state.value.importing is ImportUiState.SelectEncoding || state.value.importing is ImportUiState.ConfirmShare) {
                mutableState.update { it.copy(notice = "진행 중인 가져오기를 완료하거나 취소한 뒤 새 파일을 선택해 주세요.") }
                return@launch
            }
            if (document.origin == DocumentOrigin.SHARE) {
                mutableState.update { it.copy(importing = ImportUiState.ConfirmShare(document)) }
            } else runImport(document)
        }
    }

    fun rejectDocument() {
        val message = "읽기 권한이 있는 content 파일 하나만 가져올 수 있습니다. 파일 앱에서 TXT를 다시 선택해 주세요."
        mutableState.update {
            if (it.importing is ImportUiState.Working || it.importing is ImportUiState.SelectEncoding)
                it.copy(notice = message)
            else it.copy(importing = ImportUiState.Error(message))
        }
    }
    fun confirmShare() { (state.value.importing as? ImportUiState.ConfirmShare)?.let { runImport(it.document) } }
    fun dismissImport() { mutableState.update { it.copy(importing = ImportUiState.Idle) } }
    fun showBook(book: Book?) { mutableState.update { it.copy(selectedBook = book) } }
    fun requestDelete(book: Book?) { mutableState.update { it.copy(deletingBook = book) } }

    private fun runImport(document: IncomingDocument) = launchImport(document.origin) { service, progress ->
        service.import(document, progress)
    }

    fun selectImportEncoding(encodingId: String) {
        val pending = state.value.importing as? ImportUiState.SelectEncoding ?: return
        launchImport(pending.origin) { service, progress -> service.selectEncoding(pending.jobId, encodingId, progress) }
    }

    private fun launchImport(origin: DocumentOrigin,
        action: suspend (BookManagement, suspend (ImportProgress) -> Unit) -> ImportResult,
    ) {
        if (importJob?.isActive == true) return
        val service = management ?: return
        mutableState.update { it.copy(importing = ImportUiState.Working()) }
        importJob = viewModelScope.launch {
            try {
                recoveryDone.await()
                val result = withContext(ioDispatcher) {
                    action(service) { progress -> mutableState.update { it.copy(importing = ImportUiState.Working(progress)) } }
                }
                acceptImportResult(result, origin)
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(importing = ImportUiState.Error("가져오기를 취소했습니다. 원본과 기존 서재는 유지됩니다.")) }
                throw cancelled
            } catch (failure: ImportFailure) {
                mutableState.update { it.copy(importing = ImportUiState.Error(importErrorMessage(failure.code))) }
            } catch (_: Exception) {
                mutableState.update { it.copy(importing = ImportUiState.Error(importErrorMessage(ImportErrorCode.DATABASE_UNAVAILABLE))) }
            }
        }
    }

    private suspend fun acceptImportResult(result: ImportResult, origin: DocumentOrigin) {
        val previews = if (result is ImportResult.EncodingRequired) withContext(ioDispatcher) { management?.preview(result.stagingPath).orEmpty() } else emptyList()
        mutableState.update { it.copy(importing = when (result) {
            is ImportResult.Success -> if (origin == DocumentOrigin.VIEW) ImportUiState.Idle else ImportUiState.Complete(result)
            is ImportResult.Failure -> ImportUiState.Error(importErrorMessage(result.code))
            is ImportResult.EncodingRequired -> ImportUiState.SelectEncoding(result.jobId, origin, previews)
        }) }
        if (result is ImportResult.Success) {
            refreshLibrary()
            if (origin == DocumentOrigin.VIEW) openBook(result.book)
        }
    }

    fun cancelImport() {
        val pending = state.value.importing as? ImportUiState.SelectEncoding
        if (pending != null) {
            viewModelScope.launch {
                try { withContext(ioDispatcher) { management?.cancelEncoding(pending.jobId) }; dismissImport() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableState.update { it.copy(notice = importErrorMessage(ImportErrorCode.DATABASE_UNAVAILABLE)) } }
            }
        } else importJob?.cancel()
    }

    private val pendingDeletions = mutableSetOf<String>()

    fun confirmDelete() {
        val book = state.value.deletingBook ?: return
        if (!pendingDeletions.add(book.id)) return
        mutableState.update { it.copy(deletingBook = null, selectedBook = null) }
        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    management?.delete(book.id) {
                        // The coordinator has durably marked DELETING and invalidated the epoch.
                        withContext(Dispatchers.Main.immediate) {
                            if (state.value.reader?.book?.id == book.id) closeReader()
                        }
                    }
                }
                if (result is DeleteResult.Failure) mutableState.update { it.copy(importing = ImportUiState.Error(importErrorMessage(result.code))) }
                refreshLibrary()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(importing = ImportUiState.Error(importErrorMessage(ImportErrorCode.DATABASE_UNAVAILABLE))) } }
            finally { pendingDeletions.remove(book.id) }
        }
    }

    private val readerMutex = Mutex()
    private var readerObserver: Job? = null
    fun openBook(book: Book) {
        val factory = readerFactory ?: return
        if (book.availability != BookAvailability.READY || book.id in pendingDeletions) return
        viewModelScope.launch {
            try {
            readerMutex.withLock {
                // A tap queued before confirmation must not reopen this book after deletion starts.
                if (book.id in pendingDeletions) return@withLock
                readerObserver?.cancel()
                state.value.reader?.close()
                mutableState.update { it.copy(reader = null) }
                val reader = factory(book, viewModelScope)
                mutableState.update { it.copy(reader = reader, selectedBook = null) }
                readerObserver = viewModelScope.launch {
                    var marked = false
                    reader.state.collect { readerState ->
                        val availability = readerState.sourceAvailability
                        if (availability != null && !marked) {
                            marked = true
                            try { withContext(ioDispatcher) { management?.markUnavailable(book, availability) }; refreshLibrary() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { mutableState.update { it.copy(notice = "책의 파일 상태를 저장하지 못했습니다. 기존 독서 기록은 유지됩니다.") } }
                        }
                    }
                }
            }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(reader = null, importing = ImportUiState.Error("책을 열지 못했습니다. 원본과 마지막 저장된 독서 기록은 유지됩니다.")) }
                refreshLibrary()
            }
        }
    }
    fun leaveReader() { viewModelScope.launch { closeReader() } }
    private suspend fun closeReader() {
        readerMutex.withLock {
            readerObserver?.cancel()
            try { state.value.reader?.close() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(notice = "독서를 종료하는 동안 오류가 발생했습니다. 마지막 저장된 독서 기록은 유지됩니다.") } }
            finally { mutableState.update { it.copy(reader = null) } }
        }
        refreshLibrary()
    }
    private fun refreshLibrary() { libraryJob?.cancel(); libraryJob = null; retryLibrary() }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
}

fun importErrorMessage(code: ImportErrorCode): String = when (code) {
    ImportErrorCode.PERMISSION_DENIED -> "파일 읽기 권한이 만료되었거나 없습니다. 파일을 다시 선택해 주세요."
    ImportErrorCode.SOURCE_UNAVAILABLE -> "원본 파일을 읽을 수 없습니다. 파일 앱에서 다시 선택해 주세요."
    ImportErrorCode.DISK_FULL -> "앱 저장 공간이 부족합니다. 공간을 확보한 뒤 다시 시도해 주세요."
    ImportErrorCode.TOO_LARGE -> "TXT 파일은 최대 256MiB까지 가져올 수 있습니다."
    ImportErrorCode.ENCODING_REQUIRED -> "텍스트 인코딩을 선택해 주세요."
    ImportErrorCode.INVALID_TEXT, ImportErrorCode.UNSUPPORTED_FORMAT -> "지원하지 않는 파일이거나 텍스트가 손상되었습니다. TXT 파일과 인코딩을 확인해 주세요."
    ImportErrorCode.DATABASE_UNAVAILABLE -> "서재에 저장하지 못했습니다. 기존 책과 독서 기록은 유지됩니다."
    ImportErrorCode.INTERRUPTED -> "가져오기가 중단되었습니다. 원본 파일을 다시 선택해 주세요."
    else -> "파일 작업을 완료하지 못했습니다. 기존 책과 원본은 유지됩니다. 다시 시도해 주세요."
}

fun filterLibraryBooks(books: List<Book>, query: String): List<Book> {
    if (query.isBlank()) return books
    return books.filter { book ->
        book.title.contains(query, ignoreCase = true) ||
            book.author?.contains(query, ignoreCase = true) == true ||
            book.originalDisplayName.contains(query, ignoreCase = true)
    }
}


fun continueBook(books: List<Book>): Book? = books.filter {
    it.availability == BookAvailability.READY && it.lastReadAt != null
}.maxWithOrNull(compareBy<Book> { it.readOrder ?: 0L }.thenBy { it.lastReadAt ?: 0L }.thenBy { it.id })
