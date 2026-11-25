package com.savyor.app.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.SnapshotCacheManager
import com.savyor.app.data.remote.*
import java.io.File

object PipelineDependencies {
    var downloaderOverride: IReelDownloader? = null
    var geminiClientOverride: IGeminiClient? = null
    var frameExtractorOverride: IFrameExtractor? = null

    fun reset() {
        downloaderOverride = null
        geminiClientOverride = null
        frameExtractorOverride = null
    }
}

class ProcessReelWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SHORTCODE = "shortcode"
        private const val TAG = "ProcessReelWorker"
    }

    private val gson = Gson()

    override suspend fun doWork(): Result {
        val shortcode = inputData.getString(KEY_SHORTCODE) ?: run {
            Log.e(TAG, "ProcessReelWorker missing shortcode argument")
            return Result.failure()
        }

        Log.i(TAG, "Starting processing job for shortcode: $shortcode")
        val db = SavyorDatabase.getInstance(applicationContext)
        val reelDao = db.reelDao()
        val correctionDao = db.categoryCorrectionDao()
        val settings = EncryptedSettingsManager(applicationContext)

        val reel = reelDao.getReelByShortcode(shortcode) ?: run {
            Log.e(TAG, "Reel $shortcode not found in database")
            return Result.failure()
        }

        val downloader = PipelineDependencies.downloaderOverride ?: YtDlpEngine(applicationContext)
        val geminiClient = PipelineDependencies.geminiClientOverride ?: GeminiFilesClient()
        val frameExtractor = PipelineDependencies.frameExtractorOverride ?: FrameExtractor(applicationContext)

        var downloadedVideoFile: File? = null
        val totalStartMs = SystemClock.elapsedRealtime()

        return try {
            // 1. Mark as DOWNLOADING (status stays PROCESSING until everything succeeds)
            reelDao.updateProcessingAndStatus(shortcode, "DOWNLOADING", "PROCESSING", null)

            // 2. Download reel
            Log.i(TAG, "Downloading reel: ${reel.cleanUrl}")
            val ytStartMs = SystemClock.elapsedRealtime()
            val downloadResult = downloader.downloadReel(reel.cleanUrl, shortcode)
            val ytDurationMs = SystemClock.elapsedRealtime() - ytStartMs
            downloadedVideoFile = downloadResult.videoFile
            val downloadedBytes = downloadedVideoFile.length()
            val creator = downloadResult.creator.ifBlank { reel.creator }

            // 3. Mark as ANALYZING
            reelDao.updateProcessingAndStatus(shortcode, "ANALYZING", "PROCESSING", null)

            // Read duration before building prompt so Gemini never hallucinates out-of-bounds timestamps
            val videoDurationSec = (frameExtractor as? FrameExtractor)
                ?.readVideoDurationSec(downloadedVideoFile)
                ?.takeIf { it > 0 }

            // 4. Load recent 5 category corrections and dynamic categories
            val recentCorrections = correctionDao.getRecentCorrections(5)
            val availableCategories = db.categoryDao().getAllCategoriesList().map { it.name }

            // 5. Build prompt
            val prompt = GeminiPromptBuilder.buildSystemPrompt(
                originalCaption = downloadResult.caption,
                creatorHandle = creator,
                availableCategories = availableCategories,
                recentCorrections = recentCorrections,
                videoDurationSec = videoDurationSec
            )

            // 6. Call Gemini
            Log.i(TAG, "Sending video to Gemini (${GeminiFilesClient.MODEL_NAME}) for multimodal analysis...")
            val aiStartMs = SystemClock.elapsedRealtime()
            val apiKey = settings.geminiApiKey
            val analysis = geminiClient.analyzeReel(
                apiKey = apiKey,
                videoFile = downloadedVideoFile,
                prompt = prompt
            )
            val aiDurationMs = SystemClock.elapsedRealtime() - aiStartMs

            // Validate category against available list (fallback to "Other")
            val validatedCategory = if (availableCategories.any { it.equals(analysis.category, ignoreCase = true) }) {
                availableCategories.first { it.equals(analysis.category, ignoreCase = true) }
            } else {
                "Other"
            }

            // Deterministic URL normalization & regex rescue
            val mergedLinks = LinkExtractor.extractAndMerge(
                aiLinks = analysis.extractedResources,
                caption = downloadResult.caption,
                summary = analysis.summary,
                takeaways = analysis.keyTakeaways,
                transcript = analysis.transcript
            )

            // 7. Extract thumbnail and key moment frames
            Log.i(TAG, "Extracting WebP frames via MediaMetadataRetriever...")
            val frameStartMs = SystemClock.elapsedRealtime()
            val framesResult = frameExtractor.extractFrames(
                videoFile = downloadedVideoFile,
                shortcode = shortcode,
                keyMoments = analysis.keyMoments
            )
            val frameDurationMs = SystemClock.elapsedRealtime() - frameStartMs
            SnapshotCacheManager.enforceSnapshotLruCap(applicationContext.filesDir)

            val totalDurationMs = SystemClock.elapsedRealtime() - totalStartMs

            // 8. Update database with complete note & transition PROCESSING -> UNREAD
            val updatedReel = reel.copy(
                creator = creator,
                sourceLanguage = analysis.sourceLanguage,
                title = analysis.title,
                summary = analysis.summary,
                category = validatedCategory,
                tags = gson.toJson(analysis.tags),
                transcript = analysis.transcript,
                originalTranscript = analysis.originalTranscript,
                keyTakeaways = gson.toJson(analysis.keyTakeaways),
                keyMoments = gson.toJson(framesResult.keyMomentsWithImages),
                structuredContent = gson.toJson(analysis.structuredContent),
                extractedResources = gson.toJson(mergedLinks),
                caveats = gson.toJson(analysis.caveats),
                useWhen = analysis.useWhen,
                visualMemory = analysis.visualMemory,
                thumbnailPath = framesResult.thumbnailPath,
                status = "UNREAD",
                processingState = "COMPLETED",
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
                ytDlpDurationMs = ytDurationMs,
                frameExtractDurationMs = frameDurationMs,
                aiAnalysisDurationMs = aiDurationMs,
                totalProcessingDurationMs = totalDurationMs,
                videoDurationSec = videoDurationSec ?: framesResult.videoDurationSec.takeIf { it > 0 },
                downloadedVideoBytes = downloadedBytes,
                coverSizeBytes = framesResult.coverSizeBytes,
                framesExtractedCount = framesResult.keyMomentsWithImages.size,
                linksExtractedCount = mergedLinks.size,
                aiModelUsed = GeminiFilesClient.MODEL_NAME,
                processingError = null
            )

            reelDao.update(updatedReel)
            Log.i(TAG, "Successfully processed note for reel $shortcode in ${totalDurationMs}ms: \"${analysis.title}\"")
            Result.success()
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            Log.e(TAG, "Failed processing reel $shortcode: $errorMsg", e)
            reelDao.updateProcessingAndStatus(shortcode, "FAILED", "FAILED", errorMsg)
            Result.failure()
        } finally {
            // CRITICAL REQUIREMENT: Always delete temporary .mp4 so permanent video storage is 0 MB
            downloadedVideoFile?.let { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.i(TAG, "Deleted temporary video file ${file.name}: $deleted (Storage freed)")
                }
            }
        }
    }
}
