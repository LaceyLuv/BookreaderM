package org.bookreader.mobile.backup

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.bookreader.mobile.database.BookReaderDatabase
import org.bookreader.mobile.importing.maxImportBytes
import org.bookreader.mobile.importing.withManagedBookOperations
import org.bookreader.mobile.model.BookFormat

/**
 * Coordinates durable, idempotent asset restoration after records commit.
 * Room 9 tables remain unchanged; all mutations serialize under withManagedBookOperations.
 */
class BackupAssetRestoreCoordinator(
    private val database: BookReaderDatabase,
    private val storage: BackupAssetStorage,
    private val readingBackup: RoomReadingBackup,
    private val newId: () -> String,
    private val now: () -> Long,
    private val hook: BackupCommitHook = BackupCommitHook { _, _ -> },
) {
    suspend fun commit(
        prepared: PreparedReadingBackup,
        mode: BackupRestoreMode,
        sessionId: String,
        assets: List<StagedBackupAsset>,
    ): BackupAssetRestoreOutcome = withManagedBookOperations {
        currentCoroutineContext().ensureActive()
        if (storage.readJournal() != null) throw BackupFailure(BackupProblem.CHANGED_LIBRARY)
        val plannedBookIds = buildMap {
            for ((book, target) in prepared.snapshot.books.zip(prepared.targets)) {
                if (target == null) {
                    put(BackupAssetRules.bookKey(book.sha256, book.byteSize), checkedId())
                }
            }
        }
        val planningJournal = BackupRestoreJournal(
            sessionId = sessionId,
            phase = BackupJournalPhase.PLANNING,
            mode = mode.name,
            plannedBookIds = plannedBookIds,
            assets = assets,
        )
        storage.writeJournal(planningJournal)
        hook.reached(BackupCommitCheckpoint.JOURNAL_PLANNING, sessionId)

        val recordResult = try {
            readingBackup.restoreLocked(prepared, mode, plannedBookIds)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: BackupFailure) {
            cleanupQuietly(sessionId)
            throw failure
        } catch (failure: SQLiteException) {
            cleanupQuietly(sessionId)
            throw mapSqlite(failure)
        } catch (failure: Exception) {
            cleanupQuietly(sessionId)
            throw failure
        }
        hook.reached(BackupCommitCheckpoint.DB_COMMITTED, sessionId)

        val committingJournal = planningJournal.copy(phase = BackupJournalPhase.COMMITTING_ASSETS)
        // DB is already committed; if upgrading the journal fails (e.g., DISK_FULL), keep the
        // PLANNING journal and staging intact so recovery detects committed rows and resumes.
        storage.writeJournal(committingJournal)
        hook.reached(BackupCommitCheckpoint.JOURNAL_COMMITTING_ASSETS, sessionId)

        val promoted = promoteAssetsAndCleanup(committingJournal)
        BackupAssetRestoreOutcome(
            records = recordResult,
            promotedBooks = promoted.promotedBooks,
            installedFonts = promoted.installedFonts,
            skippedFonts = promoted.skippedFonts,
        )
    }

    /** Idempotently resumes an interrupted commit or cleans abandoned preview staging directories. */
    suspend fun recoverOrResume(): BackupRecoverySummary = withManagedBookOperations {
        currentCoroutineContext().ensureActive()
        val journal = storage.readJournal()
        val activeSession = journal?.sessionId
        var cleaned = 0
        for (session in storage.listStagingSessions()) {
            if (session != activeSession) {
                storage.deleteStagingSession(session)
                cleaned++
            }
        }
        if (journal == null) {
            return@withManagedBookOperations BackupRecoverySummary(
                resumed = false,
                promotedBooks = 0,
                installedFonts = 0,
                skippedFonts = 0,
                cleanedStagingSessions = cleaned,
                pendingRetry = false,
            )
        }
        val effectiveJournal = when (journal.phase) {
            BackupJournalPhase.PLANNING -> {
                val committed = journal.plannedBookIds.values.any { id -> database.bookDao().findBook(id) != null }
                if (!committed) {
                    storage.deleteStagingSession(journal.sessionId)
                    storage.clearJournal()
                    return@withManagedBookOperations BackupRecoverySummary(
                        resumed = false,
                        promotedBooks = 0,
                        installedFonts = 0,
                        skippedFonts = 0,
                        cleanedStagingSessions = cleaned + 1,
                        pendingRetry = false,
                    )
                }
                val upgraded = journal.copy(phase = BackupJournalPhase.COMMITTING_ASSETS)
                try {
                    storage.writeJournal(upgraded)
                } catch (failure: BackupFailure) {
                    if (failure.problem == BackupProblem.DISK_FULL) {
                        return@withManagedBookOperations BackupRecoverySummary(
                            resumed = false,
                            promotedBooks = 0,
                            installedFonts = 0,
                            skippedFonts = 0,
                            cleanedStagingSessions = cleaned,
                            pendingRetry = true,
                        )
                    }
                    throw failure
                }
                upgraded
            }
            BackupJournalPhase.COMMITTING_ASSETS -> journal
        }
        try {
            val promoted = promoteAssetsAndCleanup(effectiveJournal)
            BackupRecoverySummary(
                resumed = true,
                promotedBooks = promoted.promotedBooks,
                installedFonts = promoted.installedFonts,
                skippedFonts = promoted.skippedFonts,
                cleanedStagingSessions = cleaned,
                pendingRetry = false,
            )
        } catch (failure: BackupFailure) {
            if (failure.problem == BackupProblem.DISK_FULL) {
                BackupRecoverySummary(
                    resumed = false,
                    promotedBooks = 0,
                    installedFonts = 0,
                    skippedFonts = 0,
                    cleanedStagingSessions = cleaned,
                    pendingRetry = true,
                )
            } else {
                throw failure
            }
        }
    }

    suspend fun hasPendingJournal(): Boolean = withManagedBookOperations { storage.readJournal() != null }

    /** Discards only pending journal/staging files without altering committed DB rows or managed books. */
    suspend fun discardPendingJournal() = withManagedBookOperations {
        val journal = storage.readJournal()
        if (journal != null) {
            storage.deleteStagingSession(journal.sessionId)
            storage.clearJournal()
        }
        for (session in storage.listStagingSessions()) {
            storage.deleteStagingSession(session)
        }
    }

    private data class PromotionTotals(val promotedBooks: Int, val installedFonts: Int, val skippedFonts: Int)

    private suspend fun promoteAssetsAndCleanup(journal: BackupRestoreJournal): PromotionTotals {
        val request = currentCoroutineContext()
        val mode = try {
            BackupRestoreMode.valueOf(journal.mode)
        } catch (_: IllegalArgumentException) {
            throw BackupFailure(BackupProblem.FORMAT)
        }
        val plannedIdSet = journal.plannedBookIds.values.toSet()
        var promotedBooks = 0
        var installedFonts = 0
        var skippedFonts = 0

        for (asset in journal.assets) {
            request.ensureActive()
            when (asset.kind) {
                BackupAssetKind.FONT -> {
                    when (storage.installFontAsset(journal.sessionId, asset)) {
                        FontInstallOutcome.INSTALLED -> {
                            installedFonts++
                            hook.reached(BackupCommitCheckpoint.ASSET_PROMOTED, "font:${asset.sha256}")
                        }
                        FontInstallOutcome.ALREADY_PRESENT -> Unit
                        FontInstallOutcome.QUOTA_EXCEEDED -> skippedFonts++
                    }
                }
                BackupAssetKind.BOOK -> {
                    val format = try {
                        BookFormat.valueOf(requireNotNull(asset.format))
                    } catch (_: Exception) {
                        throw BackupFailure(BackupProblem.FORMAT)
                    }
                    val row = database.importDao().findDuplicate(asset.sha256, asset.byteSize) ?: continue
                    if (row.format != format.name || row.availability == "DELETING") continue
                    if (row.availability == "READY") continue
                    if (mode == BackupRestoreMode.NEW_BOOKS_ONLY && row.id !in plannedIdSet) continue
                    if (row.availability != "MISSING" && row.availability != "CORRUPT") continue
                    val revision = row.currentRevision ?: continue

                    val existingFile = storage.managedBookFingerprint(row.managedRelativePath, maxImportBytes(format))
                    if (existingFile != null) {
                        if (existingFile.sha256 != asset.sha256 || existingFile.byteSize != asset.byteSize) {
                            throw BackupFailure(BackupProblem.INTEGRITY)
                        }
                    } else {
                        storage.promoteBookAsset(journal.sessionId, asset, row.managedRelativePath)
                        hook.reached(BackupCommitCheckpoint.ASSET_PROMOTED, "book:${asset.sha256}")
                    }
                    val updated = try {
                        database.bookDao().restoreVerifiedAvailability(
                            id = row.id,
                            revision = revision,
                            hash = asset.sha256,
                            bytes = asset.byteSize,
                            now = now(),
                        )
                    } catch (failure: SQLiteException) {
                        throw mapSqlite(failure)
                    }
                    if (updated > 0) {
                        promotedBooks++
                        hook.reached(BackupCommitCheckpoint.AVAILABILITY_UPDATED, "book:${asset.sha256}")
                    }
                }
            }
        }
        storage.deleteStagingSession(journal.sessionId)
        storage.clearJournal()
        hook.reached(BackupCommitCheckpoint.COMPLETED, journal.sessionId)
        return PromotionTotals(promotedBooks, installedFonts, skippedFonts)
    }

    private suspend fun cleanupQuietly(sessionId: String) {
        runCatching { storage.deleteStagingSession(sessionId) }
        runCatching { storage.clearJournal() }
    }

    private fun mapSqlite(failure: SQLiteException): BackupFailure {
        val msg = failure.message.orEmpty()
        return if ("Error code: 13" in msg || "database or disk is full" in msg.lowercase()) {
            BackupFailure(BackupProblem.DISK_FULL)
        } else {
            BackupFailure(BackupProblem.DOCUMENT_IO)
        }
    }

    private fun checkedId(): String = newId().also { require(uuidPattern.matches(it)) }

    private companion object {
        val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
