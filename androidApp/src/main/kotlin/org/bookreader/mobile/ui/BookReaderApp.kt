package org.bookreader.mobile.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import org.bookreader.mobile.reader.TxtReaderScreen
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.importing.ImportPhase
import org.bookreader.mobile.encoding.TxtEncoding
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.repository.LibraryState

@Composable
fun BookReaderApp(model: AppViewModel, onAddTxt: () -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val dark = state.theme.isDark(isSystemInDarkTheme())
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    state.reader?.let { reader ->
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                state.notice?.let { Text(it, modifier = Modifier.testTag("import_notice"), color = MaterialTheme.colorScheme.error) }
                if (state.importing is ImportUiState.ConfirmShare || state.importing is ImportUiState.SelectEncoding ||
                    state.importing is ImportUiState.Working || state.importing is ImportUiState.Complete || state.importing is ImportUiState.Error) {
                    ImportStatus(state.importing, model::confirmShare, model::dismissImport, model::cancelImport,
                        model::selectImportEncoding, model::openBook)
                }
                TxtReaderScreen(reader, model::leaveReader)
            }
            }
        }
        return
    }
    BackHandler(enabled = state.tab != AppTab.LIBRARY) { model.selectTab(AppTab.LIBRARY) }
    BookReaderScreen(state, model::selectTab, model::updateQuery, model::retryLibrary, model::selectTheme,
        onAddTxt, model::confirmShare, model::dismissImport, model::cancelImport, model::selectImportEncoding,
        model::showBook, model::requestDelete, model::confirmDelete, model::openBook)
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
) {
    val dark = state.theme.isDark(isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            bottomBar = {
                Column {
                (state.library as? LibraryState.Content)?.books?.let(::continueBook)?.let { book ->
                    TextButton(onClick = { onOpenBook(book) }, modifier = Modifier.fillMaxWidth().testTag("continue_reading")) {
                        Text("이어읽기 · ${book.title} · " + (state.continuePercent?.let { "${it.toInt()}%" } ?: "진행률 확인 필요"))
                    }
                }
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = state.tab == tab,
                            onClick = { onTabSelected(tab) },
                            modifier = Modifier.testTag("tab_${tab.name}"),
                            icon = {
                                Text(
                                    when (tab) {
                                        AppTab.LIBRARY -> "▤"
                                        AppTab.SEARCH -> "⌕"
                                        AppTab.SETTINGS -> "⚙"
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
                    Text(state.tab.label, style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.testTag("screen_title"))
                    if (state.tab == AppTab.LIBRARY) Button(onClick = onAddTxt,
                        enabled = state.importing !is ImportUiState.Working && state.importing !is ImportUiState.SelectEncoding,
                        modifier = Modifier.testTag("add_txt")) { Text("TXT 추가") }
                }
                state.notice?.let { Text(it, modifier = Modifier.testTag("import_notice"), color = MaterialTheme.colorScheme.error) }
                ImportStatus(state.importing, onConfirmShare, onDismissImport, onCancelImport,
                    onEncodingSelected, onOpenBook)
                when (state.tab) {
                    AppTab.LIBRARY -> LibraryBody(state.library, onRetry, onInfo = onBookInfo, onOpen = onOpenBook)
                    AppTab.SEARCH -> {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChanged,
                            label = { Text("제목·작가·원본 파일명") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("search_query"),
                        )
                        Spacer(Modifier.height(16.dp))
                        LibraryBody(state.library, onRetry, state.query, onBookInfo, onOpenBook)
                    }
                    AppTab.SETTINGS -> SettingsBody(state, onThemeSelected)
                }
            }
        }
        state.selectedBook?.let { book ->
            AlertDialog(onDismissRequest = { onBookInfo(null) }, title = { Text("책 정보") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(book.title); Text(book.originalDisplayName); Text("${book.sourceByteSize} bytes · ${book.encodingId.orEmpty()}")
                    Text("앱 내부 관리 복사본")
                } },
                confirmButton = { TextButton(onClick = { onBookInfo(null) }) { Text("닫기") } },
                dismissButton = { TextButton(onClick = { onDeleteRequested(book) }, modifier = Modifier.testTag("book_delete")) { Text("삭제") } })
        }
        state.deletingBook?.let { book ->
            AlertDialog(onDismissRequest = { onDeleteRequested(null) }, title = { Text("책을 삭제할까요?") },
                text = { Text("${book.title}의 앱 내부 복사본과 독서 진행도·북마크가 삭제됩니다. 원본 파일은 삭제하지 않습니다.") },
                confirmButton = { TextButton(onClick = onConfirmDelete, modifier = Modifier.testTag("confirm_delete")) { Text("삭제") } },
                dismissButton = { TextButton(onClick = { onDeleteRequested(null) }) { Text("취소") } })
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
            title = { Text("공유한 TXT를 추가할까요?") }, text = { Text("앱 내부에 복사합니다. 원본은 변경하지 않습니다.") },
            confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_share")) { Text("추가") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
        is ImportUiState.Working -> Row(Modifier.fillMaxWidth().testTag("import_progress"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(when (state.progress?.phase) {
                    ImportPhase.VALIDATING -> "텍스트 확인 중"
                    ImportPhase.FINALIZING -> "서재에 저장 중"
                    else -> "파일 복사 중"
                })
                Text("${state.progress?.bytesCopied ?: 0} bytes" + (state.progress?.expectedBytes?.let { " / $it bytes" } ?: " · 전체 크기 미상"))
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_import"),
                enabled = state.progress?.phase != ImportPhase.FINALIZING) { Text("취소") }
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
        is ImportUiState.Complete -> Row(Modifier.fillMaxWidth().testTag("import_success"),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.result.duplicate) "동일한 파일이 서재에 있습니다." else "TXT를 서재에 추가했습니다.")
            TextButton(onClick = { onRead(state.result.book); onDismiss() }, modifier = Modifier.testTag("import_read")) { Text("읽기") }
        }
        is ImportUiState.Error -> Row(Modifier.fillMaxWidth().testTag("import_error"), verticalAlignment = Alignment.CenterVertically) {
            Text(state.message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismiss) { Text("닫기") }
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
) {
    when (state) {
        LibraryState.Loading -> StatusMessage("서재를 불러오는 중", "library_loading") {
            CircularProgressIndicator(Modifier.size(36.dp))
        }
        LibraryState.Empty -> StatusMessage("서재가 비어 있습니다", "library_empty")
        is LibraryState.Error -> StatusMessage(
            "서재를 불러오지 못했습니다. 기존 책과 독서 기록은 유지됩니다.",
            "library_error",
        ) {
            Button(onClick = onRetry, modifier = Modifier.testTag("library_retry")) { Text("다시 시도") }
        }
        is LibraryState.Content -> {
            val matches = filterLibraryBooks(state.books, query.orEmpty())
            if (matches.isEmpty()) {
                StatusMessage("검색 결과가 없습니다", "search_empty")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("library_content"),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(matches, key = Book::id) { BookMetadata(it, onInfo, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun BookMetadata(book: Book, onInfo: (Book) -> Unit, onOpen: (Book) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(book.format.name, style = MaterialTheme.typography.labelMedium)
            Row {
                TextButton(onClick = { onOpen(book) }, modifier = Modifier.testTag("read_${book.id}")) { Text("읽기") }
                TextButton(onClick = { onInfo(book) }, modifier = Modifier.testTag("info_${book.id}")) { Text("책 정보") }
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, tag: String, action: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
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
