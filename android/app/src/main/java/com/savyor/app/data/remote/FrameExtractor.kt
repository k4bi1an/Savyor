package com.savyor.app.data.remote

import android.content.Context
import android.graphics.Bitmap
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
            val durationMs = durationMsStr?.toLongOrNull() ?: 10_000L

            // 1. Extract 360px thumbnail at 0.5s or 10% of duration
            val thumbTimeUs = minOf(500_000L, (durationMs * 1000) / 10)
            val thumbRaw = retriever.getFrameAtTime(thumbTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (thumbRaw != null) {
                val thumbScaled = scaleBitmapToWidth(thumbRaw, 360)
                val thumbFile = File(outputDir, "thumb.webp")
                saveBitmapAsWebp(thumbScaled, thumbFile)
                thumbnailPath = thumbFile.absolutePath
                thumbRaw.recycle()
                if (thumbScaled != thumbRaw) thumbScaled.recycle()
            }

            // 2. Extract up to 4 key-moment frames at 640px
            val momentsToExtract = keyMoments.take(4)
            momentsToExtract.forEachIndexed { index, moment ->
                val timeUs = (moment.timestampSec.toLong() * 1_000_000L).coerceIn(0L, durationMs * 1000L)
                val frameRaw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
        bitmap.compress(format, 80, fos)
        fos.flush()
        fos.close()
    }
}
