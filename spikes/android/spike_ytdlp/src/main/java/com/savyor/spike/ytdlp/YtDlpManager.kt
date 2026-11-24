package com.savyor.spike.ytdlp

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
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
    val message: String,
    val hasVideoTrack: Boolean = false,
    val hasAudioTrack: Boolean = false,
    val trackInfo: String = ""
)

class YtDlpManager(private val context: Context) {

    private var initialized = false

    fun init() {
        if (!initialized) {
            YoutubeDL.getInstance().init(context.applicationContext)
            val currentVersion = YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
            Log.i("SpikeYtDlp", "YoutubeDL initialized. Bundled yt-dlp version: $currentVersion")
            initialized = true
        }
    }

    suspend fun updateEngine(): Pair<String, String> = withContext(Dispatchers.IO) {
        init()
        val beforeVersion = YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
        Log.i("SpikeYtDlp", "yt-dlp version before update check: $beforeVersion")
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
            val afterVersion = YoutubeDL.getInstance().version(context.applicationContext) ?: beforeVersion
            Log.i("SpikeYtDlp", "yt-dlp update status: $status, version: $afterVersion")
            Pair(status?.name ?: "UNKNOWN", afterVersion)
        } catch (e: Exception) {
            val err = e.message ?: e.toString()
            Log.e("SpikeYtDlp", "yt-dlp update error: $err", e)
            Pair("ERROR: $err", beforeVersion)
        }
    }

    suspend fun getEngineVersion(): String = withContext(Dispatchers.IO) {
        init()
        YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
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
                // Standard User-Agent to match browser behavior
                addOption("--add-header", "User-Agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
            }

            var lastLine = ""
            YoutubeDL.getInstance().execute(request) { _, _, line ->
                if (!line.isNullOrBlank()) lastLine = line
            }

            val elapsed = System.currentTimeMillis() - startTime
            val files = downloadDir.listFiles()?.filter { it.lastModified() >= startTime } ?: emptyList()
            val downloadedFile = files.maxByOrNull { it.length() }
            val fileSize = downloadedFile?.length() ?: 0L

            var hasVideo = false
            var hasAudio = false
            var trackDetails = ""

            if (downloadedFile != null && downloadedFile.exists() && fileSize > 0) {
                try {
                    val extractor = MediaExtractor()
                    extractor.setDataSource(downloadedFile.absolutePath)
                    val tracks = mutableListOf<String>()
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: "unknown"
                        tracks.add(mime)
                        if (mime.startsWith("video/")) hasVideo = true
                        if (mime.startsWith("audio/")) hasAudio = true
                    }
                    extractor.release()
                    trackDetails = tracks.joinToString(", ")
                } catch (e: Exception) {
                    Log.w("SpikeYtDlp", "MediaExtractor error: ${e.message}")
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(downloadedFile.absolutePath)
                        hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
                        hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                        retriever.release()
                    } catch (e2: Exception) {
                        Log.w("SpikeYtDlp", "MediaMetadataRetriever error: ${e2.message}")
                    }
                }
            }

            Log.i(
                "SpikeYtDlp",
                "[SPIKE_YTDLP_RESULT] url=$url success=true timeMs=$elapsed sizeBytes=$fileSize file=${downloadedFile?.name} hasVideo=$hasVideo hasAudio=$hasAudio tracks=[$trackDetails]"
            )

            ReelTestResult(
                url = url,
                success = true,
                durationMs = elapsed,
                fileSizeBytes = fileSize,
                errorCategory = "None",
                message = "Downloaded successfully (${"%.2f".format(fileSize.toDouble() / (1024 * 1024))} MB in ${elapsed}ms) | Video=$hasVideo Audio=$hasAudio [$trackDetails]",
                hasVideoTrack = hasVideo,
                hasAudioTrack = hasAudio,
                trackInfo = trackDetails
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
