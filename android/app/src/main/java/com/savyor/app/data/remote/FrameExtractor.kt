package com.savyor.app.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import com.savyor.app.domain.model.KeyMomentAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExtractedFramesResult(
    val thumbnailPath: String?,
    val keyMomentsWithImages: List<KeyMomentWithImage>
)

data class KeyMomentWithImage(
    val timestampSec: Int,
    val label: String,
    val webpPath: String
)

interface IFrameExtractor {
    suspend fun extractFrames(
        videoFile: File,
        shortcode: String,
        keyMoments: List<KeyMomentAnalysis>
    ): ExtractedFramesResult
}

class FrameExtractor(private val context: Context) : IFrameExtractor {

    companion object {
        private const val TAG = "FrameExtractor"
        private const val DARK_LUMA_THRESHOLD = 22.0
    }

    override suspend fun extractFrames(
        videoFile: File,
        shortcode: String,
        keyMoments: List<KeyMomentAnalysis>
    ): ExtractedFramesResult = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "snapshots/$shortcode").apply { mkdirs() }
        val retriever = MediaMetadataRetriever()

        var thumbnailPath: String? = null
        val momentsWithImages = mutableListOf<KeyMomentWithImage>()

        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = (durationMsStr?.toLongOrNull() ?: 10_000L).coerceAtLeast(500L)
            val durationUs = durationMs * 1000L

            // 1. Extract cover thumbnail: skip 0.0s black fade-in; sample 1.0s (or first key moment / midpoint if short or too dark)
            val thumbRaw = extractBestCoverFrame(retriever, durationMs, durationUs, keyMoments)
            if (thumbRaw != null) {
                val thumbScaled = scaleBitmapToWidth(thumbRaw, 480)
                val thumbFile = File(outputDir, "thumb.webp")
                saveBitmapAsWebp(thumbScaled, thumbFile)
                thumbnailPath = thumbFile.absolutePath
                thumbRaw.recycle()
                if (thumbScaled != thumbRaw) thumbScaled.recycle()
            }

            // 2. Extract up to 4 key-moment frames at 640px
            val momentsToExtract = keyMoments.take(4)
            momentsToExtract.forEachIndexed { index, moment ->
                val rawTimeUs = (moment.timestampSec.toLong() * 1_000_000L)
                    .coerceIn(0L, (durationUs - 100_000L).coerceAtLeast(0L))
                val frameRaw = getFrameAtExactOrSync(retriever, rawTimeUs)
                if (frameRaw != null) {
                    val frameScaled = scaleBitmapToWidth(frameRaw, 640)
                    val frameFile = File(outputDir, "moment_${index}_${moment.timestampSec}s.webp")
                    saveBitmapAsWebp(frameScaled, frameFile)
                    momentsWithImages.add(
                        KeyMomentWithImage(
                            timestampSec = moment.timestampSec,
                            label = moment.label,
                            webpPath = frameFile.absolutePath
                        )
                    )
                    frameRaw.recycle()
                    if (frameScaled != frameRaw) frameScaled.recycle()
                }
            }

            Log.i(TAG, "Extracted ${momentsWithImages.size} key moment frames + thumb for $shortcode")
            ExtractedFramesResult(
                thumbnailPath = thumbnailPath,
                keyMomentsWithImages = momentsWithImages
            )
        } catch (e: Exception) {
            Log.e(TAG, "Frame extraction error for $shortcode: ${e.message}", e)
            ExtractedFramesResult(thumbnailPath = null, keyMomentsWithImages = emptyList())
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Retriever release error: ${e.message}")
            }
        }
    }

    private fun extractBestCoverFrame(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        durationUs: Long,
        keyMoments: List<KeyMomentAnalysis>
    ): Bitmap? {
        val candidatesUs = buildList {
            // Primary target: 1.0s if video is long enough, else midpoint
            if (durationMs >= 1_500L) {
                add(1_000_000L)
            } else {
                add((durationUs / 2L).coerceAtLeast(100_000L))
            }
            // Fallback 1: first AI-identified key moment (> 0s)
            keyMoments.firstOrNull { it.timestampSec > 0 }?.let { firstMoment ->
                add((firstMoment.timestampSec.toLong() * 1_000_000L).coerceIn(0L, durationUs))
            }
            // Fallback 2: ~35% into video
            add((durationUs * 35L) / 100L)
        }.distinct()

        var fallbackBitmap: Bitmap? = null
        for (timeUs in candidatesUs) {
            val frame = getFrameAtExactOrSync(retriever, timeUs) ?: continue
            if (!isFrameTooDark(frame)) {
                fallbackBitmap?.recycle()
                return frame
            }
            if (fallbackBitmap == null) {
                fallbackBitmap = frame
            } else {
                frame.recycle()
            }
        }
        return fallbackBitmap
    }

    private fun getFrameAtExactOrSync(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun isFrameTooDark(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true

        val grid = 12
        var totalLuma = 0.0
        var samples = 0
        for (gx in 1 until grid) {
            val x = (w * gx) / grid
            for (gy in 1 until grid) {
                val y = (h * gy) / grid
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                totalLuma += 0.299 * r + 0.587 * g + 0.114 * b
                samples++
            }
        }
        val avgLuma = if (samples > 0) totalLuma / samples else 0.0
        return avgLuma < DARK_LUMA_THRESHOLD
    }

    private fun scaleBitmapToWidth(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun saveBitmapAsWebp(bitmap: Bitmap, file: File) {
        val fos = FileOutputStream(file)
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, 82, fos)
        fos.flush()
        fos.close()
    }
}
