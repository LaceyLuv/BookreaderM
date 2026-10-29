package org.bookreader.mobile.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.bookreader.mobile.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import org.bookreader.mobile.reader.ReaderControlTheme
import org.bookreader.mobile.reader.TxtReaderScreen
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.importing.ImportPhase
import org.bookreader.mobile.encoding.TxtEncoding
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.repository.LibraryState
import org.bookreader.mobile.library.*

@Composable
@Suppress("DEPRECATION") // Android 8/9 need an explicit bar color; newer edge-to-edge uses the surface.
fun BookReaderApp(model: AppViewModel, onAddTxt: () -> Unit = {}, onChooseCover: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val dark = state.theme.isDark(isSystemInDarkTheme())
    val view = LocalView.current
    val reading = state.reader != null || state.epubReader != null || state.comicReader != null
    DisposableEffect(view, reading) {
        val window = (view.context as? Activity)?.window
        val legacy = reading && android.os.Build.VERSION.SDK_INT < 29 && window != null
        val previousColor = window?.navigationBarColor
        val previousInputMode = window?.attributes?.softInputMode
        // Search/edit fields live in their own dialogs. The publication behind them must
        // keep its viewport while the IME transfers between dialog and activity windows.
        if (reading && previousInputMode != null) window?.setSoftInputMode(
            (previousInputMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        if (legacy) window?.navigationBarColor = 0xFF101720.toInt()
        onDispose {
            if (legacy && previousColor != null) window?.navigationBarColor = previousColor
            if (reading && previousInputMode != null) window?.setSoftInputMode(previousInputMode)
        }
    }
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark && state.reader == null && state.epubReader == null && state.comicReader == null
                isAppearanceLightNavigationBars = !dark && state.reader == null && state.epubReader == null && state.comicReader == null
            }
        }
    }
    state.comicReader?.let { reader ->
        ReaderControlTheme {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                    if (state.batch?.active != true && state.importing !is ImportUiState.Idle) ImportStatus(state.importing, model::confirmShare,
                        model::dismissImport, model::cancelImport, model::selectImportEncoding, model::openBook)
                    androidx.compose.runtime.key(reader) { org.bookreader.mobile.comic.ComicReaderScreen(reader, model::leaveReader) { model.openBook(reader.book) } }
                }
            }
        }
        return
    }
    state.epubReader?.let { reader ->
        ReaderControlTheme {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                    if (state.batch?.active != true && state.importing !is ImportUiState.Idle) ImportStatus(state.importing, model::confirmShare,
                        model::dismissImport, model::cancelImport, model::selectImportEncoding, model::openBook)
                    androidx.compose.runtime.key(reader) { org.bookreader.mobile.epub.EpubReaderScreen(reader, model::leaveReader) { model.openBook(reader.book) } }
                }
            }
        }
        return
    }
    state.reader?.let { reader ->
        ReaderControlTheme {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                state.notice?.let { Text(it, modifier = Modifier.testTag("import_notice"), color = MaterialTheme.colorScheme.error) }
                if (state.batch?.active != true && (state.importing is ImportUiState.ConfirmShare || state.importing is ImportUiState.SelectEncoding ||
                    state.importing is ImportUiState.Working || state.importing is ImportUiState.Complete || state.importing is ImportUiState.Error)) {
                    ImportStatus(state.importing, model::confirmShare, model::dismissImport, model::cancelImport,
                        model::selectImportEncoding, model::openBook)
                }
                // Reader replacement can be conflated into one composition frame. Scope the
                // LazyList, gesture jobs, and saved window baseline to this specific owner.
                androidx.compose.runtime.key(reader) { TxtReaderScreen(reader, model::leaveReader) }
            }
            }
        }
        return
    }
    BackHandler(enabled = state.tab != AppTab.LIBRARY) { model.selectTab(AppTab.LIBRARY) }
    BookReaderScreen(state, model::selectTab, model::updateQuery, model::retryLibrary, model::selectTheme,
        onAddTxt, model::confirmShare, model::dismissImport, model::cancelImport, model::selectImportEncoding,
        model::showBook, model::requestDelete, model::confirmDelete, model::openBook,
        model::requestBookEncoding, model::changeBookEncoding, model::dismissBookEncoding, model::dismissNotice,
        LibraryActions(preferences = model::selectLibraryPreferences, edit = model::editBook, save = model::saveBook,
            dismissEditor = model::dismissEditor, chooseCover = onChooseCover, ensureArtwork = model::ensureArtwork,
            showShelves = model::showShelves, createShelf = model::createShelf, renameShelf = model::renameShelf,
            deleteShelf = model::deleteShelf, toggleSelected = model::toggleSelected, clearSelection = model::clearSelection,
            classify = model::classifySelection, dismissBatch = model::dismissBatch))
}

