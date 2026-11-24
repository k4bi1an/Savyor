package com.savyor.spikes.frames

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

/**
 * Spike 2C: Hardware-accelerated frame extraction via MediaMetadataRetriever
 * with lossy WebP compression and LRU directory management.
 */
class FrameExtractorSpike(private val cacheDir: File) {

    private val snapshotDir = File(cacheDir, "snapshots").apply { mkdirs() }
    private val maxCacheBytes = 100L * 1024L * 1024L // 100 MB

    fun extractKeyFrame(videoFile: File, timeSeconds: Double, targetWidth: Int = 640): File {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val timeUs = (timeSeconds * 1_000_000).toLong()
            val rawBitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: throw IllegalStateException("Could not extract frame at ${timeSeconds}s")

            val aspectRatio = rawBitmap.height.toFloat() / rawBitmap.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).toInt()
            val scaledBitmap = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)

            val outFile = File(snapshotDir, "snap_${System.currentTimeMillis()}.webp")
            FileOutputStream(outFile).use { fos ->
                // Native WebP compression (supported natively on Android API 18+)
                scaledBitmap.compress(Bitmap.CompressFormat.WEBP, 75, fos)
            }
            rawBitmap.recycle()
            scaledBitmap.recycle()

            enforceCacheCap()
            return outFile
        } finally {
            retriever.release()
        }
    }

    /**
     * Enforces the 100 MB snapshot cache cap using Least-Recently-Used file eviction.
     */
    fun enforceCacheCap() {
        val files = snapshotDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxCacheBytes) return

        // Sort by last modified (oldest first)
        val sortedFiles = files.sortedBy { it.lastModified() }
        for (f in sortedFiles) {
            if (totalSize <= maxCacheBytes) break
            val len = f.length()
            if (f.delete()) {
                totalSize -= len
            }
        }
    }
}
