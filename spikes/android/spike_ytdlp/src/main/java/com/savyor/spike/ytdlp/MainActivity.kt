package com.savyor.spike.ytdlp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var ytDlpManager: YtDlpManager
    private val results = mutableStateListOf<ReelTestResult>()
    private var isTesting by mutableStateOf(false)
    private var isUpdating by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Ready to test")
    private var engineVersion by mutableStateOf("Checking...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ytDlpManager = YtDlpManager(this)

        setContent {
            Spike1Screen(
                results = results,
                isTesting = isTesting,
                isUpdating = isUpdating,
                statusMessage = statusMessage,
                engineVersion = engineVersion,
                onRunTest = { urls -> runTests(urls) },
                onUpdateEngine = { triggerUpdate() },
                onClearCache = {
                    val freed = ytDlpManager.clearCache()
                    statusMessage = "Cleared cache (${"%.2f".format(freed.toDouble() / (1024 * 1024))} MB)"
                    results.clear()
                }
            )
        }

        // Check/update yt-dlp on startup
        triggerUpdate {
            // Once updated or initialized, check incoming intent
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i("SpikeYtDlp", "onNewIntent received")
        intent?.let { handleIntent(it) }
    }

    private fun triggerUpdate(onComplete: (() -> Unit)? = null) {
        if (isUpdating) return
        isUpdating = true
        statusMessage = "Checking/updating yt-dlp to latest release..."

        lifecycleScope.launch {
            try {
                val (status, version) = ytDlpManager.updateEngine()
                engineVersion = version
                statusMessage = "yt-dlp $version ($status)"
                Log.i("SpikeYtDlp", "Engine ready: version=$version status=$status")
            } catch (e: Exception) {
                statusMessage = "Engine update error: ${e.message}"
                Log.e("SpikeYtDlp", "Engine update error", e)
            } finally {
                isUpdating = false
                onComplete?.invoke()
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val testUrl = intent.getStringExtra("test_url")
        val testUrls = intent.getStringArrayListExtra("test_urls")
        val testUrlsCsv = intent.getStringExtra("test_urls_csv")
        val filePath = intent.getStringExtra("urls_file")
        Log.i("SpikeYtDlp", "handleIntent: testUrl=$testUrl testUrlsCsv=$testUrlsCsv isUpdating=$isUpdating isTesting=$isTesting")

        val urlsToTest: List<String>? = when {
            !testUrls.isNullOrEmpty() -> testUrls
            !testUrl.isNullOrBlank() -> listOf(testUrl)
            !testUrlsCsv.isNullOrBlank() -> testUrlsCsv.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            !filePath.isNullOrBlank() -> {
                val file = File(filePath)
                if (file.exists()) file.readLines().map { it.trim() }.filter { it.isNotEmpty() } else null
            }
            else -> null
        }

        if (!urlsToTest.isNullOrEmpty()) {
            runTests(urlsToTest)
        }
    }

    private fun runTests(urls: List<String>) {
        if (isTesting) {
            Log.w("SpikeYtDlp", "runTests skipped: already testing")
            return
        }
        isTesting = true
        results.clear()
        statusMessage = "Preparing to test ${urls.size} reel(s)..."

        lifecycleScope.launch {
            try {
                while (isUpdating) {
                    kotlinx.coroutines.delay(200)
                }
                ytDlpManager.init()
                urls.forEachIndexed { index, url ->
                    val cleanUrl = url.trim()
                    if (cleanUrl.isNotEmpty()) {
                        statusMessage = "Testing [${index + 1}/${urls.size}]..."
                        val result = ytDlpManager.downloadReel(cleanUrl)
                        results.add(result)
                    }
                }
                statusMessage = "Tests complete: ${results.count { it.success }}/${results.size} passed"
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
                Log.e("SpikeYtDlp", "Run test failed", e)
            } finally {
                isTesting = false
            }
        }
    }
}

@Composable
fun Spike1Screen(
    results: List<ReelTestResult>,
    isTesting: Boolean,
    isUpdating: Boolean,
    statusMessage: String,
    engineVersion: String,
    onRunTest: (List<String>) -> Unit,
    onUpdateEngine: () -> Unit,
    onClearCache: () -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9F9F8)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "SAVYOR — Spike 1",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "youtubedl-android Native Engine (No FFmpeg)",
                fontSize = 13.sp,
                color = Color(0xFF6B6B6B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Device Info Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("ABI: $primaryAbi", fontSize = 11.sp) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Device: ${Build.MODEL}", fontSize = 11.sp) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("yt-dlp: $engineVersion", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // URL input field
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Instagram Reel URLs (one per line)") },
                placeholder = { Text("Paste Instagram URLs or trigger via ADB") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                enabled = !isTesting,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val urls = urlText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        onRunTest(urls)
                    },
                    modifier = Modifier.weight(1.2f),
                    enabled = !isTesting && !isUpdating && urlText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F5D50))
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Test Reels")
                }

                OutlinedButton(
                    onClick = onUpdateEngine,
                    modifier = Modifier.weight(1f),
                    enabled = !isTesting && !isUpdating
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFF2F5D50),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text("Update", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onClearCache,
                    enabled = !isTesting && !isUpdating
                ) {
                    Text("Clear", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status bar
            Text(
                text = statusMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (statusMessage.startsWith("Error")) Color(0xFFB3382C) else Color(0xFF2F5D50)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Results List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.success) Color(0xFFFFFFFF) else Color(0xFFFFF4F2)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (res.success) Color(0xFFE8E6E1) else Color(0xFFF1B4AE)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusText = when {
                                    res.success && res.hasVideoTrack && res.hasAudioTrack -> "PASS (Video + Audio)"
                                    res.success && res.hasVideoTrack -> "PASS (Video Only)"
                                    res.success -> "PASS"
                                    else -> "FAIL (${res.errorCategory})"
                                }
                                Text(
                                    text = statusText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.success && res.hasVideoTrack && res.hasAudioTrack) Color(0xFF2F5D50) else Color(0xFFB3382C)
                                )
                                Text(
                                    text = "${res.durationMs} ms",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B6B6B)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.url,
                                fontSize = 11.sp,
                                color = Color(0xFF333333),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = res.message,
                                fontSize = 11.sp,
                                color = Color(0xFF6B6B6B)
                            )
                        }
                    }
                }
            }
        }
    }
}
