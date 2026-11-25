package org.bookreader.mobile.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bookreader.mobile.R
import org.bookreader.mobile.backup.ReadingBackupDialog
import org.bookreader.mobile.encoding.TxtEncoding
import org.bookreader.mobile.importing.ImportPhase
import org.bookreader.mobile.library.*
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.reader.ReaderControlTheme
import org.bookreader.mobile.reader.TxtReaderScreen
import org.bookreader.mobile.repository.LibraryState

@Composable
@Suppress("DEPRECATION") // Android 8/9 need an explicit bar color; newer edge-to-edge uses the surface.
fun BookReaderApp(model: AppViewModel, onAddTxt: () -> Unit = {}, onChooseCover: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val librarySavedState = rememberSaveableStateHolder()
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
    LaunchedEffect(view, dark, reading) {
        withContext(Dispatchers.Main.immediate) {
            (view.context as? Activity)?.let { activity ->
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = !dark && !reading
                    isAppearanceLightNavigationBars = !dark && !reading
                }
            }
        }
    }
    state.comicReader?.let { reader ->
        ReaderControlTheme(darkTheme = true) {
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
        ReaderControlTheme(darkTheme = true) {
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
        ReaderControlTheme(darkTheme = true) {
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
    librarySavedState.SaveableStateProvider("library") {
    BookReaderScreen(state, model::selectTab, model::updateQuery, model::retryLibrary, model::selectTheme,
        onAddTxt, model::confirmShare, model::dismissImport, model::cancelImport, model::selectImportEncoding,
        model::showBook, model::requestDelete, model::confirmDelete, model::openBook,
        model::requestBookEncoding, model::changeBookEncoding, model::dismissBookEncoding, model::dismissNotice,
        LibraryActions(preferences = model::selectLibraryPreferences, edit = model::editBook, save = model::saveBook,
            dismissEditor = model::dismissEditor, chooseCover = onChooseCover, ensureArtwork = model::ensureArtwork,
            showShelves = model::showShelves, createShelf = model::createShelf, renameShelf = model::renameShelf,
            deleteShelf = model::deleteShelf, toggleSelected = model::toggleSelected, clearSelection = model::clearSelection,
            classify = model::classifySelection, dismissBatch = model::dismissBatch),
        onBackup = model.backupController?.let { model::openBackup })
    }
    model.backupController?.let { controller ->
        MaterialTheme(colorScheme = if (dark) BookReaderDarkColorScheme else BookReaderLightColorScheme) {
            ReadingBackupDialog(controller)
        }
    }
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
    onBackup: (() -> Unit)? = null,
) {
    val dark = state.theme.isDark(isSystemInDarkTheme())
    val listScroll = rememberLazyListState()
    val gridScroll = rememberLazyGridState()
    var formatName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFormat = BookFormat.entries.firstOrNull { it.name == formatName }
    val isListLayout = state.catalog.preferences.view == LibraryView.LIST
    val onToggleLayout: (Boolean) -> Unit = { showList ->
        libraryActions.preferences { it.copy(view = if (showList) LibraryView.LIST else LibraryView.GRID) }
    }
    val canAdd = state.importing !is ImportUiState.Working && state.importing !is ImportUiState.SelectEncoding
    val metadataBooks = (state.library as? LibraryState.Content)?.books.orEmpty()
        .filter { it.format == BookFormat.EPUB && it.availability == BookAvailability.READY }
    // Process offscreen EPUB metadata before the user encounters it in search.
    LaunchedEffect(metadataBooks.map { it.id to it.sourceSha256 }) {
        metadataBooks.forEach { libraryActions.ensureArtwork(it) }
    }
    MaterialTheme(colorScheme = if (dark) BookReaderDarkColorScheme else BookReaderLightColorScheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                Column {
                    (state.library as? LibraryState.Content)?.books?.let(::continueBook)?.let { book ->
                        ContinueReadingDock(
                            book = book,
                            percent = state.continuePercent,
                            onOpenBook = onOpenBook,
                            details = state.catalog.details[book.id] ?: BookDetails(),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                    ) {
                        listOf(AppTab.LIBRARY, AppTab.SETTINGS).forEach { tab ->
                            NavigationBarItem(
                                selected = state.tab == tab || tab == AppTab.LIBRARY && state.tab == AppTab.SEARCH,
                                onClick = { onTabSelected(tab) },
                                modifier = Modifier.testTag("tab_${tab.name}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            AppTab.LIBRARY -> LibraryIcons.Shelf
                                            AppTab.SEARCH -> LibraryIcons.Search
                                            AppTab.SETTINGS -> LibraryIcons.Settings
                                        },
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clearAndSetSemantics { },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (state.tab == AppTab.SETTINGS) "설정" else "서재",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("screen_title"),
                        )
                        if (state.tab == AppTab.LIBRARY) {
                            (state.library as? LibraryState.Content)?.let { content ->
                                val filteredCount = if (selectedFormat == null) {
                                    content.books.size
                                } else {
                                    content.books.count { it.format == selectedFormat }
                                }
                                val summary = if (selectedFormat == null) {
                                    "보유 도서 ${content.books.size}권"
                                } else {
                                    "보유 도서 ${content.books.size}권 · ${selectedFormat.name} ${filteredCount}권"
                                }
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.tab != AppTab.SETTINGS) {
                        IconButton(
                            onClick = { onTabSelected(if (state.tab == AppTab.SEARCH) AppTab.LIBRARY else AppTab.SEARCH) },
                            modifier = Modifier.testTag("tab_SEARCH"),
                        ) {
                            Icon(LibraryIcons.Search, contentDescription = "서재 검색")
                        }
                        Button(
                            onClick = onAddTxt,
                            enabled = canAdd,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("add_txt"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Icon(
                                imageVector = LibraryIcons.Add,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clearAndSetSemantics { },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("책 추가", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                state.notice?.let { LibraryNotice(it, onDismissNotice) }
                state.batch?.let { BatchImportStatus(it, libraryActions.dismissBatch) }
                ImportStatus(state.importing, onConfirmShare, onDismissImport, onCancelImport,
                    onEncodingSelected, onOpenBook)
                when (state.tab) {
                    AppTab.LIBRARY, AppTab.SEARCH -> {
                        val searching = state.tab == AppTab.SEARCH
                        if (searching) {
                            val searchCount = (state.library as? LibraryState.Content)?.let {
                                selectLibraryBooks(it.books, state.catalog, state.query).size
                            }
                            SearchBarHeader(
                                query = state.query,
                                resultCount = searchCount,
                                isListLayout = isListLayout,
                                onQueryChanged = onQueryChanged,
                                onCloseSearch = { onQueryChanged(""); onTabSelected(AppTab.LIBRARY) },
                                onToggleLayout = onToggleLayout,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        } else {
                            LibraryToolbarRow(
                                selectedFormat = selectedFormat,
                                isListLayout = isListLayout,
                                onSelectFormat = { formatName = it?.name },
                                onToggleLayout = onToggleLayout,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        LibraryControls(state, libraryActions)
                        LibraryBody(
                            state = state.library,
                            dark = dark,
                            formatFilter = selectedFormat.takeUnless { searching },
                            onClearFormat = { formatName = null },
                            onRetry = onRetry,
                            query = state.query.takeIf { searching },
                            onInfo = onBookInfo,
                            onOpen = onOpenBook,
                            onAdd = onAddTxt,
                            canAdd = canAdd,
                            onClearQuery = { onQueryChanged("") },
                            catalog = state.catalog,
                            selected = state.selectedBooks,
                            actions = libraryActions,
                            listScroll = listScroll,
                            gridScroll = gridScroll,
                        )
                    }
                    AppTab.SETTINGS -> SettingsBodyContent(state, onThemeSelected, onBackup)
                }
            }
        }
        state.selectedBook?.let { book ->
            BookInfoDialog(
                book = book,
                onDismiss = { onBookInfo(null) },
                onDeleteRequested = onDeleteRequested,
                onBookEncoding = onBookEncoding,
                onEdit = libraryActions.edit,
            )
        }
        BookEditor(state, libraryActions)
        ShelfManager(state, libraryActions)
        state.deletingBook?.let { book ->
            DeleteConfirmDialog(
                book = book,
                onDismiss = { onDeleteRequested(null) },
                onConfirm = onConfirmDelete,
            )
        }
        state.encodingChange?.let { change ->
            BookEncodingChangeDialog(
                change = change,
                onChangeBookEncoding = onChangeBookEncoding,
                onDismiss = onDismissBookEncoding,
            )
        }
    }
}

@Composable
private fun ImportStatus(state: ImportUiState, onConfirm: () -> Unit, onDismiss: () -> Unit,
    onCancel: () -> Unit, onEncoding: (String) -> Unit, onRead: (Book) -> Unit,
) {
    when (state) {
        ImportUiState.Idle -> Unit
        is ImportUiState.ConfirmShare -> ShareImportConfirmDialog(
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is ImportUiState.Working -> Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .testTag("import_progress"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (state.progress?.phase) {
                            ImportPhase.VALIDATING -> "책 확인 중"
                            ImportPhase.FINALIZING -> "서재에 저장 중"
                            else -> "파일 복사 중"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatReadableByteSize(state.progress?.bytesCopied ?: 0) +
                            (state.progress?.expectedBytes?.let { " / ${formatReadableByteSize(it)}" } ?: " · 전체 크기 확인 중"),
                        modifier = Modifier.testTag("import_bytes"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("cancel_import"),
                    enabled = state.progress?.phase != ImportPhase.FINALIZING,
                ) { Text("취소") }
            }
        }
        is ImportUiState.SelectEncoding -> ImportSelectEncodingDialog(
            state = state,
            onEncodingSelected = onEncoding,
            onCancel = onCancel,
        )
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
private fun LibraryBody(state: LibraryState, dark: Boolean, formatFilter: BookFormat?,
    onClearFormat: () -> Unit, onRetry: () -> Unit, query: String? = null,
    onInfo: (Book) -> Unit = {}, onOpen: (Book) -> Unit = {},
    onAdd: () -> Unit = {}, canAdd: Boolean = true, onClearQuery: () -> Unit = {},
    catalog: LibraryCatalog = LibraryCatalog(), selected: Set<String> = emptySet(), actions: LibraryActions = LibraryActions(),
    listScroll: LazyListState = rememberLazyListState(), gridScroll: LazyGridState = rememberLazyGridState(),
) {
    when (state) {
        LibraryState.Loading -> LibraryStatusCard("서재를 불러오는 중", "library_loading") {
            CircularProgressIndicator(Modifier.size(36.dp))
        }
        LibraryState.Empty -> LibraryStatusCard("첫 책을 추가해 보세요", "library_empty",
            "TXT·EPUB·ZIP/CBZ 파일을 보관하고 읽을 수 있어요.", icon = R.drawable.ic_library) {
            if (query == null) Button(onClick = onAdd, enabled = canAdd,
                modifier = Modifier.testTag("empty_add_book")) {
                AppIcon(R.drawable.ic_add); Spacer(Modifier.size(6.dp)); Text("책 추가")
            }
        }
        is LibraryState.Error -> LibraryStatusCard(
            "서재를 불러오지 못했습니다",
            "library_error",
            "기존 책과 독서 기록은 유지됩니다. 잠시 후 다시 시도해 주세요.",
            isError = true,
        ) {
            Button(onClick = onRetry, modifier = Modifier.testTag("library_retry")) { Text("다시 시도") }
        }
        is LibraryState.Content -> {
            val matches = selectLibraryBooks(state.books, catalog, query.orEmpty())
                .filter { formatFilter == null || it.format == formatFilter }
            if (matches.isEmpty()) {
                LibraryStatusCard("검색 결과가 없습니다", "search_empty", "다른 제목이나 작가 이름으로 찾아보세요.",
                    icon = R.drawable.ic_search) {
                    if (query != null) TextButton(onClick = onClearQuery, modifier = Modifier.testTag("clear_search")) {
                        Text("검색 지우기")
                    }
                    if (formatFilter != null || catalog.preferences.filter != LibraryFilter.ALL || catalog.preferences.shelfId != null) {
                        TextButton(onClick = {
                            onClearFormat()
                            actions.preferences { it.copy(filter = LibraryFilter.ALL, shelfId = null) }
                        }) { Text("필터 초기화") }
                    }
                }
            } else {
                if (catalog.preferences.view == LibraryView.LIST || LocalDensity.current.fontScale >= 1.5f) {
                    LazyColumn(Modifier.fillMaxSize().testTag("library_content"), state = listScroll, contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(matches, key = Book::id) { book -> LibraryBookCard(book, catalog, book.id in selected, selected.isNotEmpty(), true, dark, actions, onInfo, onOpen) }
                    }
                } else {
                LazyVerticalGrid(
                    state = gridScroll,
                    columns = GridCells.Adaptive(160.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("library_content"),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(matches, key = Book::id) { book -> LibraryBookCard(book, catalog, book.id in selected, selected.isNotEmpty(), false, dark, actions, onInfo, onOpen) }
                }
                }
            }
        }
    }
}
