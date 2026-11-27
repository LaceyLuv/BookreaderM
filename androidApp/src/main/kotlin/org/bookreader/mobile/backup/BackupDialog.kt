package org.bookreader.mobile.backup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ReadingBackupDialog(controller: BackupController) {
    val screen = controller.state.collectAsStateWithLifecycle().value
    val createContract = remember {
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: Context, input: String) =
                super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
        }
    }
    val openContract = remember {
        object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>) =
                super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE)
        }
    }
    val create = rememberLauncherForActivityResult(createContract, controller::exportSelected)
    val open = rememberLauncherForActivityResult(openContract, controller::sourceSelected)
    LaunchedEffect(screen) {
        if (screen == BackupScreen.DestinationReady && controller.takeDestinationRequest()) {
            try {
                create.launch("BookReaderM-독서기록.brmbackup")
            } catch (_: ActivityNotFoundException) {
                controller.pickerUnavailable()
            }
        }
    }
    if (screen == null) return
    when (screen) {
        is BackupScreen.Menu -> BackupMenuDialog(
            hasPendingRestore = screen.hasPendingRestore,
            onExport = controller::prepareExport,
            onPickSource = {
                if (controller.chooseSource()) try {
                    open.launch(arrayOf("*/*"))
                } catch (_: ActivityNotFoundException) {
                    controller.pickerUnavailable()
                }
            },
            onResumePending = controller::resumePendingRestore,
            onDiscardPending = controller::discardPendingRestore,
            onDismiss = controller::dismiss,
        )
        is BackupScreen.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text(when (screen.work) {
                BackupWork.PREPARE -> "백업 준비 중"
                BackupWork.WRITE -> "백업 파일 저장 중"
                BackupWork.VERIFY -> "백업 파일 확인 중"
                BackupWork.RESTORE -> "서재에 추가하는 중"
                BackupWork.RESUME -> "중단된 복원 정리 중"
            }) },
            text = { CircularProgressIndicator(Modifier.testTag("backup_busy")) },
            confirmButton = {
                if (screen.work != BackupWork.RESTORE && screen.work != BackupWork.RESUME) {
                    TextButton(onClick = controller::cancelWork) { Text("취소") }
                }
            },
        )
        is BackupScreen.Preview -> RestoreBackupPreview(screen.overview, screen.assets, controller::restore, controller::dismiss)
        BackupScreen.Exported -> BackupNotice("백업을 저장했습니다", "선택한 위치에서 백업 파일을 찾을 수 있습니다.", "backup_exported", controller::dismiss)
        is BackupScreen.Restored -> BackupNotice(
            "복원했습니다",
            buildRestoredMessage(screen.outcome),
            "backup_restored",
            controller::dismiss,
        )
        is BackupScreen.Resumed -> BackupNotice(
            "중단된 복원을 완료했습니다",
            "복원한 책 원본 ${screen.summary.promotedBooks}권 · 등록한 글꼴 ${screen.summary.installedFonts}개",
            "backup_restored",
            controller::dismiss,
        )
        is BackupScreen.Failed -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("백업 작업을 마치지 못했습니다", Modifier.testTag("backup_failed")) },
            text = { Text(screen.problem.message(), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                if (screen.hasPendingRestore) {
                    TextButton(
                        onClick = controller::resumePendingRestore,
                        modifier = Modifier.testTag("backup_resume_pending"),
                    ) { Text("복원 이어하기") }
                } else {
                    TextButton(onClick = controller::dismiss, modifier = Modifier.testTag("backup_close")) { Text("닫기") }
                }
            },
            dismissButton = if (screen.hasPendingRestore) {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = controller::discardPendingRestore,
                            modifier = Modifier.testTag("backup_discard_pending"),
                        ) { Text("임시 파일 정리") }
                        TextButton(onClick = controller::dismiss, modifier = Modifier.testTag("backup_close")) { Text("닫기") }
                    }
                }
            } else {
                null
            },
        )
        BackupScreen.DestinationReady, BackupScreen.ChoosingSource, BackupScreen.ChoosingDestination -> Unit
    }
}

@Composable
private fun BackupMenuDialog(
    hasPendingRestore: Boolean,
    onExport: (BackupExportOptions) -> Unit,
    onPickSource: () -> Unit,
    onResumePending: () -> Unit,
    onDiscardPending: () -> Unit,
    onDismiss: () -> Unit,
) {
    var includeBooks by rememberSaveable { mutableStateOf(false) }
    var includeFonts by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("독서 기록 백업") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("책 정보, 진행도, 북마크, 메모, 목차 수정, 책장과 읽기 설정을 파일로 저장할 수 있습니다.")
                Text("필요하면 책 원본과 등록한 사용자 글꼴도 함께 담을 수 있습니다. 백업에는 메모와 선택한 본문이 들어갑니다.")
                if (hasPendingRestore) {
                    HorizontalDivider()
                    Text(
                        "이전에 중단된 백업 복원이 남아 있습니다. 저장 공간을 확인한 뒤 이어서 완료하거나 임시 파일을 정리할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onResumePending,
                            modifier = Modifier.testTag("backup_resume_pending"),
                        ) { Text("복원 이어하기") }
                        TextButton(
                            onClick = onDiscardPending,
                            modifier = Modifier.testTag("backup_discard_pending"),
                        ) { Text("임시 파일 정리") }
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth()
                        .testTag("backup_include_books")
                        .toggleable(value = includeBooks, onValueChange = { includeBooks = it }, role = Role.Checkbox),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeBooks, onCheckedChange = null)
                    Text("책 원본 파일 함께 저장")
                }
                Row(
                    Modifier.fillMaxWidth()
                        .testTag("backup_include_fonts")
                        .toggleable(value = includeFonts, onValueChange = { includeFonts = it }, role = Role.Checkbox),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeFonts, onCheckedChange = null)
                    Text("등록한 사용자 글꼴 함께 저장")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(BackupExportOptions(includeBooks = includeBooks, includeFonts = includeFonts)) },
                modifier = Modifier.testTag("backup_export"),
            ) { Text("백업 저장") }
        },
        dismissButton = {
            TextButton(onClick = onPickSource, modifier = Modifier.testTag("backup_restore_pick")) { Text("백업 열기") }
        },
    )
}

