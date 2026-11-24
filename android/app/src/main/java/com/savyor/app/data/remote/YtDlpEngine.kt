package com.savyor.app.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ReelDownloadResult(
    val videoFile: File,
    val creator: String,
    val caption: String,
    val durationMs: Long,
    val fileSizeBytes: Long
)

interface IReelDownloader {
    suspend fun downloadReel(url: String, shortcode: String): ReelDownloadResult
    suspend fun updateEngine(): Pair<String, String>
    suspend fun getEngineVersion(): String
}

class YtDlpEngine(private val context: Context) : IReelDownloader {

    private var initialized = false

    fun init() {
        if (!initialized) {
            YoutubeDL.getInstance().init(context.applicationContext)
            initialized = true
        }
    }

    override suspend fun updateEngine(): Pair<String, String> = withContext(Dispatchers.IO) {
        init()
        val beforeVersion = YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
            val afterVersion = YoutubeDL.getInstance().version(context.applicationContext) ?: beforeVersion
            Log.i("YtDlpEngine", "yt-dlp update status: $status, version: $afterVersion")
            Pair(status?.name ?: "UNKNOWN", afterVersion)
        } catch (e: Exception) {
            val err = e.message ?: e.toString()
            Log.e("YtDlpEngine", "yt-dlp update failed: $err", e)
            Pair("ERROR: $err", beforeVersion)
        }
    }

    override suspend fun getEngineVersion(): String = withContext(Dispatchers.IO) {
        init()
        YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
    }

    override suspend fun downloadReel(url: String, shortcode: String): ReelDownloadResult = withContext(Dispatchers.IO) {
        init()
        val startTime = System.currentTimeMillis()
        val downloadDir = File(context.cacheDir, "reels_temp").apply { mkdirs() }
        val outputTemplate = File(downloadDir, "$shortcode.%(ext)s").absolutePath

        var creator = ""
        var caption = ""

        // First attempt to get metadata JSON if possible
        try {
            val infoRequest = YoutubeDLRequest(url).apply {
                addOption("--dump-json")
                addOption("--no-playlist")
                addOption("--socket-timeout", "15")
                addOption("--add-header", "User-Agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            }
            val infoOutput = StringBuilder()
            YoutubeDL.getInstance().execute(infoRequest) { _, _, line ->
                if (!line.isNullOrBlank()) infoOutput.append(line)
            }
            if (infoOutput.isNotEmpty()) {
                val json = JsonParser.parseString(infoOutput.toString()).asJsonObject
                creator = json.get("uploader")?.asString
                    ?: json.get("channel")?.asString
                    ?: json.get("uploader_id")?.asString
                    ?: ""
                caption = json.get("description")?.asString ?: ""
            }
        } catch (e: Exception) {
            Log.w("YtDlpEngine", "Metadata fetch non-fatal error: ${e.message}")
        }

        // Download video capped to 720p progressive MP4
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-f", "best[height<=720][ext=mp4]/best[ext=mp4]/best")
            addOption("-o", outputTemplate)
            addOption("--socket-timeout", "30")
            addOption("--add-header", "User-Agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
        }

        YoutubeDL.getInstance().execute(request) { _, _, _ -> }

        val elapsed = System.currentTimeMillis() - startTime
        val downloadedFiles = downloadDir.listFiles()?.filter { it.name.startsWith(shortcode) } ?: emptyList()
        val videoFile = downloadedFiles.maxByOrNull { it.length() }
            ?: throw Exception("Download succeeded but no output file found for shortcode $shortcode")

        val fileSize = videoFile.length()
        Log.i("YtDlpEngine", "Downloaded reel $shortcode: size=$fileSize bytes in ${elapsed}ms")

        ReelDownloadResult(
            videoFile = videoFile,
            creator = creator,
            caption = caption,
            durationMs = elapsed,
            fileSizeBytes = fileSize
        )
    }
}
