package com.savyor.spike.share

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

class ProcessReelWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reelUrl = inputData.getString("reel_url") ?: return Result.failure(
            workDataOf("error" to "Missing reel_url")
        )
        val isMock = inputData.getBoolean("is_mock", false)

        Log.i("SpikeShareWorker", "[SPIKE_SHARE_WORKER_START] url=$reelUrl isMock=$isMock attempt=$runAttemptCount")

        return try {
            if (isMock) {
                delay(150)
                Log.i("SpikeShareWorker", "[SPIKE_SHARE_WORKER_RESULT] success=true url=$reelUrl mode=mock")
                Result.success(workDataOf("status" to "processed", "shortcode" to "mock_code"))
            } else {
                delay(500)
                Log.i("SpikeShareWorker", "[SPIKE_SHARE_WORKER_RESULT] success=true url=$reelUrl mode=live")
                Result.success(workDataOf("status" to "completed", "url" to reelUrl))
            }
        } catch (e: Exception) {
            Log.e("SpikeShareWorker", "[SPIKE_SHARE_WORKER_ERROR] url=$reelUrl error=${e.message}", e)
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }
}