@Composable
private fun RestoreBackupPreview(
    overview: BackupOverview,
    assets: BackupAssetOverview,
    restore: (BackupRestoreMode) -> Unit,
    close: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(BackupRestoreMode.NEW_BOOKS_ONLY) }
    val date = remember(overview.createdAt) { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(overview.createdAt)) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("복원할 기록 확인") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("backup_preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("백업 날짜 $date")
                Text("새 책 ${overview.newBooks}권 · 같은 원본 ${overview.matchedBooks}권")
                if (overview.unavailableMatches > 0) Text("형식이 다르거나 삭제 중인 책 ${overview.unavailableMatches}권은 건너뜁니다.")
                Text("진행도 ${overview.progress}개 · 북마크 ${overview.bookmarks}개 · 형광펜과 메모 ${overview.highlights}개")
                Text("책장 ${overview.shelves}개 · 목차 수정 ${overview.tocEdits}개" +
                    if (overview.hasSettings) " · 읽기 설정 포함" else "")
                if (assets.totalFiles > 0) {
                    Text(
                        "포함된 파일: 책 원본 ${assets.bookFiles}권 · 사용자 글꼴 ${assets.fontFiles}개 (${formatBytes(assets.totalBytes)})",
                        modifier = Modifier.testTag("backup_preview_assets"),
                    )
                }
                Text(
                    if (assets.bookFiles > 0) {
                        "저장된 기록과 기존 책 원본은 유지합니다. 포함된 책 원본과 글꼴은 복원 후 바로 사용할 수 있습니다."
                    } else {
                        "저장된 기록은 유지합니다. 새 책은 같은 원본을 다시 추가하면 읽을 수 있습니다."
                    },
                )
                Column(Modifier.selectableGroup()) {
                    for (option in BackupRestoreMode.entries) {
                        Row(
                            Modifier.fillMaxWidth().testTag("backup_mode_${option.name}")
                                .selectable(selected = mode == option, onClick = { mode = option }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == option, onClick = null)
                            Text(when (option) {
                                BackupRestoreMode.NEW_BOOKS_ONLY -> "새 책 기록만 복원"
                                BackupRestoreMode.ADD_MISSING_RECORDS -> "기존 책에도 없는 기록 추가"
                            })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { restore(mode) }, modifier = Modifier.testTag("backup_commit")) { Text("복원") } },
        dismissButton = { TextButton(onClick = close) { Text("취소") } },
    )
}

@Composable
private fun BackupNotice(title: String, message: String, tag: String, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text(title, Modifier.testTag(tag)) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = close, modifier = Modifier.testTag("backup_close")) { Text("닫기") } },
    )
}

private fun buildRestoredMessage(outcome: BackupAssetRestoreOutcome): String = buildString {
    val result = outcome.records
    append("새 책 ${result.newBooks}권 · 기록을 추가한 기존 책 ${result.existingBooksWithAdditions}권\n")
    append("추가된 책장 ${result.addedShelves}개 · 기록 ${result.addedRecords}개")
    if (outcome.promotedBooks > 0 || outcome.installedFonts > 0 || outcome.skippedFonts > 0) {
        append("\n복원한 책 원본 ${outcome.promotedBooks}권 · 등록한 글꼴 ${outcome.installedFonts}개")
        if (outcome.skippedFonts > 0) append(" (글꼴 한도로 건너뜀 ${outcome.skippedFonts}개)")
    }
    if (result.newBooks > outcome.promotedBooks) {
        append("\n새로 복원한 책을 읽으려면 책 추가에서 같은 원본 파일을 선택해 주세요.")
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> "${bytes} B"
}

private fun BackupProblem.message() = when (this) {
    BackupProblem.FORMAT -> "BookReaderM의 독서 기록 백업 파일을 선택해 주세요."
    BackupProblem.VERSION -> "이 버전의 앱에서 열 수 없는 백업입니다."
    BackupProblem.INTEGRITY -> "백업이 잘렸거나 내용이 달라졌습니다. 다른 백업을 선택해 주세요."
    BackupProblem.LIMIT -> "백업 크기 또는 서재의 보관 한도를 초과했습니다. 기존 기록은 유지됩니다."
    BackupProblem.CHANGED_LIBRARY -> "확인하는 동안 서재가 달라졌습니다. 백업을 다시 열어 주세요."
    BackupProblem.DOCUMENT_IO -> "파일이나 독서 기록에 접근하지 못했습니다. 기존 기록은 유지됩니다."
    BackupProblem.DISK_FULL -> "저장 공간이 부족해 복원을 마치지 못했습니다. 기존 책과 기록은 유지됩니다. 공간을 확보한 뒤 다시 이어서 진행하거나 임시 파일을 정리해 주세요."
}
