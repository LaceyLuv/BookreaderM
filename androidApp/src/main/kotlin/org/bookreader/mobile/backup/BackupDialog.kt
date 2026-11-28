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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
        BackupScreen.Menu -> AlertDialog(
            onDismissRequest = controller::dismiss,
            title = { Text("독서 기록 백업") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("책 정보, 진행도, 북마크, 메모, 목차 수정, 책장과 읽기 설정을 파일로 저장할 수 있습니다.")
                    Text("책·글꼴·표지 파일은 따로 보관해 주세요. 백업에는 메모와 선택한 본문이 들어갑니다.")
                }
            },
            confirmButton = { TextButton(onClick = controller::prepareExport, modifier = Modifier.testTag("backup_export")) { Text("백업 저장") } },
            dismissButton = {
                TextButton(onClick = {
                    if (controller.chooseSource()) try {
                        open.launch(arrayOf("*/*"))
                    } catch (_: ActivityNotFoundException) {
                        controller.pickerUnavailable()
                    }
                }, modifier = Modifier.testTag("backup_restore_pick")) { Text("백업 열기") }
            },
        )
        is BackupScreen.Working -> AlertDialog(
            onDismissRequest = {},
            title = { Text(when (screen.work) {
                BackupWork.PREPARE -> "독서 기록 모으는 중"
                BackupWork.WRITE -> "백업 파일 저장 중"
                BackupWork.VERIFY -> "백업 파일 확인 중"
                BackupWork.RESTORE -> "서재에 추가하는 중"
            }) },
            text = { CircularProgressIndicator(Modifier.testTag("backup_busy")) },
            confirmButton = {
                if (screen.work != BackupWork.RESTORE) TextButton(onClick = controller::cancelWork) { Text("취소") }
            },
        )
        is BackupScreen.Preview -> RestoreBackupPreview(screen.overview, controller::restore, controller::dismiss)
        BackupScreen.Exported -> BackupNotice("백업을 저장했습니다", "선택한 위치에서 백업 파일을 찾을 수 있습니다.", "backup_exported", controller::dismiss)
        is BackupScreen.Restored -> BackupNotice("복원했습니다",
            with(screen.result) { "새 책 ${newBooks}권 · 기록을 추가한 기존 책 ${existingBooksWithAdditions}권\n추가된 책장 ${addedShelves}개 · 기록 ${addedRecords}개" +
                if (newBooks > 0) "\n새로 복원한 책을 읽으려면 책 추가에서 같은 원본 파일을 선택해 주세요." else "" },
            "backup_restored", controller::dismiss)
        is BackupScreen.Failed -> BackupNotice("백업 작업을 마치지 못했습니다", screen.problem.message(), "backup_failed", controller::dismiss)
        BackupScreen.DestinationReady, BackupScreen.ChoosingSource, BackupScreen.ChoosingDestination -> Unit
    }
}

@Composable
private fun RestoreBackupPreview(overview: BackupOverview, restore: (BackupRestoreMode) -> Unit, close: () -> Unit) {
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
                Text("저장된 기록은 유지합니다. 새 책은 같은 원본을 다시 추가하면 읽을 수 있습니다.")
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

private fun BackupProblem.message() = when (this) {
    BackupProblem.FORMAT -> "BookReaderM의 독서 기록 백업 파일을 선택해 주세요."
    BackupProblem.VERSION -> "이 버전의 앱에서 열 수 없는 백업입니다."
    BackupProblem.INTEGRITY -> "백업이 잘렸거나 내용이 달라졌습니다. 다른 백업을 선택해 주세요."
    BackupProblem.LIMIT -> "백업 크기 또는 서재의 보관 한도를 초과했습니다. 기존 기록은 유지됩니다."
    BackupProblem.CHANGED_LIBRARY -> "확인하는 동안 서재가 달라졌습니다. 백업을 다시 열어 주세요."
    BackupProblem.DOCUMENT_IO -> "파일이나 독서 기록에 접근하지 못했습니다. 기존 기록은 유지됩니다."
}
