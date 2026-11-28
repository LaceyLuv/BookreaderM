package org.bookreader.mobile.backup

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class BackupWork { PREPARE, WRITE, VERIFY, RESTORE, RESUME }

internal sealed interface BackupScreen {
    data class Menu(val hasPendingRestore: Boolean = false) : BackupScreen
    data class Working(val work: BackupWork) : BackupScreen
    data object DestinationReady : BackupScreen
    data object ChoosingDestination : BackupScreen
    data object ChoosingSource : BackupScreen
    data class Preview(val overview: BackupOverview, val assets: BackupAssetOverview) : BackupScreen
    data object Exported : BackupScreen
    data class Restored(val outcome: BackupAssetRestoreOutcome) : BackupScreen
    data class Resumed(val summary: BackupRecoverySummary) : BackupScreen
    data class Failed(val problem: BackupProblem, val hasPendingRestore: Boolean = false) : BackupScreen
}

/** One operation and one transient payload/staging session owned by the activity's ViewModel scope. */
internal class BackupController(
    private val backup: AndroidReadingBackup,
    private val scope: CoroutineScope,
    private val onRestoreCommitted: () -> Unit,
) {
    private val mutableState = MutableStateFlow<BackupScreen?>(null)
    val state = mutableState.asStateFlow()
    private var export: AndroidReadingBackup.ExportPlan? = null
    private var prepared: AndroidReadingBackup.PreparedBackupBundle? = null
    private var work: Job? = null

    fun open() {
        if (state.value != null) return
        mutableState.value = BackupScreen.Menu(hasPendingRestore = false)
        scope.launch {
            val pending = runCatching { backup.hasPendingRestore() }.getOrDefault(false)
            if (state.value is BackupScreen.Menu) {
                mutableState.value = BackupScreen.Menu(hasPendingRestore = pending)
            }
        }
    }

    fun dismiss() {
        when (state.value) {
            is BackupScreen.Working, BackupScreen.ChoosingSource, BackupScreen.ChoosingDestination -> return
            else -> {
                val abandoned = prepared
                prepared = null
                export = null
                mutableState.value = null
                if (abandoned != null) {
                    scope.launch { backup.discardPreview(abandoned) }
                }
            }
        }
    }

    fun prepareExport(options: BackupExportOptions = BackupExportOptions()) {
        if (state.value !is BackupScreen.Menu) return
        start(BackupWork.PREPARE) {
            export = backup.prepareExport(options)
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
            openMenuRefreshingPending()
            return
        }
        start(BackupWork.WRITE) {
            backup.writeExport(uri, content)
            BackupScreen.Exported
        }
    }

    fun chooseSource(): Boolean {
        if (state.value !is BackupScreen.Menu) return false
        mutableState.value = BackupScreen.ChoosingSource
        return true
    }

    fun sourceSelected(uri: Uri?) {
        if (state.value != BackupScreen.ChoosingSource) return
        if (uri == null) {
            openMenuRefreshingPending()
            return
        }
        start(BackupWork.VERIFY) {
            val verified = backup.previewBundle(uri)
            prepared?.let { old -> backup.discardPreview(old) }
            prepared = verified
            BackupScreen.Preview(verified.prepared.overview, verified.assets)
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
            val outcome = backup.restoreBundle(input, mode)
            onRestoreCommitted()
            BackupScreen.Restored(outcome)
        }
    }

    fun resumePendingRestore() {
        val current = state.value
        if (current !is BackupScreen.Menu && current !is BackupScreen.Failed) return
        start(BackupWork.RESUME) {
            val summary = backup.recoverInterruptedRestore()
            if (summary.resumed || summary.promotedBooks > 0 || summary.installedFonts > 0) {
                onRestoreCommitted()
            }
            if (summary.pendingRetry) {
                BackupScreen.Failed(BackupProblem.DISK_FULL, hasPendingRestore = true)
            } else {
                BackupScreen.Resumed(summary)
            }
        }
    }

    fun discardPendingRestore() {
        val current = state.value
        if (current !is BackupScreen.Menu && current !is BackupScreen.Failed) return
        start(BackupWork.RESUME) {
            backup.discardInterruptedRestore()
            BackupScreen.Menu(hasPendingRestore = false)
        }
    }

    fun cancelWork() {
        val screen = state.value as? BackupScreen.Working ?: return
        if (screen.work != BackupWork.RESTORE && screen.work != BackupWork.RESUME) work?.cancel()
    }

    private fun openMenuRefreshingPending() {
        mutableState.value = BackupScreen.Menu(hasPendingRestore = false)
        scope.launch {
            val pending = runCatching { backup.hasPendingRestore() }.getOrDefault(false)
            if (state.value is BackupScreen.Menu) {
                mutableState.value = BackupScreen.Menu(hasPendingRestore = pending)
            }
        }
    }

    private fun start(phase: BackupWork, action: suspend () -> BackupScreen) {
        mutableState.value = BackupScreen.Working(phase)
        work = scope.launch {
            mutableState.value = try {
                action()
            } catch (_: TimeoutCancellationException) {
                BackupScreen.Failed(BackupProblem.DOCUMENT_IO, runCatching { backup.hasPendingRestore() }.getOrDefault(false))
            } catch (cancelled: CancellationException) {
                mutableState.value = BackupScreen.Menu(runCatching { backup.hasPendingRestore() }.getOrDefault(false))
                throw cancelled
            } catch (failure: BackupFailure) {
                BackupScreen.Failed(failure.problem, runCatching { backup.hasPendingRestore() }.getOrDefault(false))
            } catch (_: Exception) {
                BackupScreen.Failed(BackupProblem.DOCUMENT_IO, runCatching { backup.hasPendingRestore() }.getOrDefault(false))
            }
        }
    }
}