@Composable
fun BookReaderScreen(
    state: AppUiState,
    onTabSelected: (AppTab) -> Unit,
    onQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onAddTxt: () -> Unit = {},
    onConfirmShare: () -> Unit = {},
    onDismissImport: () -> Unit = {},
    onCancelImport: () -> Unit = {},
    onEncodingSelected: (String) -> Unit = {},
    onBookInfo: (Book?) -> Unit = {},
    onDeleteRequested: (Book?) -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onOpenBook: (Book) -> Unit = {},
    onBookEncoding: (Book) -> Unit = {},
    onChangeBookEncoding: (String) -> Unit = {},
    onDismissBookEncoding: () -> Unit = {},
    onDismissNotice: () -> Unit = {},
    libraryActions: LibraryActions = LibraryActions(),
) {
    val dark = state.theme.isDark(isSystemInDarkTheme())
    BookReaderTheme(dark) {
        Scaffold(
            bottomBar = {
                Column {
                (state.library as? LibraryState.Content)?.books?.let(::continueBook)?.let { book ->
                    ContinueReading(book, state.continuePercent, onOpenBook, state.catalog.details[book.id] ?: BookDetails())
                }
                NavigationBar {
                    listOf(AppTab.LIBRARY, AppTab.SETTINGS).forEach { tab ->
                        NavigationBarItem(
                            selected = state.tab == tab || tab == AppTab.LIBRARY && state.tab == AppTab.SEARCH,
                            onClick = { onTabSelected(tab) },
                            modifier = Modifier.testTag("tab_${tab.name}"),
                            icon = {
                                AppIcon(
                                    when (tab) {
                                        AppTab.LIBRARY -> R.drawable.ic_library
                                        AppTab.SEARCH -> R.drawable.ic_search
                                        AppTab.SETTINGS -> R.drawable.ic_settings
                                    },
                                    modifier = Modifier.clearAndSetSemantics { },
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
                }
            },
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (state.tab == AppTab.SETTINGS) "설정" else "서재", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f).testTag("screen_title"))
                    if (state.tab != AppTab.SETTINGS) IconButton(onClick = { onTabSelected(if (state.tab == AppTab.SEARCH) AppTab.LIBRARY else AppTab.SEARCH) },
                        modifier = Modifier.testTag("tab_SEARCH")) { AppIcon(R.drawable.ic_search, description = "서재 검색") }
                    if (state.tab != AppTab.SETTINGS) Button(onClick = onAddTxt,
                        enabled = state.importing !is ImportUiState.Working && state.importing !is ImportUiState.SelectEncoding,
                        modifier = Modifier.testTag("add_txt")) {
                        AppIcon(R.drawable.ic_add)
                        Spacer(Modifier.size(6.dp))
                        Text("책 추가")
                    }
                }
                state.notice?.let { LibraryNotice(it, onDismissNotice) }
                state.batch?.let { BatchImportStatus(it, libraryActions.dismissBatch) }
                ImportStatus(state.importing, onConfirmShare, onDismissImport, onCancelImport,
                    onEncodingSelected, onOpenBook)
                when (state.tab) {
                    AppTab.LIBRARY, AppTab.SEARCH -> {
                        if (state.tab == AppTab.SEARCH) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChanged,
                            label = { Text("책 검색") },
                            placeholder = { Text("제목, 작가 또는 파일명") },
                            leadingIcon = { AppIcon(R.drawable.ic_search) },
                            trailingIcon = { IconButton(onClick = { onQueryChanged(""); onTabSelected(AppTab.LIBRARY) }) {
                                AppIcon(R.drawable.ic_close, description = "검색 닫기") } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("search_query"),
                        )
                        Spacer(Modifier.height(8.dp))
                        }
                        LibraryControls(state, libraryActions)
                        LibraryBody(state.library, onRetry, if (state.tab == AppTab.SEARCH) state.query else null, onBookInfo, onOpenBook,
                            onAdd = onAddTxt, canAdd = state.importing !is ImportUiState.Working && state.importing !is ImportUiState.SelectEncoding,
                            onClearQuery = { onQueryChanged("") }, catalog = state.catalog, selected = state.selectedBooks, actions = libraryActions)
                    }
                    AppTab.SETTINGS -> SettingsBody(state, onThemeSelected)
                }
            }
        }
        state.selectedBook?.let { book ->
            AlertDialog(onDismissRequest = { onBookInfo(null) }, title = { Text("책 정보") },
                text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                    book.author?.let { Text(it) }
                    Text("${book.format.displayName} · ${android.text.format.Formatter.formatShortFileSize(LocalContext.current, book.sourceByteSize)}")
                    Text("원본 파일: ${book.originalDisplayName}", style = MaterialTheme.typography.bodySmall)
                    book.encodingId?.let { Text("텍스트 인코딩: $it", style = MaterialTheme.typography.bodySmall) }
                    unavailableBookMessage(book.availability)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text("책은 앱 안에 보관됩니다. 원본 파일은 변경하지 않습니다.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { libraryActions.edit(book) }, modifier = Modifier.testTag("edit_book")) { Text("정보 편집") }
                    if (book.format == org.bookreader.mobile.model.BookFormat.TXT && book.availability == BookAvailability.READY)
                        TextButton(onClick = { onBookEncoding(book) }, modifier = Modifier.testTag("book_change_encoding")) { Text("인코딩 변경") }
                } },
                confirmButton = { TextButton(onClick = { onBookInfo(null) }) { Text("닫기") } },
                dismissButton = { TextButton(onClick = { onDeleteRequested(book) }, modifier = Modifier.testTag("book_delete")) { Text("삭제") } })
        }
        BookEditor(state, libraryActions)
        ShelfManager(state, libraryActions)
        state.deletingBook?.let { book ->
            AlertDialog(onDismissRequest = { onDeleteRequested(null) }, title = { Text("책을 삭제할까요?") },
                text = { Text("${book.title}의 앱 내부 복사본과 독서 진행도·북마크가 삭제됩니다. 원본 파일은 삭제하지 않습니다.") },
                confirmButton = { TextButton(onClick = onConfirmDelete, modifier = Modifier.testTag("confirm_delete")) { Text("삭제") } },
                dismissButton = { TextButton(onClick = { onDeleteRequested(null) }) { Text("취소") } })
        }
        state.encodingChange?.let { change ->
            AlertDialog(onDismissRequest = onDismissBookEncoding, title = { Text("이 책의 인코딩 변경") },
                text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        Text("원본 파일은 바꾸지 않습니다. 새 해석에 기록이 없으면 처음부터 읽으며, 이전 진행도와 북마크는 보존됩니다.")
                        Text("현재: ${change.book.encodingId}")
                        if (change.loading) Text("미리보기 준비 중")
                        if (change.working) Text(if (change.committing) "변경 저장 중" else "전체 본문을 확인하는 중")
                        change.error?.let { Text(it, modifier = Modifier.testTag("book_encoding_error")) }
                    }
                    items(TxtEncoding.entries.filter { it.isSupported }) { encoding ->
                        TextButton(enabled = !change.loading && !change.working && encoding.id != change.book.encodingId,
                            onClick = { onChangeBookEncoding(encoding.id) }, modifier = Modifier.testTag("book_encoding_${encoding.name}")) {
                            Text(encoding.id)
                        }
                        change.previews.firstOrNull { it.encoding == encoding }?.text?.let { Text(it.take(160), maxLines = 3) }
                    }
                } }, confirmButton = { TextButton(enabled = !change.committing, onClick = onDismissBookEncoding,
                    modifier = Modifier.testTag("book_encoding_cancel")) { Text("취소") } })
        }
    }
}

