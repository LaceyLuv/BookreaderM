package org.bookreader.mobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookFormat
import org.bookreader.mobile.repository.LibraryErrorCode
import org.bookreader.mobile.repository.LibraryState
import org.junit.Rule
import org.junit.Test

/** Compose semantics/device checks; not a process-death or Room smoke test. */
class BookReaderScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun threeTabsMetadataSearchAndThemeSelectionWork() {
        val state = mutableStateOf(AppUiState(library = LibraryState.Content(listOf(
            book("1", "한글 책"), book("2", "Another Book"),
        ))))
        compose.setContent {
            BookReaderScreen(
                state.value,
                onTabSelected = { state.value = state.value.copy(tab = it) },
                onQueryChanged = { state.value = state.value.copy(query = it) },
                onRetry = {},
                onThemeSelected = { state.value = state.value.copy(theme = it) },
            )
        }
        compose.onNodeWithTag("tab_LIBRARY").assertIsSelected()
        compose.onNodeWithTag("library_content").assertIsDisplayed()
        compose.onNodeWithTag("tab_SEARCH").performClick()
        compose.onNodeWithTag("search_query").performTextInput("한글")
        compose.onNodeWithText("한글 책").assertIsDisplayed()
        compose.onNodeWithText("Another Book").assertDoesNotExist()
        compose.onNodeWithTag("tab_SETTINGS").performClick()
        compose.onNodeWithTag("theme_DARK").performClick().assertIsSelected()
        compose.onNodeWithTag("tab_LIBRARY").performClick().assertIsSelected()
        compose.onNodeWithText("Another Book").assertIsDisplayed()
    }

    @Test fun loadingEmptyAndErrorAreDistinctAndErrorHasRetry() {
        val state = mutableStateOf(AppUiState())
        compose.setContent {
            BookReaderScreen(state.value, {}, {}, {
                state.value = state.value.copy(library = LibraryState.Empty)
            }, {})
        }
        compose.onNodeWithTag("library_loading").assertIsDisplayed()
        compose.onNodeWithTag("library_empty").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(library = LibraryState.Error(LibraryErrorCode.DATABASE_UNAVAILABLE))
        }
        compose.onNodeWithTag("library_error").assertIsDisplayed()
        compose.onNodeWithTag("library_empty").assertDoesNotExist()
        compose.onNodeWithTag("library_retry").performClick()
        compose.onNodeWithTag("library_empty").assertIsDisplayed()
        compose.onNodeWithTag("library_error").assertDoesNotExist()
    }

    private fun book(id: String, title: String) = Book(
        id = id, format = BookFormat.TXT, title = title, originalDisplayName = "$id.txt",
        managedRelativePath = "books/$id/original", sourceSha256 = "a".repeat(64),
        sourceByteSize = 1, currentRevision = "revision-1", addedAt = 1, createdAt = 1, updatedAt = 1,
    )
}
