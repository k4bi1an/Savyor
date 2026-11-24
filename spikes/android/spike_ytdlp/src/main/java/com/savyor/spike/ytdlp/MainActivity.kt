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

class MainActivity : ComponentActivity() {

    private lateinit var ytDlpManager: YtDlpManager
    private val results = mutableStateListOf<ReelTestResult>()
    private var isTesting by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Ready to test")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ytDlpManager = YtDlpManager(this)

        setContent {
            Spike1Screen(
                results = results,
                isTesting = isTesting,
                statusMessage = statusMessage,
                onRunTest = { urls -> runTests(urls) },
                onClearCache = {
                    val freed = ytDlpManager.clearCache()
                    statusMessage = "Cleared cache (${"%.2f".format(freed.toDouble() / (1024 * 1024))} MB)"
                    results.clear()
                }
            )
        }

        // Handle test trigger via ADB intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        val testUrl = intent.getStringExtra("test_url")
        val testUrls = intent.getStringArrayListExtra("test_urls")
        val urlsToTest = when {
            !testUrls.isNullOrEmpty() -> testUrls
            !testUrl.isNullOrBlank() -> listOf(testUrl)
            intent.getBooleanExtra("run_default_test", false) -> listOf(
                "https://www.instagram.com/reel/Df-OIaqgGZy/",
                "https://www.instagram.com/reel/DfBEEFBo52M/"
            )
            else -> null
        }

        if (urlsToTest != null) {
            runTests(urlsToTest)
        }
    }

    private fun runTests(urls: List<String>) {
        if (isTesting) return
        isTesting = true
        results.clear()
        statusMessage = "Initializing engine..."

        lifecycleScope.launch {
            try {
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
    statusMessage: String,
    onRunTest: (List<String>) -> Unit,
    onClearCache: () -> Unit
) {
    var urlText by remember {
        mutableStateOf(
            "https://www.instagram.com/reel/Df-OIaqgGZy/\nhttps://www.instagram.com/reel/DfBEEFBo52M/"
        )
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("ABI: $primaryAbi", fontSize = 11.sp) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Device: ${Build.MODEL}", fontSize = 11.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // URL input field
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Instagram Reel URLs (one per line)") },
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
                    modifier = Modifier.weight(1f),
                    enabled = !isTesting && urlText.isNotBlank(),
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
                    onClick = onClearCache,
                    enabled = !isTesting
                ) {
                    Text("Clear Cache")
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
                                Text(
                                    text = if (res.success) "PASS" else "FAIL (${res.errorCategory})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.success) Color(0xFF2F5D50) else Color(0xFFB3382C)
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