@Composable
private fun ImportStatus(state: ImportUiState, onConfirm: () -> Unit, onDismiss: () -> Unit,
    onCancel: () -> Unit, onEncoding: (String) -> Unit, onRead: (Book) -> Unit,
) {
    when (state) {
        ImportUiState.Idle -> Unit
        is ImportUiState.ConfirmShare -> AlertDialog(onDismissRequest = onDismiss,
            title = { Text("공유한 책을 추가할까요?") }, text = { Text("책을 앱 안에 보관합니다. 원본 파일은 변경하지 않습니다.") },
            confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_share")) { Text("추가") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
        is ImportUiState.Working -> Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("import_progress"),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(when (state.progress?.phase) {
                    ImportPhase.VALIDATING -> "책 확인 중"
                    ImportPhase.FINALIZING -> "서재에 저장 중"
                    else -> "파일 복사 중"
                })
                val context = LocalContext.current
                Text(android.text.format.Formatter.formatShortFileSize(context, state.progress?.bytesCopied ?: 0) +
                    (state.progress?.expectedBytes?.let { " / ${android.text.format.Formatter.formatShortFileSize(context, it)}" }
                        ?: " · 전체 크기 확인 중"), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("import_bytes"))
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_import"),
                enabled = state.progress?.phase != ImportPhase.FINALIZING) { Text("취소") }
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is ImportUiState.SelectEncoding -> AlertDialog(onDismissRequest = onCancel,
            title = { Text("텍스트 인코딩 선택") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { Text("자동으로 읽을 수 없습니다. 내부 복사본에 사용할 인코딩을 선택해 주세요.") }
                items(TxtEncoding.entries.filter { it.isSupported }) { encoding ->
                    TextButton(onClick = { onEncoding(encoding.id) }, modifier = Modifier.testTag("encoding_${encoding.name}")) { Text(encoding.id) }
                    state.previews.firstOrNull { it.encoding == encoding }?.text?.let { Text(it.take(160), maxLines = 3) }
                }
            } }, confirmButton = { TextButton(onClick = onCancel) { Text("취소") } })
        is ImportUiState.Complete -> Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("import_success")
            .semantics { liveRegion = LiveRegionMode.Polite }, shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(R.drawable.ic_check)
                    Text(if (state.result.duplicate) "동일한 파일이 서재에 있습니다."
                        else "${state.result.book.format.displayName} 책을 서재에 추가했습니다.",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
                Text(state.result.book.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onRead(state.result.book); onDismiss() }, modifier = Modifier.testTag("import_read")) {
                        AppIcon(R.drawable.ic_book); Spacer(Modifier.size(6.dp)); Text("읽기")
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_import_success")) { Text("닫기") }
                }
            }
        }
        is ImportUiState.Error -> Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("import_error")
            .semantics { liveRegion = LiveRegionMode.Polite }, shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
            Column(Modifier.padding(12.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                Text(state.message, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        }
    }
}

private fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private val AppTab.label: String
    get() = when (this) {
        AppTab.LIBRARY -> "서재"
        AppTab.SEARCH -> "검색"
        AppTab.SETTINGS -> "설정"
    }

@Composable
private fun LibraryBody(state: LibraryState, onRetry: () -> Unit, query: String? = null,
    onInfo: (Book) -> Unit = {}, onOpen: (Book) -> Unit = {},
    onAdd: () -> Unit = {}, canAdd: Boolean = true, onClearQuery: () -> Unit = {},
    catalog: LibraryCatalog = LibraryCatalog(), selected: Set<String> = emptySet(), actions: LibraryActions = LibraryActions(),
) {
    when (state) {
        LibraryState.Loading -> StatusMessage("서재를 불러오는 중", "library_loading") {
            CircularProgressIndicator(Modifier.size(36.dp))
        }
        LibraryState.Empty -> StatusMessage("첫 책을 추가해 보세요", "library_empty",
            "TXT·EPUB·ZIP/CBZ 파일을 보관하고 읽을 수 있어요.") {
            if (query == null) Button(onClick = onAdd, enabled = canAdd,
                modifier = Modifier.testTag("empty_add_book")) {
                AppIcon(R.drawable.ic_add); Spacer(Modifier.size(6.dp)); Text("책 추가")
            }
        }
        is LibraryState.Error -> StatusMessage(
            "서재를 불러오지 못했습니다",
            "library_error",
            "기존 책과 독서 기록은 유지됩니다. 잠시 후 다시 시도해 주세요.",
        ) {
            Button(onClick = onRetry, modifier = Modifier.testTag("library_retry")) { Text("다시 시도") }
        }
        is LibraryState.Content -> {
            val matches = selectLibraryBooks(state.books, catalog, query.orEmpty())
            if (matches.isEmpty()) {
                StatusMessage("검색 결과가 없습니다", "search_empty", "다른 제목이나 작가 이름으로 찾아보세요.") {
                    TextButton(onClick = onClearQuery, modifier = Modifier.testTag("clear_search")) { Text("검색 지우기") }
                    if (catalog.preferences.filter != LibraryFilter.ALL || catalog.preferences.shelfId != null)
                        TextButton(onClick = { actions.preferences { it.copy(filter = LibraryFilter.ALL, shelfId = null) } }) { Text("필터 초기화") }
                }
            } else {
                if (catalog.preferences.view == LibraryView.LIST || LocalDensity.current.fontScale >= 1.5f) {
                    LazyColumn(Modifier.fillMaxSize().testTag("library_content"), contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(matches, key = Book::id) { book -> LibraryBookCard(book, catalog, book.id in selected, selected.isNotEmpty(), true, actions, onInfo, onOpen) }
                    }
                } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("library_content"),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(matches, key = Book::id) { book -> LibraryBookCard(book, catalog, book.id in selected, selected.isNotEmpty(), false, actions, onInfo, onOpen) }
                }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, tag: String, detail: String? = null, action: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (tag != "library_loading") Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            AppIcon(if (tag == "search_empty") R.drawable.ic_search else R.drawable.ic_library,
                modifier = Modifier.padding(16.dp))
        }
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action()
    }
}

