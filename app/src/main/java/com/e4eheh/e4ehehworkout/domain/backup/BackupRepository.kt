package com.e4eheh.e4ehehworkout.domain.backup

/** Repository boundary for exporting and restoring the local training database as versioned JSON. */
interface BackupRepository {
    suspend fun exportBackup(): BackupExportPayload

    suspend fun importBackup(rawJson: String): BackupImportResult
}
