package org.bookreader.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.bookreader.mobile.encoding.TxtEncoding
import org.bookreader.mobile.model.Book
import org.bookreader.mobile.model.BookAvailability
import org.bookreader.mobile.model.BookFormat

@Composable
internal fun SearchBarHeader(
    query: String,
    resultCount: Int?,
    isListLayout: Boolean,
    onQueryChanged: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onToggleLayout: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("제목·작가·원본 파일명") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(
                    imageVector = LibraryIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        imageVector = LibraryIcons.Close,
                        contentDescription = "검색 닫기",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_query"),
        )

        if (resultCount != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (query.isNotBlank()) "검색 결과 ${resultCount}권" else "전체 도서 ${resultCount}권",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SearchLayoutToggle(
                    isListLayout = isListLayout,
                    onToggleLayout = onToggleLayout,
                )
            }
        }
    }
}

@Composable
private fun SearchLayoutToggle(
    isListLayout: Boolean,
    onToggleLayout: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchLayoutButton(
                selected = isListLayout,
                description = "리스트 보기",
                onClick = { onToggleLayout(true) },
            ) {
                Icon(
                    imageVector = LibraryIcons.ListView,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            SearchLayoutButton(
                selected = !isListLayout,
                description = "카드 보기",
                onClick = { onToggleLayout(false) },
            ) {
                Icon(
                    imageVector = LibraryIcons.GridView,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchLayoutButton(
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = (if (selected) Modifier else Modifier.testTag("library_view")).semantics {
            this.role = Role.Tab
            this.selected = selected
            this.contentDescription = description
        },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
        }
    }
}

@Composable
internal fun BookInfoDialog(
    book: Book,
    onDismiss: () -> Unit,
    onDeleteRequested: (Book) -> Unit,
    onBookEncoding: (Book) -> Unit,
    onEdit: (Book) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("책 정보") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        BookMetaRow(label = "원본 파일", value = book.originalDisplayName)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        BookMetaRow(
                            label = "형식 · 크기",
                            value = buildString {
                                append(book.format.name)
                                append(" · ")
                                append(formatReadableByteSize(book.sourceByteSize))
                                book.encodingId?.takeIf { it.isNotBlank() }?.let {
                                    append(" · ")
                                    append(it)
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                        BookMetaRow(label = "보관 방식", value = "앱 내부 관리 복사본")
                    }
                }
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                unavailableBookMessage(book.availability)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { onEdit(book) }, modifier = Modifier.testTag("edit_book")) { Text("정보 편집") }
                if (book.format == BookFormat.TXT && book.availability == BookAvailability.READY) {
                    Surface(
                        onClick = { onBookEncoding(book) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("book_change_encoding"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "인코딩 변경",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                book.encodingId?.takeIf { it.isNotBlank() }?.let { enc ->
                                    Text(
                                        text = enc,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    imageVector = LibraryIcons.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        dismissButton = {
            TextButton(
                onClick = { onDeleteRequested(book) },
                modifier = Modifier.testTag("book_delete"),
            ) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun BookMetaRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DeleteConfirmDialog(
    book: Book,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("책을 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${book.title}의 앱 내부 복사본과 독서 진행도·북마크가 삭제됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DialogSafetyNote(text = "원본 파일은 삭제하지 않습니다.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_delete"),
            ) {
                Text(
                    text = "삭제",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
internal fun BookEncodingChangeDialog(
    change: BookEncodingUi,
    onChangeBookEncoding: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 책의 인코딩 변경") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "encoding_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "원본 파일은 바꾸지 않습니다. 새 해석에 기록이 없으면 처음부터 읽으며, 이전 진행도와 북마크는 보존됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "현재: ${change.book.encodingId}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (change.loading) {
                            EncodingWorkingRow(message = "미리보기 준비 중")
                        }
                        if (change.working) {
                            EncodingWorkingRow(
                                message = if (change.committing) "변경 저장 중" else "전체 본문을 확인하는 중",
                            )
                        }
                        change.error?.let { err ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            ) {
                                Text(
                                    text = err,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("book_encoding_error"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
                items(
                    items = TxtEncoding.entries.filter { it.isSupported },
                    key = { it.name },
                ) { encoding ->
                    val isCurrent = encoding.id == change.book.encodingId
                    val previewText = change.previews.firstOrNull { it.encoding == encoding }?.text
                    EncodingOptionCard(
                        encodingId = encoding.id,
                        previewText = previewText,
                        isCurrent = isCurrent,
                        enabled = !change.loading && !change.working && !isCurrent,
                        onClick = { onChangeBookEncoding(encoding.id) },
                        modifier = Modifier.testTag("book_encoding_${encoding.name}"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !change.committing,
                onClick = onDismiss,
                modifier = Modifier.testTag("book_encoding_cancel"),
            ) { Text("취소") }
        },
    )
}

@Composable
internal fun ImportSelectEncodingDialog(
    state: ImportUiState.SelectEncoding,
    onEncodingSelected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("텍스트 인코딩 선택") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "import_encoding_header") {
                    Text(
                        text = "자동으로 읽을 수 없습니다. 내부 복사본에 사용할 인코딩을 선택해 주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(
                    items = TxtEncoding.entries.filter { it.isSupported },
                    key = { it.name },
                ) { encoding ->
                    val previewText = state.previews.firstOrNull { it.encoding == encoding }?.text
                    EncodingOptionCard(
                        encodingId = encoding.id,
                        previewText = previewText,
                        isCurrent = false,
                        enabled = true,
                        onClick = { onEncodingSelected(encoding.id) },
                        modifier = Modifier.testTag("encoding_${encoding.name}"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("취소") }
        },
    )
}

@Composable
internal fun ShareImportConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("공유한 TXT를 추가할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "앱 내부에 복사합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                DialogSafetyNote(text = "원본은 변경하지 않습니다.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_share"),
            ) {
                Text("추가", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun EncodingOptionCard(
    encodingId: String,
    previewText: String?,
    isCurrent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = encodingId,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled || isCurrent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (isCurrent) {
                    Text(
                        text = "현재 적용",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            previewText?.let { sample ->
                Text(
                    text = sample.take(160),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EncodingWorkingRow(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DialogSafetyNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
