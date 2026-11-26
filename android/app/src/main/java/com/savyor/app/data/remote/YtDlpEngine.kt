package com.savyor.app.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.savyor.app.data.local.EncryptedSettingsManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class ReelDownloadResult(
    val videoFile: File,
    val creator: String,
    val caption: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val creatorUsername: String = ""
)

interface IReelDownloader {
    suspend fun downloadReel(url: String, shortcode: String): ReelDownloadResult
    suspend fun downloadReel(
        url: String,
        shortcode: String,
        onProgress: (Float, String) -> Unit
    ): ReelDownloadResult = downloadReel(url, shortcode)
    suspend fun updateEngine(): Pair<String, String>
    suspend fun getEngineVersion(): String
}

class YtDlpEngine(private val context: Context) : IReelDownloader {

    companion object {
        private val engineMutex = Mutex()
        @Volatile
        private var initialized = false
    }

    suspend fun init() {
        if (!initialized) {
            engineMutex.withLock {
                if (!initialized) {
                    YoutubeDL.getInstance().init(context.applicationContext)
                    initialized = true
                }
            }
        }
    }

    override suspend fun updateEngine(): Pair<String, String> = withContext(Dispatchers.IO) {
        init()
        engineMutex.withLock {
            val settings = EncryptedSettingsManager(context.applicationContext)
            settings.lastYtDlpUpdateCheckMs = System.currentTimeMillis()
            val beforeVersion = YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
                val afterVersion = YoutubeDL.getInstance().version(context.applicationContext) ?: beforeVersion
                settings.ytDlpInstalledVersion = afterVersion
                settings.ytDlpLatestVersion = afterVersion
                settings.ytDlpLastCheckedEpochMs = System.currentTimeMillis()
                settings.ytDlpUpdateStatus = "UP_TO_DATE"
                Log.i("YtDlpEngine", "yt-dlp update status: $status, version: $afterVersion")
                Pair(status?.name ?: "UNKNOWN", afterVersion)
            } catch (e: Exception) {
                val err = e.message ?: e.toString()
                Log.e("YtDlpEngine", "yt-dlp update failed: $err", e)
                Pair("ERROR: $err", beforeVersion)
            }
        }
    }

    override suspend fun getEngineVersion(): String = withContext(Dispatchers.IO) {
        init()
        val version = YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
        if (version != "unknown") {
            EncryptedSettingsManager(context.applicationContext).ytDlpInstalledVersion = version
        }
        version
    }

    /**
     * Checks GitHub latest release tag against installed yt-dlp version,
     * throttled to once every 6 hours unless [force] is true. Fails silently offline.
     */
    suspend fun refreshUpdateStatusIfNeeded(force: Boolean = false) = withContext(Dispatchers.IO) {
        val settings = EncryptedSettingsManager(context.applicationContext)
        val sixHoursMs = 6L * 3600L * 1000L
        val now = System.currentTimeMillis()
        if (!force && (now - settings.ytDlpLastCheckedEpochMs) < sixHoursMs && settings.ytDlpUpdateStatus != "UNKNOWN") {
            return@withContext
        }
        try {
            val installed = getEngineVersion()
            settings.ytDlpInstalledVersion = installed
            val latest = fetchLatestGitHubVersion()
            settings.ytDlpLastCheckedEpochMs = System.currentTimeMillis()
            if (latest != null && installed != "unknown") {
                settings.ytDlpLatestVersion = latest
                val cleanInstalled = installed.trim().removePrefix("v")
                val cleanLatest = latest.trim().removePrefix("v")
                settings.ytDlpUpdateStatus = if (cleanInstalled == cleanLatest) "UP_TO_DATE" else "UPDATE_AVAILABLE"
            } else if (installed != "unknown") {
                settings.ytDlpUpdateStatus = "UNKNOWN"
            }
        } catch (e: Exception) {
            Log.w("YtDlpEngine", "yt-dlp release check failed silently: ${e.message}")
        }
    }

    private fun fetchLatestGitHubVersion(): String? {
        val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            val obj = JsonParser.parseString(body).asJsonObject
            return obj.get("tag_name")?.asString
        }
    }

    override suspend fun downloadReel(url: String, shortcode: String): ReelDownloadResult =
        downloadReel(url, shortcode) { _, _ -> }

    override suspend fun downloadReel(
        url: String,
        shortcode: String,
        onProgress: (Float, String) -> Unit
    ): ReelDownloadResult = withContext(Dispatchers.IO) {
        init()
        val settings = EncryptedSettingsManager(context.applicationContext)
        try {
            executeDownload(url, shortcode, onProgress)
        } catch (firstError: Exception) {
            val elapsedSinceCheck = System.currentTimeMillis() - settings.lastYtDlpUpdateCheckMs
            val twelveHoursMs = 12L * 3600L * 1000L
            if (elapsedSinceCheck > twelveHoursMs) {
                Log.w("YtDlpEngine", "Download failed (${firstError.message}); running self-healing yt-dlp update and retrying once...")
                onProgress(0f, "Updating downloader engine...")
                updateEngine()
                executeDownload(url, shortcode, onProgress)
            } else {
                throw firstError
            }
        }
    }

    private fun executeDownload(
        url: String,
        shortcode: String,
        onProgress: (Float, String) -> Unit
    ): ReelDownloadResult {
        val startTime = System.currentTimeMillis()
        val downloadDir = File(context.cacheDir, "reels_temp").apply { mkdirs() }
        val outputTemplate = File(downloadDir, "$shortcode.%(ext)s").absolutePath

        var creator = ""
        var creatorUsername = ""
        var caption = ""

        onProgress(0f, "Connecting to Instagram...")

        // Download compact single-file MP4 (<= 480p preferred for low-latency Gemini upload, 720p fallback)
        // along with --write-info-json in a single request to avoid double-hitting Instagram.
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--write-info-json")
            addOption("-f", "b[height<=480][ext=mp4]/b[height<=720][ext=mp4]/b[ext=mp4]/b")
            addOption("-o", outputTemplate)
            addOption("--socket-timeout", "25")
            addOption("--retries", "3")
            addOption("--fragment-retries", "3")
            addOption("--extractor-retries", "2")
            addOption("--add-header", "User-Agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            addOption("--add-header", "Accept-Language:en-US,en;q=0.9")
        }

        YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, _ ->
            if (progress >= 0f) {
                val pct = progress.coerceIn(0f, 100f)
                val detail = if (etaInSeconds > 0 && pct < 99f) {
                    "Downloading video · ${pct.toInt()}% (${etaInSeconds}s)"
                } else {
                    "Downloading video · ${pct.toInt()}%"
                }
                onProgress(pct, detail)
            }
        }

        val infoJsonFile = File(downloadDir, "$shortcode.info.json")
        if (infoJsonFile.exists()) {
            try {
                val json = JsonParser.parseString(infoJsonFile.readText()).asJsonObject
                val uploader = json.get("uploader")?.asString?.trim().orEmpty()
                val channel = json.get("channel")?.asString?.trim().orEmpty()
                val uploaderId = json.get("uploader_id")?.asString?.trim().orEmpty()
                val rawTitle = json.get("title")?.asString?.trim().orEmpty()
                val titleHandle = if (rawTitle.startsWith("Video by ", ignoreCase = true)) {
                    rawTitle.substringAfter("Video by ", "").trim()
                } else ""
                creatorUsername = channel
                    .ifBlank { uploaderId }
                    .ifBlank { titleHandle }
                    .removePrefix("@")
                    .trim()
                creator = uploader.ifBlank { creatorUsername }
                caption = json.get("description")?.asString ?: ""
            } catch (e: Exception) {
                Log.w("YtDlpEngine", "Failed reading info.json for $shortcode: ${e.message}")
            } finally {
                runCatching { infoJsonFile.delete() }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        val downloadedFiles = downloadDir.listFiles()?.filter { file ->
            file.name.startsWith(shortcode) &&
                !file.name.endsWith(".json") &&
                !file.name.endsWith(".part") &&
                !file.name.endsWith(".ytdl")
        } ?: emptyList()
        val videoFile = downloadedFiles.maxByOrNull { it.length() }
            ?: throw Exception("No video file downloaded for reel")

        val fileSize = videoFile.length()
        if (fileSize <= 0L) {
            throw Exception("Downloaded video file is empty (0 bytes)")
        }
        Log.i("YtDlpEngine", "Downloaded reel $shortcode: size=$fileSize bytes in ${elapsed}ms")

        return ReelDownloadResult(
            videoFile = videoFile,
            creator = creator,
            caption = caption,
            durationMs = elapsed,
            fileSizeBytes = fileSize,
            creatorUsername = creatorUsername
        )
    }
}
