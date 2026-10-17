package org.bookreader.mobile.comic

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.bookreader.mobile.epub.ArchiveBookmarkAccess
import org.bookreader.mobile.locator.ComicLocator
import org.bookreader.mobile.locator.ComicLocatorPayload
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.reader.*
import org.bookreader.mobile.repository.*
import org.bookreader.mobile.settings.*

data class ComicReaderUi(val manifest: ComicManifest? = null, val index: Int = 0,
    val fatalError: Boolean = false, val settings: ReaderSettings = ReaderSettings(),
    val settingsError: Boolean = false, val settingsSaving: Boolean = false, val hostVersion: Int = 0,
    val bookmarks: List<Bookmark> = emptyList(), val bookmarkCursor: BookmarkCursor? = null,
    val bookmarkError: Boolean = false)

/** Owns one verified archive and writer. Composed pages own and release their bitmap leases. */
class ComicReaderController(val book: Book, private val file: File, private val cacheDirectory: File,
    verifySource: suspend () -> Unit, access: ReaderProgressAccess, parent: CoroutineScope,
    private val settingsAccess: ReaderSettingsAccess, private val bookmarks: ArchiveBookmarkAccess) {
    private val lifetime = SupervisorJob(parent.coroutineContext[Job])
    private val scope = CoroutineScope(parent.coroutineContext + lifetime)
    val progress = ArchiveProgressController(book, access, scope)
    private val mutableState = MutableStateFlow(ComicReaderUi())
    val state = mutableState.asStateFlow()
    private val decoder = AtomicReference<ComicImageDecoder?>(null)
    private val closeMutex = Mutex()
    private var closing = false
    private var pendingJump = false
    private var requestedAnchor: ComicLocatorPayload? = null
    var loadedSettings: LoadedReaderSettings? = null
        private set
    private var settingsJob: Job? = null
    init { lifetime.invokeOnCompletion { decoder.getAndSet(null)?.close() } }
    private val opening = scope.launch {
        try {
            val loaded = progress.state.first { it.phase != ArchiveReaderPhase.LOADING_PROGRESS }
            check(loaded.phase == ArchiveReaderPhase.RESTORING)
            when (val settings = settingsAccess.load(book.id)) {
                is ReaderSettingsRead.Ready -> { loadedSettings = settings.value; mutableState.update { it.copy(settings = settings.value.effective) } }
                ReaderSettingsRead.Error -> mutableState.update { it.copy(settingsError = true) }
            }
            verifySource()
            val manifest = withContext(Dispatchers.IO) {
                val context = currentCoroutineContext()
                val result = ComicManifest.inspect(file, book.sourceSha256, context::ensureActive)
                check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory)
                // Derived manifest only: rebuild from the verified source on every open.
                // Opaque source hash binds the stable sort version; never use archive names as paths.
                val destination = File(cacheDirectory, "${book.sourceSha256}-sort-1.json")
                val temporary = File.createTempFile("manifest-", ".tmp", cacheDirectory)
                try {
                    val json = org.json.JSONObject().put("sourceSha256", result.sourceSha256).put("sortVersion", 1)
                        .put("entryKeys", org.json.JSONArray(result.entryKeys))
                    temporary.writeText(json.toString())
                    context.ensureActive()
                    check(temporary.renameTo(destination))
                } finally { temporary.delete() }
                result
            }
            val original = loaded.original as? ComicLocator
            val index = original?.let { manifest.indexOf(it.payload.entryKey).also { value -> require(value >= 0) } } ?: 0
            decoder.set(ComicImageDecoder(file, File(cacheDirectory, "decode-${book.id}")))
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(manifest = manifest, index = index) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { progress.restorationFailed(); mutableState.update { it.copy(fatalError = true) } }
    }
    suspend fun decode(entry: String, width: Int, height: Int): ComicImage {
        check(!closing && state.value.manifest?.indexOf(entry)?.let { it >= 0 } == true)
        return requireNotNull(decoder.get()).decode(entry, width, height)
    }
    fun anchor(): ComicLocatorPayload? = requestedAnchor ?: (progress.state.value.accepted as? ComicLocator)?.payload
    fun renderReady(entry: String, generation: Int) {
        if (closing || state.value.manifest?.entryKeys?.getOrNull(state.value.index) != entry) return
        progress.restored(generation)
        if (pendingJump) scope.launch {
            val ready = progress.state.first { it.phase !in setOf(ArchiveReaderPhase.RESTORING, ArchiveReaderPhase.REFLOWING) }
            if (ready.phase == ArchiveReaderPhase.READY && state.value.manifest?.entryKeys?.getOrNull(state.value.index) == entry) {
                val target = requestedAnchor
                pendingJump = false; requestedAnchor = null
                visible(entry, target?.normalizedX ?: .5, target?.normalizedY ?: 0.0, true)
            }
        }
    }
    fun visible(entry: String, x: Double, y: Double, user: Boolean) {
        if (!user || closing || progress.state.value.phase != ArchiveReaderPhase.READY) return
        val manifest = state.value.manifest ?: return
        val index = manifest.indexOf(entry); if (index < 0) return
        val locator = ComicLocator(requireNotNull(book.currentRevision), ComicLocatorPayload(entry,
            imageIndexHint = index, normalizedX = x.coerceIn(0.0, 1.0), normalizedY = y.coerceIn(0.0, 1.0)))
        progress.userAnchor(progress.state.value.generation, locator, (index + y.coerceIn(0.0, 1.0)) / manifest.entryKeys.size * 100)
        mutableState.update { it.copy(index = index) }
    }
    fun move(delta: Int) = goIndex(state.value.index + delta)
    fun goIndex(index: Int, anchor: ComicLocatorPayload? = null) {
        val manifest = state.value.manifest ?: return
        if (closing || index !in manifest.entryKeys.indices || state.value.fatalError) return
        requestedAnchor = anchor ?: ComicLocatorPayload(manifest.entryKeys[index], imageIndexHint = index,
            normalizedX = .5, normalizedY = 0.0)
        pendingJump = true
        progress.beginReflow()
        mutableState.update { it.copy(index = index, hostVersion = it.hostVersion + 1) }
    }
    fun reflow() {
        if (progress.state.value.phase == ArchiveReaderPhase.READY) {
            progress.beginReflow(); mutableState.update { it.copy(hostVersion = it.hostVersion + 1) }
        }
    }
    fun saveSettings(global: ReaderSettings, book: BookReaderPreferences) {
        val expected = loadedSettings ?: return
        if (closing || settingsJob?.isActive == true || progress.state.value.phase != ArchiveReaderPhase.READY) return
        mutableState.update { it.copy(settingsSaving = true) }
        settingsJob = scope.launch {
            try {
                check(settingsAccess.save(expected, global, book) == ReaderSettingsWrite.SAVED)
                val read = settingsAccess.load(this@ComicReaderController.book.id) as? ReaderSettingsRead.Ready ?: error("Settings unavailable")
                loadedSettings = read.value; progress.beginReflow()
                mutableState.update { it.copy(settings = read.value.effective, settingsError = false, hostVersion = it.hostVersion + 1) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(settingsError = true) } }
            finally { mutableState.update { it.copy(settingsSaving = false) } }
        }
    }
    fun retrySettings() { scope.launch {
        when (val read = settingsAccess.load(book.id)) {
            is ReaderSettingsRead.Ready -> { loadedSettings = read.value; reflow(); mutableState.update { it.copy(settings = read.value.effective, settingsError = false) } }
            ReaderSettingsRead.Error -> mutableState.update { it.copy(settingsError = true) }
        }
    } }
    fun loadBookmarks(more: Boolean = false) { val cursor = if (more) state.value.bookmarkCursor ?: return else null
        scope.launch { when (val read = bookmarks.load(book.id, requireNotNull(book.currentRevision), cursor)) {
            is BookmarkReadResult.Ready -> mutableState.update { it.copy(bookmarks = read.bookmarks, bookmarkCursor = read.next, bookmarkError = false) }
            BookmarkReadResult.Error -> mutableState.update { it.copy(bookmarkError = true) }
        } }
    }
    fun addBookmark() {
        val session = progress.readySession() ?: return
        val locator = progress.state.value.accepted ?: state.value.manifest?.entryKeys?.getOrNull(state.value.index)?.let {
            ComicLocator(requireNotNull(book.currentRevision), ComicLocatorPayload(it, imageIndexHint = state.value.index))
        } ?: return
        scope.launch { if (bookmarks.add(java.util.UUID.randomUUID().toString(), session, locator, null, System.currentTimeMillis()) == BookmarkWriteResult.COMMITTED) loadBookmarks()
            else mutableState.update { it.copy(bookmarkError = true) } }
    }
    fun goBookmark(value: Bookmark) {
        val locator = value.locator as? ComicLocator
        val index = locator?.let { state.value.manifest?.indexOf(it.payload.entryKey) } ?: -1
        if (index < 0) { mutableState.update { it.copy(bookmarkError = true) }; return }
        goIndex(index, locator?.payload)
    }
    fun deleteBookmark(id: String) { scope.launch {
        if (bookmarks.delete(book.id, id) == BookmarkWriteResult.COMMITTED) loadBookmarks()
        else mutableState.update { it.copy(bookmarkError = true) }
    } }
    suspend fun close() = closeMutex.withLock {
        if (closing) return@withLock
        closing = true; opening.cancel(); settingsJob?.cancel()
        try { opening.join(); settingsJob?.join(); progress.close() }
        finally { decoder.getAndSet(null)?.close(); lifetime.cancel() }
    }
}
