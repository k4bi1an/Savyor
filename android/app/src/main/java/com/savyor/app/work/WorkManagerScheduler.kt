package com.savyor.app.work

import android.content.Context
import android.util.Log
import androidx.work.*
import com.savyor.app.data.local.EncryptedSettingsManager
import com.savyor.app.data.local.SavyorDatabase
import com.savyor.app.data.local.entity.ReelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

sealed class EnqueueResult {
    data class Success(val shortcode: String, val isNew: Boolean) : EnqueueResult()
    data class InvalidUrl(val message: String) : EnqueueResult()
    data class Error(val message: String) : EnqueueResult()
}

class WorkManagerScheduler(private val context: Context) {

    companion object {
        private const val TAG = "WorkManagerScheduler"
        private val INSTAGRAM_REEL_PATTERN = Pattern.compile(
            """https?://(?:www\.)?instagram\.com/(?:reel|p|reels)/([A-Za-z0-9_-]+)""",
            Pattern.CASE_INSENSITIVE
        )

        fun extractShortcode(text: String): String? {
            val matcher = INSTAGRAM_REEL_PATTERN.matcher(text)
            return if (matcher.find()) {
                matcher.group(1)
            } else null
        }

        fun normalizeUrl(shortcode: String): String {
            return "https://www.instagram.com/reel/$shortcode/"
        }
    }

    suspend fun enqueueReel(sharedText: String, forceReanalyze: Boolean = false): EnqueueResult = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(sharedText)
            ?: return@withContext EnqueueResult.InvalidUrl("No valid Instagram reel URL found in shared text")

        val cleanUrl = normalizeUrl(shortcode)
        val db = SavyorDatabase.getInstance(context)
        val reelDao = db.reelDao()
        val settings = EncryptedSettingsManager(context)

        try {
            val existingReel = reelDao.getReelByShortcode(shortcode)
            var isNew = false

            if (existingReel == null) {
                val newReel = ReelEntity(
                    shortcode = shortcode,
                    originalUrl = sharedText.trim(),
                    cleanUrl = cleanUrl,
                    title = "Processing reel @$shortcode...",
                    status = "PROCESSING",
                    processingState = "QUEUED",
                    createdAt = System.currentTimeMillis()
                )
                reelDao.insert(newReel)
                isNew = true
                Log.i(TAG, "Created new reel entry for $shortcode in database")
            } else if (existingReel.processingState == "COMPLETED" && !forceReanalyze) {
                Log.i(TAG, "Reel $shortcode is already processed and saved")
                return@withContext EnqueueResult.Success(shortcode, isNew = false)
            } else {
                reelDao.updateProcessingAndStatus(shortcode, "QUEUED", "PROCESSING", null)
                Log.i(TAG, "Resetting reel $shortcode to QUEUED (forceReanalyze=$forceReanalyze)")
            }

            // Build WorkRequest with network constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (settings.isWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ProcessReelWorker>()
                .setInputData(
                    workDataOf(ProcessReelWorker.KEY_SHORTCODE to shortcode)
                )
                .setConstraints(constraints)
                .addTag("process_reel")
                .addTag("reel_$shortcode")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "process_reel_$shortcode",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Log.i(TAG, "Enqueued WorkManager job for reel $shortcode")
            EnqueueResult.Success(shortcode, isNew = isNew)
        } catch (e: Exception) {
            val err = e.message ?: e.toString()
            Log.e(TAG, "Failed enqueueing reel $shortcode: $err", e)
            EnqueueResult.Error(err)
        }
    }
}
