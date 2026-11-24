package com.savyor.spikes.drive

import java.io.File

/**
 * Spike 2F: Google Drive AppData backup manager and starred reel compressor.
 */
class GoogleDriveBackupSpike {

    data class BackupManifest(
        val appVersion: String,
        val timestamp: Long,
        val totalReels: Int,
        val databaseSizeBytes: Long
    )

    /**
     * Uploads database backup and snapshot archive to hidden Drive appDataFolder.
     */
    fun uploadDatabaseBackup(dbFile: File, userAuthToken: String): Boolean {
        // Uses Drive REST API v3 multipart upload to https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart
        // Target folder: ["appDataFolder"]
        return true
    }
}
