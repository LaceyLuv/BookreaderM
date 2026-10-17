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

/** A scoped repository lease; the factory decides whether close releases an owned or borrowed handle. */
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
    val epubReader: org.bookreader.mobile.epub.EpubReaderController? = null,
    val comicReader: org.bookreader.mobile.comic.ComicReaderController? = null,
    val notice: String? = null,
    val continuePercent: Double? = null,
    val encodingChange: BookEncodingUi? = null,
)

class AppViewModel(
    private val sessions: LibrarySessionFactory,
    private val themes: ThemeStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val management: BookManagement? = null,
    private val readerFactory: ((Book, CoroutineScope) -> TxtReaderController)? = null,
    private val comicFactory: ((Book, CoroutineScope) -> org.bookreader.mobile.comic.ComicReaderController)? = null,
    private val epubFactory: ((Book, CoroutineScope) -> org.bookreader.mobile.epub.EpubReaderController)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()
    private var libraryJob: Job? = null
    private val themeMutex = Mutex()
    private var importJob: Job? = null
    private var encodingJob: Job? = null
    private val pendingInterpretations = mutableSetOf<String>()
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
                        // Release the lease after its query exits. Android borrows the app-owned pool;
                        // independent test factories may own and close their database instead.
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

    fun requestBookEncoding(book: Book) {
        val service = management ?: return
        if (book.format != org.bookreader.mobile.model.BookFormat.TXT || book.availability != BookAvailability.READY ||
            encodingJob?.isActive == true) return
        mutableState.update { it.copy(selectedBook = null, encodingChange = BookEncodingUi(book)) }
        encodingJob = viewModelScope.launch {
            try {
                val previews = withContext(ioDispatcher) { service.preview(book.managedRelativePath) }
                mutableState.update { it.copy(encodingChange = BookEncodingUi(book, previews, loading = false)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(encodingChange = BookEncodingUi(book,
                loading = false, error = "미리보기를 읽지 못했습니다. 원본과 기록은 보존됩니다.")) } }
        }
    }

    fun dismissBookEncoding() {
        if (state.value.encodingChange?.committing == true) return
        encodingJob?.cancel()
        mutableState.update { it.copy(encodingChange = null) }
    }

    fun changeBookEncoding(encoding: String) {
        val service = management ?: return
        val pending = state.value.encodingChange ?: return
        if (pending.loading || pending.working || encodingJob?.isActive == true) return
        pendingInterpretations += pending.book.id
        mutableState.update { it.copy(encodingChange = pending.copy(working = true, error = null)) }
        encodingJob = viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    service.changeEncoding(pending.book, encoding, stopReading = {
                        withContext(Dispatchers.Main.immediate) {
                            readerMutex.withLock {
                                if (state.value.reader?.book?.id == pending.book.id) {
                                    readerObserver?.cancel()
                                    state.value.reader?.close()
                                    mutableState.update { it.copy(reader = null) }
                                }
                            }
                        }
                    }, onCommitting = {
                        withContext(Dispatchers.Main.immediate) { mutableState.update {
                            it.copy(encodingChange = it.encodingChange?.copy(committing = true))
                        } }
                    })
                }
                when (result) {
                    is EncodingChangeResult.Changed -> {
                        mutableState.update { it.copy(encodingChange = null, notice =
                            if (result.hasExistingProgress) "인코딩을 변경했습니다. 이 해석의 기존 독서 위치를 복원할 수 있습니다."
                            else "인코딩을 변경했습니다. 새 해석은 처음부터 읽습니다. 이전 진행도와 북마크는 보존됩니다.") }
                        refreshLibrary()
                    }
                    is EncodingChangeResult.Failure -> mutableState.update { it.copy(encodingChange = pending.copy(
                        loading = false, error = importErrorMessage(result.code))) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(encodingChange = pending.copy(loading = false,
                error = "인코딩을 변경하지 못했습니다. 원본과 기록은 보존됩니다.")) } }
            finally { pendingInterpretations.remove(pending.book.id) }
        }
    }

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
                    action(service) { progress ->
                        // Provider callbacks run on IO. Publish UI state on Main even
                        // when an inline collector resumes on the publisher's thread.
                        withContext(Dispatchers.Main.immediate) {
                            mutableState.update { it.copy(importing = ImportUiState.Working(progress)) }
                        }
                    }
                }
                acceptImportResult(result, origin)
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(importing = ImportUiState.Error("가져오기를 취소했습니다. 원본과 기존 서재는 유지됩니다.")) }
                throw cancelled
            } catch (failure: ImportFailure) {
                mutableState.update { it.copy(importing = ImportUiState.Error(importErrorMessage(failure.code), failure.code, (it.importing as? ImportUiState.Working)?.progress?.phase)) }
            } catch (_: Exception) {
                mutableState.update { it.copy(importing = ImportUiState.Error(importErrorMessage(ImportErrorCode.DATABASE_UNAVAILABLE))) }
            }
        }
    }

    private suspend fun acceptImportResult(result: ImportResult, origin: DocumentOrigin) {
        val previews = if (result is ImportResult.EncodingRequired) withContext(ioDispatcher) { management?.preview(result.stagingPath).orEmpty() } else emptyList()
        mutableState.update { it.copy(importing = when (result) {
            is ImportResult.Success -> if (origin == DocumentOrigin.VIEW) ImportUiState.Idle else ImportUiState.Complete(result)
            is ImportResult.Failure -> ImportUiState.Error(importErrorMessage(result.code), result.code, (it.importing as? ImportUiState.Working)?.progress?.phase)
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
                            if (state.value.reader?.book?.id == book.id || state.value.epubReader?.book?.id == book.id || state.value.comicReader?.book?.id == book.id) closeReader()
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
        if (book.format == org.bookreader.mobile.model.BookFormat.TXT && readerFactory == null ||
            book.format == org.bookreader.mobile.model.BookFormat.EPUB && epubFactory == null ||
            book.format == org.bookreader.mobile.model.BookFormat.COMIC && comicFactory == null) return
        if (book.availability != BookAvailability.READY || book.id in pendingDeletions || book.id in pendingInterpretations) return
        viewModelScope.launch {
            try {
            readerMutex.withLock {
                // A tap queued before confirmation must not reopen this book after deletion starts.
                if (book.id in pendingDeletions || book.id in pendingInterpretations) return@withLock
                readerObserver?.cancel()
                state.value.reader?.close()
                state.value.epubReader?.close(); state.value.comicReader?.close()
                mutableState.update { it.copy(reader = null, epubReader = null, comicReader = null) }
                if (book.format == org.bookreader.mobile.model.BookFormat.COMIC) {
                    val comic = requireNotNull(comicFactory)(book, viewModelScope)
                    mutableState.update { it.copy(comicReader = comic, selectedBook = null) }
                    return@withLock
                }
                if (book.format == org.bookreader.mobile.model.BookFormat.EPUB) {
                    val epub = requireNotNull(epubFactory)(book, viewModelScope)
                    mutableState.update { it.copy(epubReader = epub, selectedBook = null) }
                    return@withLock
                }
                val reader = requireNotNull(readerFactory)(book, viewModelScope)
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
                mutableState.update { it.copy(reader = null, epubReader = null, comicReader = null, importing = ImportUiState.Error("책을 열지 못했습니다. 원본과 마지막 저장된 독서 기록은 유지됩니다.")) }
                refreshLibrary()
            }
        }
    }
    fun leaveReader() { viewModelScope.launch { closeReader() } }
    private suspend fun closeReader() {
        readerMutex.withLock {
            readerObserver?.cancel()
            try { state.value.reader?.close(); state.value.epubReader?.close(); state.value.comicReader?.close() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(notice = "독서를 종료하는 동안 오류가 발생했습니다. 마지막 저장된 독서 기록은 유지됩니다.") } }
            finally { mutableState.update { it.copy(reader = null, epubReader = null, comicReader = null) } }
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
    ImportErrorCode.TOO_LARGE -> "파일 한도를 초과했습니다. TXT는 256MiB, EPUB는 512MiB까지 지원합니다."
    ImportErrorCode.ENCODING_REQUIRED -> "텍스트 인코딩을 선택해 주세요."
    ImportErrorCode.INVALID_TEXT, ImportErrorCode.UNSUPPORTED_FORMAT -> "지원하지 않거나 손상된 파일입니다. TXT 인코딩 또는 EPUB의 지원 범위를 확인해 주세요."
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
