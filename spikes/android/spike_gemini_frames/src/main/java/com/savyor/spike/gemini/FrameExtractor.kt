package com.savyor.spike.gemini

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExtractedFrame(
    val timestampSeconds: Double,
    val file: File,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isThumbnail: Boolean
)

data class ExtractionSummary(
    val frames: List<ExtractedFrame>,
    val totalSizeBytes: Long,
    val deletedOriginalVideo: Boolean
)

class FrameExtractor(private val outputDir: File) {

    init {
        outputDir.mkdirs()
    }

    suspend fun extractKeyFrames(
        videoFile: File,
        timestampsSeconds: List<Double>
    ): ExtractionSummary = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val extracted = mutableListOf<ExtractedFrame>()

        try {
            retriever.setDataSource(videoFile.absolutePath)

            // Extract 360px thumbnail at 0.5s or first timestamp
            val thumbBitmap = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (thumbBitmap != null) {
                val scaledThumb = scaleBitmap(thumbBitmap, targetWidth = 360)
                val thumbFile = File(outputDir, "thumb_${videoFile.nameWithoutExtension}.webp")
                saveAsWebP(scaledThumb, thumbFile, quality = 80)
                extracted.add(
                    ExtractedFrame(
                        timestampSeconds = 0.5,
                        file = thumbFile,
                        width = scaledThumb.width,
                        height = scaledThumb.height,
                        sizeBytes = thumbFile.length(),
                        isThumbnail = true
                    )
                )
            }

            // Extract up to 4 key moment frames at 640px
            val targetTimestamps = timestampsSeconds.take(4).ifEmpty { listOf(1.0, 3.0) }
            targetTimestamps.forEachIndexed { index, tsSec ->
                val timeMicros = (tsSec * 1_000_000).toLong()
                val frameBitmap = retriever.getFrameAtTime(timeMicros, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frameBitmap != null) {
                    val scaledFrame = scaleBitmap(frameBitmap, targetWidth = 640)
                    val frameFile = File(outputDir, "moment_${index}_${tsSec.toInt()}s.webp")
                    saveAsWebP(scaledFrame, frameFile, quality = 80)
                    extracted.add(
                        ExtractedFrame(
                            timestampSeconds = tsSec,
                            file = frameFile,
                            width = scaledFrame.width,
                            height = scaledFrame.height,
                            sizeBytes = frameFile.length(),
                            isThumbnail = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("FrameExtractor", "Frame extraction failed", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Delete the original MP4 immediately as required
        val deleted = videoFile.delete()
        Log.i("FrameExtractor", "Deleted original video ${videoFile.name}: $deleted")

        ExtractionSummary(
            frames = extracted,
            totalSizeBytes = extracted.sumOf { it.sizeBytes },
            deletedOriginalVideo = deleted
        )
    }

    private fun scaleBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun saveAsWebP(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { out ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
            }
        }
    }
}
