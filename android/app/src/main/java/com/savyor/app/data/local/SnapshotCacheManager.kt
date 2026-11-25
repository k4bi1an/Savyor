package com.savyor.app.data.local

import android.util.Log
import java.io.File

object SnapshotCacheManager {

    private const val TAG = "SnapshotCacheManager"
    const val DEFAULT_SNAPSHOT_CAP_BYTES = 100L * 1024L * 1024L // 100 MB

    /**
     * Sweeps any stale temporary video files left in cache/reels_temp from a prior mid-job crash.
     */
    fun cleanupOrphanedTempVideos(cacheDir: File): Int {
        val tempDir = File(cacheDir, "reels_temp")
        if (!tempDir.exists() || !tempDir.isDirectory) return 0

        var deletedCount = 0
        tempDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                if (file.delete()) {
                    deletedCount++
                }
            } else if (file.isDirectory) {
                if (file.deleteRecursively()) {
                    deletedCount++
                }
            }
        }
        if (deletedCount > 0) {
            runCatching { Log.i(TAG, "Cleaned up $deletedCount orphaned temp video file(s) from reels_temp") }
        }
        return deletedCount
    }

    /**
     * Enforces an LRU eviction cap on visual snapshots under filesDir/snapshots.
     * Never evicts cover thumbnails (thumb.webp) or database rows.
     * Returns the number of evicted moment snapshot files.
     */
    fun enforceSnapshotLruCap(
        filesDir: File,
        maxBytes: Long = DEFAULT_SNAPSHOT_CAP_BYTES
    ): Int {
        val snapshotsRoot = File(filesDir, "snapshots")
        if (!snapshotsRoot.exists() || !snapshotsRoot.isDirectory) return 0

        val allFiles = snapshotsRoot.walkTopDown()
            .filter { it.isFile }
            .toList()

        var totalBytes = allFiles.sumOf { it.length() }
        if (totalBytes <= maxBytes) return 0

        // Only evict non-cover moment frames, oldest lastModified first (LRU)
        val evictable = allFiles
            .filter { it.name != "thumb.webp" }
            .sortedBy { it.lastModified() }

        var evictedCount = 0
        for (file in evictable) {
            if (totalBytes <= maxBytes) break
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
                evictedCount++
            }
        }

        if (evictedCount > 0) {
            runCatching { Log.i(TAG, "LRU evicted $evictedCount old moment snapshot(s); remaining size=${totalBytes / 1024} KB") }
        }
        return evictedCount
    }
}
