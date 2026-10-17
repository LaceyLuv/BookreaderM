package org.bookreader.mobile.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bookreader.mobile.locator.TxtLocator

@Composable
fun ReaderSearchDialog(controller: TxtReaderController, onDismiss: () -> Unit) {
    val state by controller.tools.state.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("책 안에서 찾기") }, text = {
        Column {
            OutlinedTextField(value = state.query, onValueChange = { controller.tools.search(it.take(257), state.ignoreAsciiCase) },
                label = { Text("검색어 · 최대 256자") }, modifier = Modifier.fillMaxWidth().testTag("reader_search_query"))
            Row {
                Checkbox(checked = state.ignoreAsciiCase, onCheckedChange = { controller.tools.search(state.query, it) })
                Text("영문 대소문자 구분 안 함")
            }
            if (state.searching) Text("본문에서 찾는 중")
            if (state.searchError) Text("검색하지 못했습니다. 검색어 길이를 확인하고 다시 시도해 주세요.")
            if (state.searchComplete && state.hits.isEmpty()) Text("검색 결과가 없습니다")
            Text("${state.hits.size}개${if (state.searchComplete) " · 검색 완료" else ""}")
            LazyColumn(Modifier.heightIn(max = 320.dp).testTag("reader_search_results")) {
                items(state.hits, key = { it.offset }) { hit ->
                    TextButton(onClick = { controller.tools.jumpFromSearch(hit.offset); onDismiss() },
                        modifier = Modifier.fillMaxWidth().testTag("reader_search_hit_${hit.offset}")) {
                        Text(hit.snippet, maxLines = 3)
                    }
                }
            }
            if (state.searchCapped) Text("처음 1,000개를 표시했습니다. 검색어를 구체적으로 입력해 주세요.")
            else if (state.nextSearchOffset != null && !state.searching && state.query.isNotEmpty())
                TextButton(onClick = controller.tools::moreSearch) { Text("계속 찾기") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

@Composable
fun ReaderBookmarksDialog(controller: TxtReaderController, onDismiss: () -> Unit) {
    val state by controller.tools.state.collectAsStateWithLifecycle()
    var label by remember { mutableStateOf("") }
    LaunchedEffect(controller) { controller.tools.loadBookmarks() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("북마크") }, text = {
        Column {
            OutlinedTextField(value = label, onValueChange = { label = it.take(200) }, label = { Text("이름 (선택)") },
                modifier = Modifier.fillMaxWidth().testTag("reader_bookmark_label"))
            TextButton(enabled = !state.bookmarkWriting && !state.bookmarkReadError && !state.bookmarksLoading,
                onClick = { controller.tools.addBookmark(label) }, modifier = Modifier.testTag("reader_bookmark_add")) { Text("현재 위치 저장") }
            if (state.bookmarksLoading) Text("북마크를 읽는 중")
            if (state.bookmarkReadError) {
                Text("북마크를 읽지 못했습니다. 기존 북마크는 보존됩니다.")
                TextButton(onClick = { controller.tools.loadBookmarks() }) { Text("다시 읽기") }
            }
            if (state.bookmarkWriteError) Text("변경을 저장하지 못했습니다. 다시 시도해 주세요.")
            LazyColumn(Modifier.heightIn(max = 320.dp).testTag("reader_bookmarks_list")) {
                items(state.bookmarks, key = { it.id }) { bookmark ->
                    Row {
                        TextButton(onClick = {
                            (bookmark.locator as? TxtLocator)?.let { controller.navigateTo(it.payload.utf16Offset) }
                            onDismiss()
                        }, modifier = Modifier.weight(1f).testTag("reader_bookmark_${bookmark.id}")) {
                            Text(bookmark.label ?: "저장된 위치", maxLines = 2)
                        }
                        TextButton(enabled = !state.bookmarkWriting,
                            onClick = { controller.tools.deleteBookmark(bookmark.id) }) { Text("삭제") }
                    }
                }
            }
            if (state.bookmarks.size >= 1000 && state.bookmarkCursor != null) Text("처음 1,000개를 표시했습니다.")
            else if (state.bookmarkCursor != null && !state.bookmarksLoading)
                TextButton(onClick = { controller.tools.loadBookmarks(more = true) }) { Text("더 보기") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}
