package com.e4eheh.e4ehehworkout.domain.backup

import java.time.Clock

/** Record counts included in a backup export or restored by a backup import. */
data class BackupSummary(
    val schemaVersion: Int,
    val exerciseCount: Int,
    val routineCount: Int,
    val sessionCount: Int,
    val bodyweightEntryCount: Int,
) {
    val totalRecords: Int
        get() = exerciseCount + routineCount + sessionCount + bodyweightEntryCount
}

/** Result of exporting the local training database to a versioned JSON archive. */
data class BackupExportPayload(
    val json: String,
    val suggestedFileName: String,
    val summary: BackupSummary,
)

/** Outcome of validating and importing a JSON backup archive into local storage. */
sealed interface BackupImportResult {
    data class Success(val summary: BackupSummary) : BackupImportResult

    data class Failure(val reason: String) : BackupImportResult
}

/** Canonical schema version and default filename helper for JSON database backups. */
object BackupFileNames {
    const val CURRENT_SCHEMA_VERSION = 1

    fun suggestedFileName(clock: Clock): String {
        val date = clock.instant().atZone(clock.zone).toLocalDate()
        return "e4eheh-workout-backup-$date.json"
    }
}
