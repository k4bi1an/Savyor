package com.savyor.app.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.util.Log
import com.savyor.app.domain.model.KeyMomentAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class ExtractedFramesResult(
    val thumbnailPath: String?,
    val keyMomentsWithImages: List<KeyMomentWithImage>,
    val videoDurationSec: Int = 0,
    val coverSizeBytes: Long = 0L
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
        private const val BRIGHT_LUMA_THRESHOLD = 242.0
        private const val MIN_LUMA_VARIANCE = 140.0
        private const val DUPLICATE_MAD_THRESHOLD = 8.0
    }

    fun readVideoDurationSec(videoFile: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            (ms / 1000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            0
        } finally {
            runCatching { retriever.release() }
        }
    }

    override suspend fun extractFrames(
        videoFile: File,
        shortcode: String,
        keyMoments: List<KeyMomentAnalysis>
    ): ExtractedFramesResult = withContext(Dispatchers.IO) {
        val outputDir = File(context.filesDir, "snapshots/$shortcode").apply { mkdirs() }
        val retriever = MediaMetadataRetriever()

        var thumbnailPath: String? = null
        var coverSizeBytes = 0L
        var durationSec = 0
        val momentsWithImages = mutableListOf<KeyMomentWithImage>()
        val savedSignatures = mutableListOf<IntArray>()

        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = (durationMsStr?.toLongOrNull() ?: 10_000L).coerceAtLeast(500L)
            val durationUs = durationMs * 1000L
            durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)

            // 1. Extract cover thumbnail (540px max width, quality 78 WebP)
            val thumbRaw = extractBestCoverFrame(retriever, durationMs, durationUs, keyMoments)
            if (thumbRaw != null) {
                val thumbFile = File(outputDir, "thumb.webp")
                coverSizeBytes = ImageCompressor.saveOptimizedWebp(
                    bitmap = thumbRaw,
                    outFile = thumbFile,
                    maxWidthPx = 540,
                    quality = 78
                )
                thumbnailPath = thumbFile.absolutePath
                thumbRaw.recycle()
            }

            // 2. Extract up to 5 key-moment frames (720px max width, quality 80 WebP)
            //    with blank-frame nudge (+/-400ms) and 16x16 MAD duplicate filtering.
            val momentsToExtract = keyMoments.take(5)
            momentsToExtract.forEachIndexed { index, moment ->
                val rawTimeUs = (moment.timestampSec.toLong() * 1_000_000L)
                    .coerceIn(0L, (durationUs - 100_000L).coerceAtLeast(0L))
                val frameRaw = extractNonBlankMomentFrame(retriever, rawTimeUs, durationUs)
                if (frameRaw != null) {
                    val signature = compute16x16LumaSignature(frameRaw)
                    val isDuplicate = savedSignatures.any { prev ->
                        meanAbsoluteDifference(prev, signature) < DUPLICATE_MAD_THRESHOLD
                    }
                    if (!isDuplicate) {
                        savedSignatures.add(signature)
                        val frameFile = File(outputDir, "moment_${index}_${moment.timestampSec}s.webp")
                        ImageCompressor.saveOptimizedWebp(
                            bitmap = frameRaw,
                            outFile = frameFile,
                            maxWidthPx = 720,
                            quality = 80
                        )
                        momentsWithImages.add(
                            KeyMomentWithImage(
                                timestampSec = moment.timestampSec,
                                label = moment.label,
                                webpPath = frameFile.absolutePath
                            )
                        )
                    } else {
                        Log.d(TAG, "Skipped near-duplicate visual moment at ${moment.timestampSec}s for $shortcode")
                    }
                    frameRaw.recycle()
                }
            }

            Log.i(TAG, "Extracted ${momentsWithImages.size} key moment frames + thumb (${coverSizeBytes}B) for $shortcode")
            ExtractedFramesResult(
                thumbnailPath = thumbnailPath,
                keyMomentsWithImages = momentsWithImages,
                videoDurationSec = durationSec,
                coverSizeBytes = coverSizeBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Frame extraction error for $shortcode: ${e.message}", e)
            ExtractedFramesResult(
                thumbnailPath = null,
                keyMomentsWithImages = emptyList(),
                videoDurationSec = durationSec,
                coverSizeBytes = 0L
            )
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
        val primaryUs = minOf(1_500_000L, (durationUs * 15L) / 100L).coerceAtLeast(250_000L)
        val candidatesUs = buildList {
            add(primaryUs)
            add(500_000L.coerceAtMost(durationUs))
            if (durationMs >= 3_000L) add(3_000_000L)
            keyMoments.firstOrNull { it.timestampSec > 0 }?.let { firstMoment ->
                add((firstMoment.timestampSec.toLong() * 1_000_000L).coerceIn(0L, durationUs))
            }
            add((durationUs * 35L) / 100L)
        }.distinct()

        var fallbackBitmap: Bitmap? = null
        for (timeUs in candidatesUs) {
            val frame = getFrameAtExactOrSync(retriever, timeUs) ?: continue
            if (!isBlankOrLowVarianceFrame(frame)) {
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

    private fun extractNonBlankMomentFrame(
        retriever: MediaMetadataRetriever,
        targetTimeUs: Long,
        durationUs: Long
    ): Bitmap? {
        val offsetsUs = listOf(0L, 400_000L, -400_000L)
        var fallback: Bitmap? = null
        for (offset in offsetsUs) {
            val candidateUs = (targetTimeUs + offset).coerceIn(0L, (durationUs - 100_000L).coerceAtLeast(0L))
            val frame = getFrameAtExactOrSync(retriever, candidateUs) ?: continue
            if (!isBlankOrLowVarianceFrame(frame)) {
                fallback?.recycle()
                return frame
            }
            if (fallback == null) {
                fallback = frame
            } else {
                frame.recycle()
            }
        }
        return fallback
    }

    private fun getFrameAtExactOrSync(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun isBlankOrLowVarianceFrame(bitmap: Bitmap): Boolean {
        val sample = Bitmap.createScaledBitmap(bitmap, 32, 32, false)
        try {
            var sum = 0.0
            var sumSq = 0.0
            val n = 32 * 32
            for (y in 0 until 32) {
                for (x in 0 until 32) {
                    val pixel = sample.getPixel(x, y)
                    val luma = 0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)
                    sum += luma
                    sumSq += luma * luma
                }
            }
            val mean = sum / n
            val variance = (sumSq / n) - (mean * mean)
            return mean < DARK_LUMA_THRESHOLD || mean > BRIGHT_LUMA_THRESHOLD || variance < MIN_LUMA_VARIANCE
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
    }

    private fun compute16x16LumaSignature(bitmap: Bitmap): IntArray {
        val sample = Bitmap.createScaledBitmap(bitmap, 16, 16, false)
        val sig = IntArray(256)
        try {
            var idx = 0
            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    val p = sample.getPixel(x, y)
                    sig[idx++] = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
                }
            }
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
        return sig
    }

    private fun meanAbsoluteDifference(a: IntArray, b: IntArray): Double {
        var total = 0L
        for (i in a.indices) {
            total += abs(a[i] - b[i])
        }
        return total.toDouble() / a.size.toDouble()
    }
}
