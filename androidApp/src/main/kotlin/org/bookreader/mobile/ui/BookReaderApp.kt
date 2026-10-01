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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.repository.LibraryState

@Composable
fun BookReaderApp(model: AppViewModel) {
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
    BackHandler(enabled = state.tab != AppTab.LIBRARY) { model.selectTab(AppTab.LIBRARY) }
    BookReaderScreen(state, model::selectTab, model::updateQuery, model::retryLibrary, model::selectTheme)
}

@Composable
fun BookReaderScreen(
    state: AppUiState,
    onTabSelected: (AppTab) -> Unit,
    onQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
) {
    val dark = state.theme.isDark(isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Scaffold(
            bottomBar = {
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
            },
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
                Text(
                    state.tab.label,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 20.dp).testTag("screen_title"),
                )
                when (state.tab) {
                    AppTab.LIBRARY -> LibraryBody(state.library, onRetry)
                    AppTab.SEARCH -> {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChanged,
                            label = { Text("제목·작가·원본 파일명") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("search_query"),
                        )
                        Spacer(Modifier.height(16.dp))
                        LibraryBody(state.library, onRetry, state.query)
                    }
                    AppTab.SETTINGS -> SettingsBody(state, onThemeSelected)
                }
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
private fun LibraryBody(state: LibraryState, onRetry: () -> Unit, query: String? = null) {
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
                    items(matches, key = Book::id) { BookMetadata(it) }
                }
            }
        }
    }
}

@Composable
private fun BookMetadata(book: Book) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(book.format.name, style = MaterialTheme.typography.labelMedium)
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
