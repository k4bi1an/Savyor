package com.savyor.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.savyor.app.R
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.SnapshotCacheManager
import com.savyor.app.data.remote.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.abs

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
        private const val NOTIFICATION_CHANNEL_ID = "savyor_processing"
        private const val NOTIFICATION_BASE_ID = 4100
    }

    private val gson = Gson()
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

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

        if (reel.title.startsWith("Processing reel @", ignoreCase = true)) {
            reelDao.clearLegacyPlaceholderTitle(shortcode)
        }

        ensureNotificationChannel()
        val notificationId = NOTIFICATION_BASE_ID + (shortcode.hashCode() and 0x0FFF)
        runCatching { setForeground(createForegroundInfo(notificationId, 5, "Starting reel analysis...")) }

        val downloader = PipelineDependencies.downloaderOverride ?: YtDlpEngine(applicationContext)
        val geminiClient = PipelineDependencies.geminiClientOverride ?: GeminiFilesClient()
        val frameExtractor = PipelineDependencies.frameExtractorOverride ?: FrameExtractor(applicationContext)

        var downloadedVideoFile: File? = null
        val totalStartMs = SystemClock.elapsedRealtime()
        var currentStage = "Download"

        val progressMutex = Mutex()
        var lastEmitAtMs = 0L
        var lastEmitProgress = -1
        var lastEmitState = ""

        suspend fun emitProgress(
            state: String,
            progress: Int,
            detail: String,
            force: Boolean = false
        ) {
            val clamped = progress.coerceIn(0, 100)
            progressMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                val stateChanged = state != lastEmitState
                val progressDelta = abs(clamped - lastEmitProgress)
                if (!force && !stateChanged && progressDelta < 2 && (now - lastEmitAtMs) < 350L) {
                    return
                }
                lastEmitAtMs = now
                lastEmitProgress = clamped
                lastEmitState = state
                reelDao.updateProcessingProgress(shortcode, state, clamped, detail)
                runCatching {
                    notificationManager?.notify(
                        notificationId,
                        buildNotification(clamped, detail)
                    )
                }
            }
        }

        return try {
            // 1. Mark as DOWNLOADING (6%..45%)
            currentStage = "Download"
            reelDao.updateProcessingAndStatus(shortcode, "DOWNLOADING", "PROCESSING", null, 6, "Connecting to Instagram...")
            emitProgress("DOWNLOADING", 6, "Connecting to Instagram...", force = true)

            // 2. Download reel with real-time yt-dlp progress
            Log.i(TAG, "Downloading reel: ${reel.cleanUrl}")
            val ytStartMs = SystemClock.elapsedRealtime()
            val downloadResult = coroutineScope {
                downloader.downloadReel(reel.cleanUrl, shortcode) { pct, detail ->
                    val mapped = 6 + (pct * 0.39f).toInt()
                    launch {
                        emitProgress("DOWNLOADING", mapped, detail)
                    }
                }
            }
            val ytDurationMs = SystemClock.elapsedRealtime() - ytStartMs
            downloadedVideoFile = downloadResult.videoFile
            val downloadedBytes = downloadedVideoFile.length()
            val creator = downloadResult.creator.ifBlank { reel.creator }
            val creatorUsername = downloadResult.creatorUsername.ifBlank { reel.creatorUsername }

            // 3. Mark as UPLOADING (48%..66%) before Gemini upload/analysis
            currentStage = "Upload"
            emitProgress("UPLOADING", 48, "Preparing video upload...", force = true)

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
                creatorHandle = creatorUsername.ifBlank { creator },
                availableCategories = availableCategories,
                recentCorrections = recentCorrections,
                videoDurationSec = videoDurationSec
            )

            // 6. Call Gemini (48%..66% upload, 68%..89% analysis)
            Log.i(TAG, "Sending video to Gemini (mode=${settings.geminiModelMode}) for multimodal analysis...")
            val aiStartMs = SystemClock.elapsedRealtime()
            val apiKey = settings.geminiApiKey
            val analysis = coroutineScope {
                var analyzingStarted = false
                var currentAnalyzeDetail = "Analyzing video with AI..."
                var currentAnalyzeProgress = 68

                val tickerJob = launch {
                    while (isActive) {
                        delay(1500L)
                        if (analyzingStarted && currentAnalyzeProgress < 88) {
                            currentAnalyzeProgress = (currentAnalyzeProgress + 2).coerceAtMost(88)
                            emitProgress("ANALYZING", currentAnalyzeProgress, currentAnalyzeDetail)
                        }
                    }
                }

                try {
                    geminiClient.analyzeReel(
                        apiKey = apiKey,
                        videoFile = downloadedVideoFile,
                        prompt = prompt,
                        modelMode = settings.geminiModelMode
                    ) { stage, stagePct, detail ->
                        if (stage == "UPLOADING") {
                            currentStage = "Upload"
                            val mapped = 48 + (stagePct * 0.18f).toInt()
                            launch {
                                emitProgress("UPLOADING", mapped, detail)
                            }
                        } else {
                            currentStage = "AI Analysis"
                            analyzingStarted = true
                            currentAnalyzeDetail = detail
                            val base = (68 + (stagePct * 0.15f).toInt()).coerceIn(68, 86)
                            if (base > currentAnalyzeProgress) {
                                currentAnalyzeProgress = base
                            }
                            launch {
                                emitProgress("ANALYZING", currentAnalyzeProgress, detail, force = true)
                            }
                        }
                    }
                } finally {
                    tickerJob.cancel()
                }
            }
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

            // 7. Extract thumbnail and key moment frames (90%..98%)
            currentStage = "Frames"
            emitProgress("FRAMES", 90, "Capturing visual moments...", force = true)
            Log.i(TAG, "Extracting WebP frames via MediaMetadataRetriever...")
            val frameStartMs = SystemClock.elapsedRealtime()
            val boundedMoments = GeminiFilesClient.filterValidKeyMoments(analysis.keyMoments, videoDurationSec)
            val framesResult = coroutineScope {
                frameExtractor.extractFrames(
                    videoFile = downloadedVideoFile,
                    shortcode = shortcode,
                    keyMoments = boundedMoments
                ) { done, total ->
                    val mapped = 90 + ((done.toFloat() / total.coerceAtLeast(1).toFloat()) * 8f).toInt()
                    val detail = if (total > 1) {
                        "Capturing visual moments ($done/$total)..."
                    } else {
                        "Capturing cover snapshot..."
                    }
                    launch {
                        emitProgress("FRAMES", mapped, detail)
                    }
                }
            }
            val frameDurationMs = SystemClock.elapsedRealtime() - frameStartMs
            SnapshotCacheManager.enforceSnapshotLruCap(applicationContext.filesDir)

            val totalDurationMs = SystemClock.elapsedRealtime() - totalStartMs

            // 8. Update database with complete note & transition PROCESSING -> UNREAD
            val updatedReel = reel.copy(
                creator = creator,
                creatorUsername = creatorUsername,
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
                processingProgress = 100,
                processingDetail = null,
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
                aiModelUsed = analysis.modelUsed.ifBlank { GeminiFilesClient.MODEL_NAME },
                processingError = null
            )

            reelDao.update(updatedReel)
            Log.i(TAG, "Successfully processed note for reel $shortcode in ${totalDurationMs}ms: \"${analysis.title}\"")
            Result.success()
        } catch (e: Exception) {
            val failingStage = (e as? GeminiStageException)?.stage ?: currentStage
            val rawMsg = (e.cause?.message ?: e.message ?: e.toString())
                .replace(Regex("\\s+"), " ")
                .trim()
            val conciseReason = if (rawMsg.length > 140) rawMsg.take(137) + "..." else rawMsg
            val errorMsg = "$failingStage failed: $conciseReason"
            Log.e(TAG, "Failed processing reel $shortcode [$failingStage]: $rawMsg", e)
            reelDao.updateProcessingAndStatus(shortcode, "FAILED", "FAILED", errorMsg, 0, null)
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

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Reel Analysis",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress while downloading and analyzing saved reels"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, detail: String) =
        NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Analyzing Instagram Reel")
            .setContentText(detail)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()

    private fun createForegroundInfo(notificationId: Int, progress: Int, detail: String): ForegroundInfo {
        val notification = buildNotification(progress, detail)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
