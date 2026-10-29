package org.bookreader.mobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.bookreader.mobile.importing.ImportResult
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.reader.ReaderFixtureScreenshot
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollTo
import org.bookreader.mobile.library.*
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
        var opened: String? = null
        var information: String? = null
        compose.setContent {
            BookReaderScreen(
                state.value,
                onTabSelected = { state.value = state.value.copy(tab = it) },
                onQueryChanged = { state.value = state.value.copy(query = it) },
                onRetry = {},
                onThemeSelected = { state.value = state.value.copy(theme = it) },
                onOpenBook = { opened = it.id }, onBookInfo = { information = it?.id },
            )
        }
        compose.onNodeWithTag("tab_LIBRARY").assertIsSelected()
        compose.onNodeWithTag("library_content").assertIsDisplayed()
        compose.onNodeWithTag("book_1").performClick()
        org.junit.Assert.assertEquals("1", opened)
        opened = null
        compose.onNodeWithTag("info_1").performClick()
        org.junit.Assert.assertEquals("1", information)
        org.junit.Assert.assertNull("The info action must not open the reader", opened)
        compose.onNodeWithTag("read_2").performClick()
        org.junit.Assert.assertEquals("2", opened)
        compose.onNodeWithTag("tab_SEARCH").performClick()
        compose.onNodeWithTag("search_query").performTextInput("한글")
        compose.onNodeWithText("한글 책").assertIsDisplayed()
        compose.onNodeWithText("Another Book").assertDoesNotExist()
        compose.onNodeWithTag("tab_SETTINGS").performClick()
        compose.onNodeWithTag("theme_DARK").performClick().assertIsSelected()
        compose.onNodeWithTag("tab_LIBRARY").performClick().assertIsSelected()
        compose.onNodeWithText("Another Book").assertIsDisplayed()
    }

    @Test fun thousandBookGridAndListScrollAndIntegratedSearchUseBoundedVisibleCards() {
        val fixtures=(0 until 1000).map { book(it.toString(),"Generated %04d".format(it)) }
        val state=mutableStateOf(AppUiState(library=LibraryState.Content(fixtures),
            catalog=LibraryCatalog(preferences=LibraryPreferences(sort=LibrarySort.TITLE))))
        compose.setContent {
            BookReaderScreen(state.value,{state.value=state.value.copy(tab=it)},
                {state.value=state.value.copy(query=it)},{},{},libraryActions=LibraryActions(
                    preferences={ change -> state.value=state.value.copy(catalog=state.value.catalog.copy(preferences=change(state.value.catalog.preferences))) }))
        }
        val started=android.os.SystemClock.elapsedRealtime()
        for(view in LibraryView.entries) {
            compose.runOnIdle { state.value=state.value.copy(catalog=state.value.catalog.copy(preferences=state.value.catalog.preferences.copy(view=view))) }
            compose.onNodeWithTag("library_content").performScrollToIndex(999)
            compose.onNodeWithTag("book_999").assertIsDisplayed()
            compose.onNodeWithTag("book_0").assertDoesNotExist()
            compose.onNodeWithTag("tab_SETTINGS").performClick()
            compose.onNodeWithTag("tab_LIBRARY").performClick()
            compose.onNodeWithTag("book_999").assertIsDisplayed()
            compose.onNodeWithTag("library_content").performScrollToIndex(0)
            compose.onNodeWithTag("book_0").assertIsDisplayed()
        }
        compose.onNodeWithTag("tab_SEARCH").performClick()
        compose.onNodeWithTag("search_query").performTextInput("Generated 0917")
        compose.onNodeWithTag("book_917").assertIsDisplayed()
        compose.onNodeWithTag("book_0").assertDoesNotExist()
        val args=androidx.test.platform.app.InstrumentationRegistry.getArguments()
        if(args.getString("captureFixtureScreenshots")=="true") args.getString("additionalTestOutputDir")?.let { path ->
            java.io.File(path,"phase2-thousand-library.txt").apply { parentFile?.mkdirs() }.writeText(
                "generatedBooks=1000\nlayouts=GRID,LIST\nverifiedIndices=999,0\nsearchResult=917\nelapsedMs=${android.os.SystemClock.elapsedRealtime()-started}\nclaim=emulator navigation and semantics; not physical frame rate\n")
        }
    }

    @Test fun metadataSearchFindsAnEpubOutsideTheVisibleLibraryCards() {
        val fixtures=(0 until 100).map { book(it.toString(),"Generated %03d".format(it)) }
            .map { if(it.id=="99")it.copy(format=BookFormat.EPUB) else it }
        val state=mutableStateOf(AppUiState(library=LibraryState.Content(fixtures),
            catalog=LibraryCatalog(preferences=LibraryPreferences(sort=LibrarySort.TITLE))))
        val inspected=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        compose.setContent {
            BookReaderScreen(state.value,{state.value=state.value.copy(tab=it)},
                {state.value=state.value.copy(query=it)},{},{},libraryActions=LibraryActions(ensureArtwork={ candidate ->
                    inspected+=candidate.id
                    if(candidate.id=="99") state.value=state.value.copy(library=LibraryState.Content(fixtures.map {
                        if(it.id=="99")it.copy(author="Offscreen author") else it
                    }))
                }))
        }
        compose.waitUntil(10_000) { "99" in inspected }
        compose.onNodeWithTag("book_99").assertDoesNotExist()
        compose.onNodeWithTag("tab_SEARCH").performClick()
        compose.onNodeWithTag("search_query").performTextInput("Offscreen author")
        compose.onNodeWithTag("book_99").assertIsDisplayed()
        compose.onNodeWithTag("book_author_99", useUnmergedTree = true)
            .assertIsDisplayed().assertTextEquals("Offscreen author")
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

    @Test fun importedFormatsAndDuplicateHaveAccurateMessagesAndSeparateReadDismissActions() {
        val state = mutableStateOf(AppUiState(library = LibraryState.Empty))
        var opened: Book? = null
        compose.setContent {
            BookReaderScreen(state.value, {}, {}, {}, {},
                onOpenBook = { opened = it },
                onDismissImport = { state.value = state.value.copy(importing = ImportUiState.Idle) })
        }
        for ((format, label) in listOf(BookFormat.TXT to "TXT", BookFormat.EPUB to "EPUB", BookFormat.COMIC to "만화")) {
            val fixture = book("import-${format.name}", "한글 책").copy(format = format)
            compose.runOnIdle { state.value = state.value.copy(importing = ImportUiState.Complete(ImportResult.Success(fixture, false))) }
            compose.onNodeWithText("$label 책을 서재에 추가했습니다.").assertIsDisplayed()
            compose.onNodeWithTag("import_read").performClick()
            compose.runOnIdle { assertEquals(fixture, opened) }
            compose.onNodeWithTag("import_success").assertDoesNotExist()
            compose.runOnIdle {
                opened = null
                state.value = state.value.copy(importing = ImportUiState.Complete(ImportResult.Success(fixture, true)))
            }
            compose.onNodeWithText("동일한 파일이 서재에 있습니다.").assertIsDisplayed()
            compose.onNodeWithTag("dismiss_import_success").performClick()
            compose.runOnIdle { assertEquals(null, opened) }
        }
    }

    @Test fun emptySearchAndUnavailableBookOfferActionsWithoutChangingStoredMetadata() {
        val fixture = book("unavailable", "보관한 책").copy(availability = BookAvailability.MISSING)
        val state = mutableStateOf(AppUiState(library = LibraryState.Empty))
        var adds = 0
        compose.setContent {
            BookReaderScreen(state.value, {}, { state.value = state.value.copy(query = it) }, {}, {},
                onAddTxt = { adds++ },
                onOpenBook = { state.value = state.value.copy(notice = unavailableBookMessage(it.availability)) },
                onDismissNotice = { state.value = state.value.copy(notice = null) })
        }
        compose.onNodeWithTag("empty_add_book").performClick()
        compose.runOnIdle {
            assertEquals(1, adds)
            state.value = state.value.copy(library = LibraryState.Content(listOf(fixture)), tab = AppTab.SEARCH, query = "없는 제목")
        }
        compose.onNodeWithTag("search_empty").assertIsDisplayed()
        compose.onNodeWithTag("clear_search").performClick()
        compose.onNodeWithTag("availability_unavailable", useUnmergedTree = true).assertTextEquals("파일 확인 필요")
        compose.onNodeWithTag("book_unavailable").performClick()
        compose.onNodeWithTag("import_notice").assertIsDisplayed()
        compose.onNodeWithTag("dismiss_notice").performClick()
        compose.onNodeWithTag("import_notice").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(fixture), (state.value.library as LibraryState.Content).books) }
    }

    @Test fun largeTextAndDarkThemeKeepImportAndBookActionsReachable() {
        val fixture = book("large", "아주 긴 한글 제목이 있는 책의 첫 번째 이야기").copy(format = BookFormat.EPUB)
        val state = mutableStateOf(AppUiState(theme = ThemeMode.DARK, library = LibraryState.Content(listOf(fixture)),
            importing = ImportUiState.Complete(ImportResult.Success(fixture, false))))
        var opened = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BookReaderScreen(state.value, {}, {}, {}, {}, onOpenBook = { opened = true },
                    onDismissImport = { state.value = state.value.copy(importing = ImportUiState.Idle) },
                    onBookInfo = { state.value = state.value.copy(selectedBook = it) })
            }
        }
        compose.onNodeWithTag("import_read").assertIsDisplayed()
        ReaderFixtureScreenshot.capture("phase1-import-large-dark.png")
        compose.onNodeWithTag("import_read").performClick()
        compose.runOnIdle { assertEquals(true, opened) }
        compose.onNodeWithTag("info_large").assertIsDisplayed().performClick()
        compose.onNodeWithTag("book_delete").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("책 정보").assertDoesNotExist()
        compose.waitForIdle()
        ReaderFixtureScreenshot.capture("phase1-library-large-dark.png")
    }

    private fun book(id: String, title: String) = Book(
        id = id, format = BookFormat.TXT, title = title, originalDisplayName = "$id.txt",
        managedRelativePath = "books/$id/original", sourceSha256 = "a".repeat(64),
        sourceByteSize = 1, currentRevision = "revision-1", addedAt = 1, createdAt = 1, updatedAt = 1,
    )
}
