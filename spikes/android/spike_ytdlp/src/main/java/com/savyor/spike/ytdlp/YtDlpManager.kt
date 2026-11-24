package com.savyor.spike.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ReelTestResult(
    val url: String,
    val success: Boolean,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val errorCategory: String,
    val message: String
)

class YtDlpManager(private val context: Context) {

    private var initialized = false

    fun init() {
        if (!initialized) {
            YoutubeDL.getInstance().init(context.applicationContext)
            initialized = true
        }
    }

    suspend fun downloadReel(url: String): ReelTestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val downloadDir = File(context.cacheDir, "reels_spike").apply { mkdirs() }
        val outputTemplate = File(downloadDir, "%(id)s.%(ext)s").absolutePath

        try {
            init()
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("-f", "best[ext=mp4]/best")
                addOption("-o", outputTemplate)
                addOption("--socket-timeout", "30")
            }

            var lastLine = ""
            YoutubeDL.getInstance().execute(request) { _, _, line ->
                if (!line.isNullOrBlank()) lastLine = line
            }

            val elapsed = System.currentTimeMillis() - startTime
            val files = downloadDir.listFiles()?.filter { it.lastModified() >= startTime } ?: emptyList()
            val downloadedFile = files.maxByOrNull { it.length() }
            val fileSize = downloadedFile?.length() ?: 0L

            Log.i("SpikeYtDlp", "[SPIKE_YTDLP_RESULT] url=$url success=true timeMs=$elapsed sizeBytes=$fileSize file=${downloadedFile?.name}")

            ReelTestResult(
                url = url,
                success = true,
                durationMs = elapsed,
                fileSizeBytes = fileSize,
                errorCategory = "None",
                message = "Downloaded successfully (${"%.2f".format(fileSize.toDouble() / (1024 * 1024))} MB in ${elapsed}ms)"
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            val errorText = e.message ?: e.toString()
            val category = categorizeError(errorText)

            Log.e("SpikeYtDlp", "[SPIKE_YTDLP_RESULT] url=$url success=false timeMs=$elapsed category=$category error=$errorText")

            ReelTestResult(
                url = url,
                success = false,
                durationMs = elapsed,
                fileSizeBytes = 0L,
                errorCategory = category,
                message = errorText.lines().firstOrNull() ?: errorText
            )
        }
    }

    fun categorizeError(stderr: String): String {
        val lower = stderr.lowercase()
        return when {
            "login" in lower || "checkpoint" in lower || "sign in" in lower -> "Login required"
            "rate" in lower || "429" in lower || "too many requests" in lower -> "Rate limited"
            "private" in lower || "unavailable" in lower || "deleted" in lower || "not found" in lower -> "Private or unavailable"
            "timeout" in lower || "timed out" in lower || "connection" in lower || "unreachable" in lower -> "Network / Timeout"
            "ffmpeg" in lower -> "FFmpeg required"
            "format" in lower || "extractor" in lower || "json" in lower -> "Format/Parser error"
            else -> "Other"
        }
    }

    fun clearCache(): Long {
        val downloadDir = File(context.cacheDir, "reels_spike")
        var freed = 0L
        downloadDir.listFiles()?.forEach {
            freed += it.length()
            it.delete()
        }
        return freed
    }
}
