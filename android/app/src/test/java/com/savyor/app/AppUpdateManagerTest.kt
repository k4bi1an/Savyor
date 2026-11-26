package com.savyor.app

import com.savyor.app.data.remote.AppReleaseAsset
import com.savyor.app.data.remote.AppUpdateCheckResult
import com.savyor.app.data.remote.AppUpdateManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSemanticVersionComparison() {
        assertTrue(AppUpdateManager.compareSemanticVersions("1.2.0", "1.1.0") > 0)
        assertTrue(AppUpdateManager.compareSemanticVersions("v2.0.0", "1.9.9") > 0)
        assertTrue(AppUpdateManager.compareSemanticVersions("1.1.1", "1.1.0") > 0)
        assertEquals(0, AppUpdateManager.compareSemanticVersions("v1.1.0", "1.1.0"))
        assertTrue(AppUpdateManager.compareSemanticVersions("1.0.9", "1.1.0") < 0)
    }

    @Test
    fun testAbiAwareApkAssetSelection() {
        val assets = listOf(
            AppReleaseAsset("app-x86_64-release.apk", "https://example.com/x86_64.apk", 22_000_000L),
            AppReleaseAsset("app-arm64-v8a-release.apk", "https://example.com/arm64.apk", 22_000_000L),
            AppReleaseAsset("app-universal-release.apk", "https://example.com/universal.apk", 64_000_000L),
            AppReleaseAsset("app-arm64-v8a-debug.apk", "https://example.com/debug.apk", 40_000_000L)
        )

        val arm64Pick = AppUpdateManager.selectApkAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-arm64-v8a-release.apk", arm64Pick?.name)

        val fallbackUniversal = AppUpdateManager.selectApkAsset(assets, listOf("armeabi-v7a"))
        assertEquals("app-universal-release.apk", fallbackUniversal?.name)
    }

    @Test
    fun testParseGitHubReleaseUpdateAndSha256() {
        val sampleDigest = "a".repeat(64)
        val releaseJson = """
            {
              "tag_name": "v1.2.0",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/k4bi1an/Savyor/releases/tag/v1.2.0",
              "body": "versionCode: 3\n- Faster fallback\n- Drag and drop",
              "assets": [
                {
                  "name": "app-arm64-v8a-release.apk",
                  "browser_download_url": "https://github.com/k4bi1an/Savyor/releases/download/v1.2.0/app-arm64-v8a-release.apk",
                  "size": 22500000,
                  "digest": "sha256:$sampleDigest"
                }
              ]
            }
        """.trimIndent()

        val result = AppUpdateManager.parseReleaseUpdate(
            jsonBody = releaseJson,
            currentVersionName = "1.1.0",
            currentVersionCode = 2,
            supportedAbis = listOf("arm64-v8a")
        )
        assertTrue(result is AppUpdateCheckResult.UpdateAvailable)
        val info = (result as AppUpdateCheckResult.UpdateAvailable).info
        assertEquals("1.2.0", info.versionName)
        assertEquals(3, info.versionCode)
        assertEquals("app-arm64-v8a-release.apk", info.asset.name)
        assertEquals(sampleDigest, info.asset.sha256)

        val sameVersion = AppUpdateManager.parseReleaseUpdate(
            jsonBody = releaseJson,
            currentVersionName = "1.2.0",
            currentVersionCode = 3,
            supportedAbis = listOf("arm64-v8a")
        )
        assertTrue(sameVersion is AppUpdateCheckResult.UpToDate)
    }

    @Test
    fun testComputeSha256MatchesExpectedDigest() {
        val file = tempFolder.newFile("sample.apk").apply {
            writeText("savyor-ota-test", Charsets.UTF_8)
        }
        val sha = AppUpdateManager.computeSha256(file)
        assertEquals(64, sha.length)
        assertEquals(sha, AppUpdateManager.computeSha256(file))
    }
}
