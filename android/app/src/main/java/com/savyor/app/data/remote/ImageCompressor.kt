package com.savyor.app.data.remote

import android.graphics.Bitmap
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/**
 * Shared WebP image compression helper for cover thumbnails and visual moment snapshots.
 * Scales proportionally to [maxWidthPx] and encodes using WEBP_LOSSY on API 30+ (WEBP fallback on API 26-29).
 */
object ImageCompressor {

    fun saveOptimizedWebp(
        bitmap: Bitmap,
        outFile: File,
        maxWidthPx: Int,
        quality: Int
    ): Long {
        outFile.parentFile?.mkdirs()
        val scaled = scaleProportionally(bitmap, maxWidthPx)
        try {
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(outFile).use { fos ->
                scaled.compress(format, quality.coerceIn(1, 100), fos)
                fos.flush()
            }
        } finally {
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        }
        return outFile.length()
    }

    private fun scaleProportionally(bitmap: Bitmap, maxWidthPx: Int): Bitmap {
        if (bitmap.width <= maxWidthPx || bitmap.width <= 0) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (maxWidthPx * aspectRatio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidthPx, targetHeight, true)
    }
}
