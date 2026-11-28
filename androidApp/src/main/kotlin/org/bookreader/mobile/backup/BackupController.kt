package org.bookreader.mobile.backup

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class BackupWork { PREPARE, WRITE, VERIFY, RESTORE }

internal sealed interface BackupScreen {
    data object Menu : BackupScreen
    data class Working(val work: BackupWork) : BackupScreen
    data object DestinationReady : BackupScreen
    data object ChoosingDestination : BackupScreen
    data object ChoosingSource : BackupScreen
    data class Preview(val overview: BackupOverview) : BackupScreen
    data object Exported : BackupScreen
    data class Restored(val result: BackupRestoreResult) : BackupScreen
    data class Failed(val problem: BackupProblem) : BackupScreen
}

/** One operation and one transient payload owned by the activity's ViewModel scope. */
internal class BackupController(
    private val backup: AndroidReadingBackup,
    private val scope: CoroutineScope,
    private val onRestoreCommitted: () -> Unit,
) {
    private val mutableState = MutableStateFlow<BackupScreen?>(null)
    val state = mutableState.asStateFlow()
    private var export: ReadingBackupFile.EncodedReadingBackup? = null
    private var prepared: PreparedReadingBackup? = null
    private var work: Job? = null

    fun open() {
        if (state.value == null) mutableState.value = BackupScreen.Menu
    }

    fun dismiss() {
        when (state.value) {
            is BackupScreen.Working, BackupScreen.ChoosingSource, BackupScreen.ChoosingDestination -> return
            else -> {
                prepared = null
                export = null
                mutableState.value = null
            }
        }
    }

    fun prepareExport() {
        if (state.value != BackupScreen.Menu) return
        start(BackupWork.PREPARE) {
            export = backup.prepareExport()
            BackupScreen.DestinationReady
        }
    }

    fun takeDestinationRequest(): Boolean {
        if (state.value != BackupScreen.DestinationReady) return false
        mutableState.value = BackupScreen.ChoosingDestination
        return true
    }

    fun exportSelected(uri: Uri?) {
        if (state.value != BackupScreen.ChoosingDestination) return
        val content = export
        export = null
        if (uri == null || content == null) {
            mutableState.value = BackupScreen.Menu
            return
        }
        start(BackupWork.WRITE) {
            backup.writeExport(uri, content)
            BackupScreen.Exported
        }
    }

    fun chooseSource(): Boolean {
        if (state.value != BackupScreen.Menu) return false
        mutableState.value = BackupScreen.ChoosingSource
        return true
    }

    fun sourceSelected(uri: Uri?) {
        if (state.value != BackupScreen.ChoosingSource) return
        if (uri == null) {
            mutableState.value = BackupScreen.Menu
            return
        }
        start(BackupWork.VERIFY) {
            val verified = backup.preview(uri)
            prepared = verified
            BackupScreen.Preview(verified.overview)
        }
    }

    fun pickerUnavailable() {
        if (state.value == BackupScreen.ChoosingSource || state.value == BackupScreen.ChoosingDestination) {
            export = null
            mutableState.value = BackupScreen.Failed(BackupProblem.DOCUMENT_IO)
        }
    }

    fun restore(mode: BackupRestoreMode) {
        if (state.value !is BackupScreen.Preview) return
        val input = prepared ?: return
        prepared = null
        start(BackupWork.RESTORE) {
            val result = backup.restore(input, mode)
            onRestoreCommitted()
            BackupScreen.Restored(result)
        }
    }

    fun cancelWork() {
        val screen = state.value as? BackupScreen.Working ?: return
        if (screen.work != BackupWork.RESTORE) work?.cancel()
    }

    private fun start(phase: BackupWork, action: suspend () -> BackupScreen) {
        mutableState.value = BackupScreen.Working(phase)
        work = scope.launch {
            mutableState.value = try {
                action()
            } catch (_: TimeoutCancellationException) {
                BackupScreen.Failed(BackupProblem.DOCUMENT_IO)
            } catch (cancelled: CancellationException) {
                mutableState.value = BackupScreen.Menu
                throw cancelled
            } catch (failure: BackupFailure) {
                BackupScreen.Failed(failure.problem)
            } catch (_: Exception) {
                BackupScreen.Failed(BackupProblem.DOCUMENT_IO)
            }
        }
    }
}
