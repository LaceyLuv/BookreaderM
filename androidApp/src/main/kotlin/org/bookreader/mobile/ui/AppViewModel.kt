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
import org.bookreader.mobile.library.*

enum class AppTab { LIBRARY, SEARCH, SETTINGS }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface ThemeStore {
    fun read(): Result<ThemeMode>
    fun write(mode: ThemeMode): Result<Unit>
}

/** A scoped repository lease; the factory decides whether close releases an owned or borrowed handle. */
class LibrarySession(val repository: BookRepository, val close: () -> Unit) {
    var progressRepository: org.bookreader.mobile.repository.ProgressRepository? = null
    var libraryManager: RoomLibraryManager? = null
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
    val catalog: LibraryCatalog = LibraryCatalog(),
    val selectedBooks: Set<String> = emptySet(),
    val editingBook: Book? = null,
    val editorSaving: Boolean = false,
    val coverLoading: Boolean = false,
    val editorError: String? = null,
    val pendingCoverKey: String? = null,
    val coverSelectionVersion: Long = 0,
    val shelvesVisible: Boolean = false,
    val batch: BatchImportUi? = null,
)

class AppViewModel(
    private val sessions: LibrarySessionFactory,
    private val themes: ThemeStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val management: BookManagement? = null,
    private val readerFactory: ((Book, CoroutineScope) -> TxtReaderController)? = null,
    private val comicFactory: ((Book, CoroutineScope) -> org.bookreader.mobile.comic.ComicReaderController)? = null,
    private val epubFactory: ((Book, CoroutineScope) -> org.bookreader.mobile.epub.EpubReaderController)? = null,
    private val artwork: LibraryArtwork? = null,
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
                if (withContext(ioDispatcher) { management?.consumeInterruptedBatch() == true }) {
                    mutableState.update { it.copy(notice = "일괄 가져오기가 중단되었습니다. 완료된 책은 서재에 남아 있습니다. 나머지 파일은 다시 선택해 주세요.") }
                }
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

    fun retryLibrary() = loadLibrary(showLoading = true)

    private fun loadLibrary(showLoading: Boolean) {
        if (libraryJob?.isActive == true) return
        if (showLoading) mutableState.update { it.copy(library = LibraryState.Loading) }
        libraryJob = viewModelScope.launch {
            val snapshot = try {
                recoveryDone.await()
                withContext(ioDispatcher) {
                    val active = sessions.open()
                    try {
                        val loaded = active.repository.loadBooks()
                        val catalog = active.libraryManager?.load() ?: LibraryCatalog()
                        val candidate = (loaded as? LibraryState.Content)?.books?.let(::continueBook)
                        val progress = candidate?.currentRevision?.let { revision ->
                            active.progressRepository?.loadProgress(candidate.id, revision)
                        }
                        currentCoroutineContext().ensureActive()
                        Triple(loaded, catalog,
                            (progress as? org.bookreader.mobile.repository.ProgressReadResult.Found)?.progress?.percent)
                    } finally {
                        // Release the lease after its query exits. Android borrows the app-owned pool;
                        // independent test factories may own and close their database instead.
                        active.close()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Triple(LibraryState.Error(LibraryErrorCode.DATABASE_UNAVAILABLE), state.value.catalog, state.value.continuePercent)
            }
            // Publish one coherent snapshot on Main; inline observers must never recompose on IO.
            val (loaded, catalog, percent) = snapshot
            val retainedIds = (loaded as? LibraryState.Content)?.books?.map { it.id }?.toSet()
            mutableState.update { it.copy(library = loaded, catalog = catalog, continuePercent = percent, selectedBooks = if (retainedIds == null) it.selectedBooks else it.selectedBooks.intersect(retainedIds)) }
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

    private var batchChoice: kotlinx.coroutines.CompletableDeferred<String>? = null

    fun receiveDocuments(documents: List<IncomingDocument>) {
        if (documents.size == 1) { receiveDocument(documents.single()); return }
        if (documents.size !in 2..64 || documents.any { it.origin != DocumentOrigin.PICKER } || documents.map { it.uri }.distinct().size != documents.size) {
            rejectDocument(); return
        }
        viewModelScope.launch {
            recoveryDone.await()
            if (importJob?.isActive == true || state.value.importing is ImportUiState.SelectEncoding || state.value.importing is ImportUiState.ConfirmShare) {
                mutableState.update { it.copy(notice = "진행 중인 가져오기를 완료하거나 취소한 뒤 새 파일을 선택해 주세요.") }; return@launch
            }
            val service = management ?: return@launch
            mutableState.update { it.copy(batch = BatchImportUi(documents.size), importing = ImportUiState.Working(), notice = null) }
            importJob = viewModelScope.launch {
                var pending: ImportResult? = null
                val progress: suspend (ImportProgress) -> Unit = { value -> withContext(Dispatchers.Main.immediate) {
                    mutableState.update { it.copy(importing = ImportUiState.Working(value)) }
                } }
                try {
                    withContext(ioDispatcher) { service.setBatchActive(true) }
                    for (document in documents) {
                        pending = null
                        mutableState.update { it.copy(importing = ImportUiState.Working()) }
                        var result = try {
                            withContext(ioDispatcher) { service.import(document, progress).also { pending = it } }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: ImportFailure) { ImportResult.Failure(failure.code) }
                        catch (_: Exception) { ImportResult.Failure(ImportErrorCode.DATABASE_UNAVAILABLE) }
                        while (result is ImportResult.EncodingRequired) {
                            val required = result
                            val previews = withContext(ioDispatcher) { service.preview(required.stagingPath) }
                            val choice = kotlinx.coroutines.CompletableDeferred<String>().also { batchChoice = it }
                            mutableState.update { it.copy(importing = ImportUiState.SelectEncoding(required.jobId, DocumentOrigin.PICKER, previews)) }
                            val encoding = choice.await()
                            batchChoice = null
                            result = withContext(ioDispatcher) { service.selectEncoding(required.jobId, encoding, progress).also { pending = it } }
                        }
                        recordBatchResult(result)
                        pending = null
                        if (result is ImportResult.Failure && result.code in setOf(ImportErrorCode.DISK_FULL, ImportErrorCode.DATABASE_UNAVAILABLE)) break
                    }
                } catch (cancelled: CancellationException) {
                    // ImportCoordinator may have committed in NonCancellable before IO's prompt cancellation.
                    (pending as? ImportResult.Success)?.let { recordBatchResult(it); pending = null }
                    mutableState.update { it.copy(batch = it.batch?.copy(cancelled = true)) }
                    throw cancelled
                } catch (_: Exception) {
                    recordBatchResult(ImportResult.Failure(ImportErrorCode.DATABASE_UNAVAILABLE))
                } finally {
                    val encoding = pending as? ImportResult.EncodingRequired
                    // Include Main-thread publication in the non-cancellable boundary. Returning
                    // from IO to an already cancelled caller otherwise skips the final UI state.
                    withContext(kotlinx.coroutines.NonCancellable) {
                        withContext(ioDispatcher) {
                            try { if (encoding != null) service.cancelEncoding(encoding.jobId) } catch (_: Exception) { /* Recovery owns an unfinished journal. */ }
                            try { service.setBatchActive(false) } catch (_: Exception) { /* Next start reports interrupted work. */ }
                        }
                        batchChoice?.cancel(); batchChoice = null
                        mutableState.update { it.copy(importing = ImportUiState.Idle, batch = it.batch?.copy(active = false)) }
                        refreshLibrary()
                    }
                }
            }
        }
    }

    private fun recordBatchResult(result: ImportResult) {
        mutableState.update { value ->
            val batch = value.batch ?: return@update value
            val duplicate = (result as? ImportResult.Success)?.duplicate == true
            val message = when (result) {
                is ImportResult.Success -> "${result.book.title}: " + if (duplicate) "이미 서재에 있음" else "추가 완료"
                is ImportResult.Failure -> "${batch.completed + 1}번째 파일: ${importErrorMessage(result.code)}"
                is ImportResult.EncodingRequired -> return@update value
            }
            value.copy(batch = batch.copy(completed = batch.completed + 1,
                added = batch.added + if (result is ImportResult.Success && !duplicate) 1 else 0,
                duplicates = batch.duplicates + if (duplicate) 1 else 0, failures = batch.failures + if (result is ImportResult.Failure) 1 else 0,
                results = batch.results + message))
        }
    }
    fun dismissBatch() { if (state.value.batch?.active != true) mutableState.update { it.copy(batch = null) } }

    fun rejectDocument() {
        val message = "파일을 열 수 없습니다. 파일 앱에서 읽기 권한이 있는 TXT·EPUB·ZIP/CBZ 파일을 다시 선택해 주세요. 한 번에 최대 64개를 추가할 수 있습니다."
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

    private val catalogWrites = Mutex()
    private var coverJob: Job? = null

    private suspend fun <T> withCatalog(action: suspend (RoomLibraryManager) -> T): T = withContext(ioDispatcher) {
        val active = sessions.open()
        try { action(requireNotNull(active.libraryManager)) } finally { active.close() }
    }

    /** Library/card composition owns extraction and cancels it when leaving the screen. */
    suspend fun ensureArtwork(book: Book) {
        val service = artwork ?: return
        try {
            val details = state.value.catalog.details[book.id] ?: BookDetails()
            service.ensure(book, details)
            // Another owner may have committed before cancellation or while we waited for the decoder.
            val current = withCatalog { it.record(book.id) } ?: return
            mutableState.update { value ->
                val library = value.library as? LibraryState.Content ?: return@update value
                val displayed = library.books.firstOrNull { it.id == book.id } ?: return@update value
                val extracted = current.first
                if (displayed.sourceSha256 != extracted.sourceSha256 || displayed.currentRevision != extracted.currentRevision ||
                    displayed.updatedAt > extracted.updatedAt) return@update value
                // Artwork never replaces an interpretation, availability, read order or writer epoch.
                val merged = displayed.copy(title = extracted.title, author = extracted.author,
                    coverKey = extracted.coverKey, updatedAt = extracted.updatedAt)
                value.copy(library = library.copy(books = library.books.map { if (it.id == book.id) merged else it }),
                    catalog = value.catalog.copy(details = value.catalog.details + (book.id to current.second)))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A decorative fallback remains readable; library data is not reset. */ }
    }

    fun editBook(book: Book) { mutableState.update { it.copy(selectedBook = null, editingBook = book, pendingCoverKey = null, editorError = null) } }
    fun dismissEditor() {
        if (state.value.editorSaving) return
        coverJob?.cancel()
        mutableState.update { it.copy(editingBook = null, pendingCoverKey = null, coverLoading = false, editorError = null) }
    }
    fun rejectCover() {
        mutableState.update { it.copy(editorError = "파일 선택기에서 표지 이미지 한 개를 선택해 주세요.") }
    }
    fun importCover(uri: android.net.Uri) {
        val service = artwork ?: return
        val book = state.value.editingBook ?: return
        if (state.value.editorSaving || state.value.coverLoading) return
        mutableState.update { it.copy(coverLoading = true, editorError = null, pendingCoverKey = null) }
        coverJob = viewModelScope.launch {
            try {
                val key = service.import(uri)
                mutableState.update { if (it.editingBook?.id == book.id) it.copy(pendingCoverKey = key,
                    coverSelectionVersion = it.coverSelectionVersion + 1) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(editorError = "표지를 읽지 못했습니다. 8MB 이하의 JPEG·PNG·WebP 파일을 선택해 주세요.") } }
            finally { mutableState.update { it.copy(coverLoading = false) } }
        }
    }
    fun saveBook(edit: BookEdit) {
        val book = state.value.editingBook ?: return
        if (state.value.editorSaving || state.value.coverLoading) return
        mutableState.update { it.copy(editorSaving = true, editorError = null) }
        viewModelScope.launch {
            try {
                catalogWrites.withLock { withCatalog { it.edit(book.id, edit) } }
                mutableState.update { it.copy(editingBook = null, pendingCoverKey = null) }
                refreshLibrary()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(editorError = "책 정보를 저장하지 못했습니다. 제목과 책장을 확인해 주세요. 기존 정보는 유지됩니다.") } }
            finally { mutableState.update { it.copy(editorSaving = false) } }
        }
    }
    fun selectLibraryPreferences(change: (LibraryPreferences) -> LibraryPreferences) = catalogWrite { it.preferences(change(it.load().preferences)) }
    fun showShelves(show: Boolean) { mutableState.update { it.copy(shelvesVisible = show) } }
    fun createShelf(name: String) = catalogWrite { it.createShelf(java.util.UUID.randomUUID().toString(), name) }
    fun renameShelf(id: String, name: String) = catalogWrite { it.renameShelf(id, name) }
    fun deleteShelf(id: String) = catalogWrite { it.deleteShelf(id) }
    fun toggleSelected(book: Book) {
        mutableState.update { value ->
            if (book.id in value.selectedBooks) value.copy(selectedBooks = value.selectedBooks - book.id)
            else if (value.selectedBooks.size < 200) value.copy(selectedBooks = value.selectedBooks + book.id)
            else value.copy(notice = "한 번에 200권까지 선택할 수 있습니다.")
        }
    }
    fun clearSelection() { mutableState.update { it.copy(selectedBooks = emptySet()) } }
    fun classifySelection(shelf: String, add: Boolean) {
        val ids = state.value.selectedBooks
        catalogWrite { it.classify(ids, shelf, add) }
    }
    private fun catalogWrite(action: suspend (RoomLibraryManager) -> Unit) {
        mutableState.update { it.copy(notice = null) }
        viewModelScope.launch {
            try {
                catalogWrites.withLock { withCatalog(action) }
                refreshLibrary()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(notice = "변경을 저장하지 못했습니다. 중복된 책장 이름이나 저장 공간을 확인해 주세요. 기존 정보는 유지됩니다.") } }
        }
    }

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
                        mutableState.update { value -> value.copy(encodingChange = null,
                            library = (value.library as? LibraryState.Content)?.let { library ->
                                library.copy(books = library.books.map { if (it.id == result.book.id) result.book else it })
                            } ?: value.library, notice =
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
        batchChoice?.let { it.complete(encodingId); return }
        val pending = state.value.importing as? ImportUiState.SelectEncoding ?: return
        launchImport(pending.origin) { service, progress -> service.selectEncoding(pending.jobId, encodingId, progress) }
    }

    private fun launchImport(origin: DocumentOrigin,
        action: suspend (BookManagement, suspend (ImportProgress) -> Unit) -> ImportResult,
    ) {
        if (importJob?.isActive == true) return
        val service = management ?: return
        mutableState.update { it.copy(importing = ImportUiState.Working(), batch = null) }
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
        if (state.value.batch?.active == true) { importJob?.cancel(); return }
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
        val unavailable = unavailableBookMessage(book.availability)
        if (unavailable != null) {
            mutableState.update { it.copy(notice = unavailable, selectedBook = null) }
            return
        }
        if (book.id in pendingDeletions || book.id in pendingInterpretations) {
            mutableState.update { it.copy(notice = "책을 변경하는 중입니다. 작업이 끝나면 다시 열어 주세요.") }
            return
        }
        if (book.format == org.bookreader.mobile.model.BookFormat.TXT && readerFactory == null ||
            book.format == org.bookreader.mobile.model.BookFormat.EPUB && epubFactory == null ||
            book.format == org.bookreader.mobile.model.BookFormat.COMIC && comicFactory == null) {
            mutableState.update { it.copy(notice = "읽기 화면을 준비하지 못했습니다. 앱을 다시 열어 주세요. 기존 책과 독서 기록은 유지됩니다.", selectedBook = null) }
            return
        }
        viewModelScope.launch {
            try {
            readerMutex.withLock {
                // A tap queued before confirmation must not reopen this book after deletion starts.
                if (book.id in pendingDeletions || book.id in pendingInterpretations) {
                    mutableState.update { it.copy(notice = "책을 변경하는 중입니다. 작업이 끝나면 다시 열어 주세요.") }
                    return@withLock
                }
                readerObserver?.cancel()
                state.value.reader?.close()
                state.value.epubReader?.close(); state.value.comicReader?.close()
                mutableState.update { it.copy(reader = null, epubReader = null, comicReader = null, notice = null) }
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
    private fun refreshLibrary() { libraryJob?.cancel(); libraryJob = null; loadLibrary(showLoading = false) }
    fun dismissNotice() { mutableState.update { it.copy(notice = null) } }
}

fun importErrorMessage(code: ImportErrorCode): String = when (code) {
    ImportErrorCode.PERMISSION_DENIED -> "파일 읽기 권한이 만료되었거나 없습니다. 파일을 다시 선택해 주세요."
    ImportErrorCode.SOURCE_UNAVAILABLE -> "원본 파일을 읽을 수 없습니다. 파일 앱에서 다시 선택해 주세요."
    ImportErrorCode.DISK_FULL -> "앱 저장 공간이 부족합니다. 공간을 확보한 뒤 다시 시도해 주세요."
    ImportErrorCode.TOO_LARGE -> "파일이 너무 큽니다. TXT는 256MB, EPUB는 512MB, ZIP/CBZ는 2GB까지 추가할 수 있습니다."
    ImportErrorCode.ENCODING_REQUIRED -> "텍스트 인코딩을 선택해 주세요."
    ImportErrorCode.INVALID_TEXT -> "텍스트를 읽을 수 없습니다. 파일의 인코딩과 손상 여부를 확인해 주세요."
    ImportErrorCode.UNSUPPORTED_FORMAT -> "지원하지 않거나 손상된 책입니다. TXT·EPUB·ZIP/CBZ 파일인지 확인해 주세요. DRM·암호화된 책과 고정 레이아웃 EPUB은 지원하지 않습니다."
    ImportErrorCode.EXISTING_COPY_UNAVAILABLE -> "서재에 있는 같은 책의 파일을 확인하지 못했습니다. 기존 책과 기록은 보존됩니다. 원본 파일 상태를 확인해 주세요."
    ImportErrorCode.BOOK_DELETING -> "같은 책을 삭제 중입니다. 삭제가 끝난 뒤 다시 추가해 주세요."
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
