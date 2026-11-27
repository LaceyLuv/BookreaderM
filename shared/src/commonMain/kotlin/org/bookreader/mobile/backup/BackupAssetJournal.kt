package org.bookreader.mobile.backup

import kotlinx.serialization.Serializable
import org.bookreader.mobile.importing.FileFingerprint

@Serializable
enum class BackupJournalPhase { PLANNING, COMMITTING_ASSETS }

@Serializable
data class StagedBackupAsset(
    val kind: BackupAssetKind,
    val stagingName: String,
    val sha256: String,
    val byteSize: Long,
    val format: String? = null,
    val label: String? = null,
)

/** Durable journal for resumable asset promotion across ENOSPC or external force-stop. */
@Serializable
data class BackupRestoreJournal(
    val sessionId: String,
    val phase: BackupJournalPhase,
    val mode: String,
    val plannedBookIds: Map<String, String>,
    val assets: List<StagedBackupAsset>,
)

enum class BackupCommitCheckpoint {
    JOURNAL_PLANNING,
    DB_COMMITTED,
    JOURNAL_COMMITTING_ASSETS,
    ASSET_PROMOTED,
    AVAILABILITY_UPDATED,
    COMPLETED,
}

/** Tests throw an Error or pause at a checkpoint to simulate crash/force-stop. Production uses a no-op. */
fun interface BackupCommitHook {
    suspend fun reached(checkpoint: BackupCommitCheckpoint, detail: String)
}

enum class FontInstallOutcome { INSTALLED, ALREADY_PRESENT, QUOTA_EXCEEDED }

/** Platform storage boundary for private staging, durable journal, managed book promotion, and user fonts. */
interface BackupAssetStorage {
    suspend fun readJournal(): BackupRestoreJournal?
    suspend fun writeJournal(journal: BackupRestoreJournal)
    suspend fun clearJournal()
    suspend fun listStagingSessions(): List<String>
    suspend fun deleteStagingSession(sessionId: String)
    suspend fun managedBookFingerprint(relativePath: String, maxBytes: Long): FileFingerprint?
    suspend fun promoteBookAsset(sessionId: String, asset: StagedBackupAsset, finalRelativePath: String)
    suspend fun installFontAsset(sessionId: String, asset: StagedBackupAsset): FontInstallOutcome
}

data class BackupAssetRestoreOutcome(
    val records: BackupRestoreResult,
    val promotedBooks: Int,
    val installedFonts: Int,
    val skippedFonts: Int,
)

data class BackupRecoverySummary(
    val resumed: Boolean,
    val promotedBooks: Int,
    val installedFonts: Int,
    val skippedFonts: Int,
    val cleanedStagingSessions: Int,
    val pendingRetry: Boolean,
)
