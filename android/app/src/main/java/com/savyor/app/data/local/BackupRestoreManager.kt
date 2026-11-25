package com.savyor.app.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupStats(
    val fileSizeBytes: Long,
    val snapshotCount: Int
)

data class RestoreStats(
    val reelsRestored: Int,
    val snapshotCount: Int
)

object BackupRestoreManager {

    private const val TAG = "BackupRestoreManager"
    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    /**
     * Checkpoints SQLite WAL and archives savyor.db + all snapshots into a .zip file via SAF URI.
     */
    suspend fun exportBackup(context: Context, destUri: Uri): Result<BackupStats> = withContext(Dispatchers.IO) {
        runCatching {
            val db = SavyorDatabase.getInstance(context)
            // 1. Checkpoint SQLite WAL to flush all pages to savyor.db
            try {
                db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            } catch (e: Exception) {
                Log.w(TAG, "WAL checkpoint warning: ${e.message}")
            }

            val dbFile = context.getDatabasePath("savyor.db")
            if (!dbFile.exists()) {
                throw FileNotFoundException("Database file savyor.db does not exist")
            }

            val snapshotsDir = File(context.filesDir, "snapshots")
            var snapshotsArchived = 0
            var bytesWritten = 0L

            val outputStream = context.contentResolver.openOutputStream(destUri)
                ?: throw IOException("Cannot open output stream for URI: $destUri")

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // Write database
                val dbEntry = ZipEntry("database/savyor.db")
                dbEntry.time = dbFile.lastModified()
                zipOut.putNextEntry(dbEntry)
                FileInputStream(dbFile).use { input ->
                    bytesWritten += input.copyTo(zipOut)
                }
                zipOut.closeEntry()

                // Write snapshots
                if (snapshotsDir.exists() && snapshotsDir.isDirectory) {
                    snapshotsDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val relativePath = "snapshots/" + file.relativeTo(snapshotsDir).path.replace('\\', '/')
                            val entry = ZipEntry(relativePath)
                            entry.time = file.lastModified()
                            zipOut.putNextEntry(entry)
                            FileInputStream(file).use { input ->
                                bytesWritten += input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                            snapshotsArchived++
                        }
                    }
                }
            }

            Log.i(TAG, "Exported backup: $snapshotsArchived snapshots, $bytesWritten db/snapshot bytes")
            BackupStats(
                fileSizeBytes = bytesWritten,
                snapshotCount = snapshotsArchived
            )
        }
    }

    /**
     * Safely validates and extracts a Savyor backup .zip file, guarding against Zip Slip path traversal.
     */
    suspend fun importBackup(context: Context, srcUri: Uri): Result<RestoreStats> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(srcUri)
                ?: throw IOException("Cannot open input stream for URI: $srcUri")

            // 1. Staging directory in cache
            val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}")
            if (!stagingDir.exists()) stagingDir.mkdirs()

            var extractedSnapshots = 0

            try {
                // 2. Extract with strict Zip Slip path traversal validation
                ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val destinationFile = File(stagingDir, entry.name)

                        // Path traversal (Zip Slip) security check
                        val canonicalDestinationPath = destinationFile.canonicalPath
                        val canonicalStagingPath = stagingDir.canonicalPath + File.separator
                        if (!canonicalDestinationPath.startsWith(canonicalStagingPath) &&
                            canonicalDestinationPath != stagingDir.canonicalPath
                        ) {
                            throw SecurityException("Zip Slip path traversal attempt rejected: ${entry.name}")
                        }

                        if (entry.isDirectory) {
                            destinationFile.mkdirs()
                        } else {
                            destinationFile.parentFile?.mkdirs()
                            FileOutputStream(destinationFile).use { output ->
                                zipIn.copyTo(output)
                            }
                            if (entry.name.startsWith("snapshots/")) {
                                extractedSnapshots++
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                // 3. Verify staged database exists and is valid SQLite
                val stagedDbFile = File(stagingDir, "database/savyor.db")
                if (!stagedDbFile.exists()) {
                    throw IllegalArgumentException("Invalid Savyor backup: database/savyor.db not found in archive")
                }

                validateSqliteHeader(stagedDbFile)

                // 4. Safely close current Room DB instance
                SavyorDatabase.closeAndResetInstance()

                // 5. Replace database files
                val targetDbFile = context.getDatabasePath("savyor.db")
                targetDbFile.parentFile?.mkdirs()

                // Remove existing WAL and SHM files
                File(targetDbFile.path + "-wal").delete()
                File(targetDbFile.path + "-shm").delete()

                stagedDbFile.copyTo(targetDbFile, overwrite = true)

                // 6. Restore snapshots
                val stagedSnapshotsDir = File(stagingDir, "snapshots")
                val targetSnapshotsDir = File(context.filesDir, "snapshots")
                if (stagedSnapshotsDir.exists() && stagedSnapshotsDir.isDirectory) {
                    if (!targetSnapshotsDir.exists()) targetSnapshotsDir.mkdirs()
                    stagedSnapshotsDir.copyRecursively(targetSnapshotsDir, overwrite = true)
                }

                // 7. Reopen Room database and get reel count
                val restoredDb = SavyorDatabase.getInstance(context)
                val reelCount = restoredDb.reelDao().countAll()

                Log.i(TAG, "Successfully restored $reelCount reels and $extractedSnapshots snapshots")
                RestoreStats(
                    reelsRestored = reelCount,
                    snapshotCount = extractedSnapshots
                )
            } finally {
                // Always clean up staging directory
                stagingDir.deleteRecursively()
            }
        }
    }

    private fun validateSqliteHeader(file: File) {
        FileInputStream(file).use { input ->
            val headerBytes = ByteArray(16)
            val bytesRead = input.read(headerBytes)
            if (bytesRead < 16) {
                throw IllegalArgumentException("Corrupt database file: smaller than 16 bytes")
            }
            val headerString = String(headerBytes, Charsets.US_ASCII)
            if (!headerString.startsWith("SQLite format 3")) {
                throw IllegalArgumentException("Corrupt database file: invalid SQLite header")
            }
        }
    }
}
