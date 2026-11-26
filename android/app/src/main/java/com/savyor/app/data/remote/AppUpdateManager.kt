package com.savyor.app.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.savyor.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String? = null
)

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int?,
    val releaseNotes: String,
    val htmlUrl: String,
    val asset: AppReleaseAsset
)

sealed class AppUpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateCheckResult()
    data class UpToDate(val currentVersion: String) : AppUpdateCheckResult()
    data class Error(val message: String) : AppUpdateCheckResult()
}

sealed class AppInstallLaunchResult {
    data object LaunchedInstaller : AppInstallLaunchResult()
    data object NeedsInstallPermission : AppInstallLaunchResult()
    data class Error(val message: String) : AppInstallLaunchResult()
}

class AppUpdateManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "AppUpdateManager"
        const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/k4bi1an/Savyor/releases/latest"

        fun cleanupCachedUpdates(cacheDir: File) {
            val updatesDir = File(cacheDir, "updates")
            if (updatesDir.exists() && updatesDir.isDirectory) {
                updatesDir.listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }
            }
        }

        internal fun compareSemanticVersions(remote: String, current: String): Int {
            val rParts = normalizeVersionParts(remote)
            val cParts = normalizeVersionParts(current)
            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r != c) return r.compareTo(c)
            }
            return 0
        }

        private fun normalizeVersionParts(raw: String): List<Int> {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .substringBefore("+")
                .split(".")
                .mapNotNull { it.trim().toIntOrNull() }
                .ifEmpty { listOf(0) }
        }

        internal fun selectApkAsset(
            assets: List<AppReleaseAsset>,
            supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
        ): AppReleaseAsset? {
            val apkAssets = assets.filter {
                it.name.endsWith(".apk", ignoreCase = true) &&
                    !it.name.contains("androidTest", ignoreCase = true)
            }
            if (apkAssets.isEmpty()) return null

            val releaseApks = apkAssets.filterNot { it.name.contains("debug", ignoreCase = true) }
                .ifEmpty { apkAssets }

            for (abi in supportedAbis) {
                val abiMatch = releaseApks.firstOrNull {
                    it.name.contains(abi, ignoreCase = true)
                }
                if (abiMatch != null) return abiMatch
            }

            return releaseApks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
                ?: releaseApks.firstOrNull()
        }

        internal fun parseReleaseUpdate(
            jsonBody: String,
            currentVersionName: String = BuildConfig.VERSION_NAME,
            currentVersionCode: Int = BuildConfig.VERSION_CODE,
            supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
        ): AppUpdateCheckResult {
            val root = JsonParser.parseString(jsonBody).asJsonObject
            if (root.get("draft")?.asBoolean == true || root.get("prerelease")?.asBoolean == true) {
                return AppUpdateCheckResult.UpToDate(currentVersionName)
            }

            val tagName = root.get("tag_name")?.asString?.trim().orEmpty()
            val cleanVersion = tagName.removePrefix("v").removePrefix("V")
            if (cleanVersion.isBlank()) {
                return AppUpdateCheckResult.UpToDate(currentVersionName)
            }

            val releaseBody = root.get("body")?.asString.orEmpty()
            val htmlUrl = root.get("html_url")?.asString.orEmpty()
            val remoteVersionCode = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(releaseBody)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

            val isNewer = if (remoteVersionCode != null) {
                remoteVersionCode > currentVersionCode ||
                    compareSemanticVersions(cleanVersion, currentVersionName) > 0
            } else {
                compareSemanticVersions(cleanVersion, currentVersionName) > 0
            }

            if (!isNewer) {
                return AppUpdateCheckResult.UpToDate(currentVersionName)
            }

            val rawAssets = parseAssetsArray(root.getAsJsonArray("assets"), releaseBody)
            val chosenAsset = selectApkAsset(rawAssets, supportedAbis)
                ?: return AppUpdateCheckResult.Error("Release v$cleanVersion has no downloadable APK asset yet")

            return AppUpdateCheckResult.UpdateAvailable(
                AppUpdateInfo(
                    versionName = cleanVersion,
                    versionCode = remoteVersionCode,
                    releaseNotes = releaseBody.trim(),
                    htmlUrl = htmlUrl,
                    asset = chosenAsset
                )
            )
        }

        private fun parseAssetsArray(assetsArray: JsonArray?, releaseBody: String): List<AppReleaseAsset> {
            if (assetsArray == null) return emptyList()
            val out = mutableListOf<AppReleaseAsset>()
            for (elem in assetsArray) {
                if (!elem.isJsonObject) continue
                val obj: JsonObject = elem.asJsonObject
                val name = obj.get("name")?.asString.orEmpty()
                val url = obj.get("browser_download_url")?.asString.orEmpty()
                val size = obj.get("size")?.asLong ?: 0L
                val rawDigest = obj.get("digest")?.asString.orEmpty()
                val digestSha = rawDigest
                    .takeIf { it.startsWith("sha256:", ignoreCase = true) }
                    ?.substringAfter(":")
                    ?.trim()
                    ?.takeIf { it.length == 64 }
                    ?: extractSha256ForAsset(releaseBody, name)

                if (name.isNotBlank() && url.isNotBlank()) {
                    out.add(
                        AppReleaseAsset(
                            name = name,
                            downloadUrl = url,
                            sizeBytes = size,
                            sha256 = digestSha
                        )
                    )
                }
            }
            return out
        }

        private fun extractSha256ForAsset(body: String, assetName: String): String? {
            if (body.isBlank()) return null
            val escaped = Regex.escape(assetName)
            val assetSpecific = Regex("""([a-fA-F0-9]{64})\s+\*?$escaped""")
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
            if (assetSpecific != null) return assetSpecific.lowercase()
            return null
        }

        internal fun computeSha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun checkForUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Savyor-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    return@withContext AppUpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME)
                }
                if (!resp.isSuccessful) {
                    return@withContext AppUpdateCheckResult.Error("GitHub returned HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                parseReleaseUpdate(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
            AppUpdateCheckResult.Error(e.message ?: "Update check failed")
        }
    }

    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            cleanupCachedUpdates(context.cacheDir)
            val targetFile = File(updatesDir, "savyor-${updateInfo.versionName}.apk")

            val req = Request.Builder()
                .url(updateInfo.asset.downloadUrl)
                .header("User-Agent", "Savyor-Android/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw Exception("APK download failed (HTTP ${resp.code})")
                }
                val body = resp.body ?: throw Exception("Empty APK response body")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: updateInfo.asset.sizeBytes

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val pct = if (totalBytes > 0L) {
                                ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct, downloaded, totalBytes)
                            }
                        }
                        output.flush()
                    }
                }
            }

            val expectedSha = updateInfo.asset.sha256
            if (!expectedSha.isNullOrBlank()) {
                val actualSha = computeSha256(targetFile)
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    targetFile.delete()
                    throw SecurityException("APK SHA-256 verification failed")
                }
            }

            targetFile
        }
    }

    fun launchInstaller(context: Context, apkFile: File): AppInstallLaunchResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return AppInstallLaunchResult.NeedsInstallPermission
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            AppInstallLaunchResult.LaunchedInstaller
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching APK installer: ${e.message}", e)
            AppInstallLaunchResult.Error(e.message ?: "Could not open package installer")
        }
    }
}