@Composable
private fun SettingsBody(state: AppUiState, onThemeSelected: (ThemeMode) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_content"),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("앱 테마", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                            .testTag("theme_${mode.name}")
                            .selectable(
                                selected = state.theme == mode,
                                onClick = { onThemeSelected(mode) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.theme == mode, onClick = null)
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "시스템 설정"
                                ThemeMode.LIGHT -> "밝게"
                                ThemeMode.DARK -> "어둡게"
                            },
                        )
                    }
                }
            }
        }
        if (state.themeError) {
            item {
                Text(
                    "테마 설정을 읽거나 저장하지 못했습니다. 저장된 값은 자동으로 덮어쓰지 않습니다.",
                    modifier = Modifier.testTag("theme_error"),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        item { OpenSourceNotices() }
        item {
            Text("지원 형식", style = MaterialTheme.typography.titleMedium)
            Text("TXT, 암호화되지 않은 EPUB2/3, 정적 JPEG·PNG·WebP를 담은 ZIP/CBZ를 읽습니다. DRM·고정 레이아웃 EPUB, RAR/CBR, 애니메이션 이미지는 지원하지 않습니다. 큰 이미지는 해상도를 낮춰 표시할 수 있습니다.")
        }
        item {
            Text("데이터 보관", style = MaterialTheme.typography.titleMedium)
            Text(
                "책은 앱 내부 관리 복사본을 사용하며 원본은 변경하지 않습니다. " +
                    "앱을 삭제하거나 앱 데이터를 지우면 내부 복사본과 독서 기록이 삭제될 수 있습니다. " +
                    "앱 백업·복원 기능은 제공하지 않습니다.",
            )
        }
    }
}
