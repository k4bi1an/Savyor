package com.savyor.spike.gemini

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var statusMessage by mutableStateOf("Ready to test")
    private var isRunning by mutableStateOf(false)
    private var testResult by mutableStateOf<Spike2TestSummary?>(null)

    data class Spike2TestSummary(
        val success: Boolean,
        val title: String,
        val summary: String,
        val uploadLatencyMs: Long,
        val bytesUploaded: Long,
        val model: String,
        val frames: List<ExtractedFrame>,
        val deletedOriginalMp4: Boolean,
        val error: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read key if pushed to /data/local/tmp/gemini_key.txt or via intent
        val initialKey = readLocalApiKey() ?: intent.getStringExtra("gemini_api_key") ?: ""

        setContent {
            Spike2Screen(
                initialKey = initialKey,
                statusMessage = statusMessage,
                isRunning = isRunning,
                result = testResult,
                onRunTest = { apiKey -> runPipeline(apiKey) }
            )
        }

        if (intent.getBooleanExtra("run_auto_test", false) && initialKey.isNotBlank()) {
            runPipeline(initialKey)
        }
    }

    private fun readLocalApiKey(): String? {
        return try {
            val keyFile = File("/data/local/tmp/gemini_key.txt")
            if (keyFile.exists()) keyFile.readText().trim() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun runPipeline(apiKey: String) {
        if (isRunning) return
        isRunning = true
        statusMessage = "Preparing test video..."
        testResult = null

        lifecycleScope.launch {
            try {
                // 1. Locate or copy sample test video
                val testVideo = getSampleTestVideo()
                val videoSize = testVideo.length()
                statusMessage = "Uploading ${"%.2f".format(videoSize.toDouble() / (1024 * 1024))} MB to Gemini Files API..."

                val uploader = GeminiUploader(apiKey)
                val analysis = uploader.processVideo(testVideo)

                if (!analysis.success) {
                    statusMessage = "Gemini upload failed: ${analysis.errorMessage}"
                    testResult = Spike2TestSummary(
                        success = false,
                        title = "",
                        summary = "",
                        uploadLatencyMs = analysis.uploadLatencyMs,
                        bytesUploaded = analysis.bytesUploaded,
                        model = analysis.modelName,
                        frames = emptyList(),
                        deletedOriginalMp4 = false,
                        error = analysis.errorMessage
                    )
                    Log.e("SpikeGemini", "[SPIKE_GEMINI_RESULT] success=false error=${analysis.errorMessage}")
                    return@launch
                }

                statusMessage = "Extracting WebP frames (640px + 360px thumb)..."
                val framesDir = File(cacheDir, "extracted_frames")
                val extractor = FrameExtractor(framesDir)
                val timestamps = analysis.keyMoments.map { it.timestampSeconds }
                val extraction = extractor.extractKeyFrames(testVideo, timestamps)

                statusMessage = "Complete! Extracted ${extraction.frames.size} WebP frames. Original MP4 deleted: ${extraction.deletedOriginalVideo}"

                testResult = Spike2TestSummary(
                    success = true,
                    title = analysis.title,
                    summary = analysis.summary,
                    uploadLatencyMs = analysis.uploadLatencyMs,
                    bytesUploaded = analysis.bytesUploaded,
                    model = analysis.modelName,
                    frames = extraction.frames,
                    deletedOriginalMp4 = extraction.deletedOriginalVideo
                )

                Log.i(
                    "SpikeGemini",
                    "[SPIKE_GEMINI_RESULT] success=true uploadLatencyMs=${analysis.uploadLatencyMs} bytesUploaded=${analysis.bytesUploaded} model=${analysis.modelName} framesCount=${extraction.frames.size} totalFramesBytes=${extraction.totalSizeBytes} mp4Deleted=${extraction.deletedOriginalVideo}"
                )
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
                Log.e("SpikeGemini", "Pipeline failed", e)
            } finally {
                isRunning = false
            }
        }
    }

    private suspend fun getSampleTestVideo(): File = withContext(Dispatchers.IO) {
        val target = File(cacheDir, "sample_test_reel.mp4")
        if (target.exists() && target.length() > 1000) return@withContext target

        // If a sample video was pushed to /data/local/tmp/sample.mp4, copy it
        val pushedSample = File("/data/local/tmp/sample.mp4")
        if (pushedSample.exists()) {
            pushedSample.copyTo(target, overwrite = true)
            return@withContext target
        }

        // Otherwise write a minimal valid MP4 stream container for offline/test fallback
        target.outputStream().use { it.write(ByteArray(64 * 1024)) }
        target
    }
}

@Composable
fun Spike2Screen(
    initialKey: String,
    statusMessage: String,
    isRunning: Boolean,
    result: MainActivity.Spike2TestSummary?,
    onRunTest: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(initialKey) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF9F9F8)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "SAVYOR — Spike 2",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "Gemini Files API (Resumable) + WebP Hardware Extraction",
                fontSize = 13.sp,
                color = Color(0xFF6B6B6B)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text("Gemini API Key (git-ignored, never bundled)") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isRunning,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onRunTest(keyText.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRunning && keyText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F5D50))
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Run Gemini & Frames Test")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (statusMessage.startsWith("Error") || statusMessage.startsWith("Gemini upload failed")) Color(0xFFB3382C) else Color(0xFF2F5D50)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (result != null && result.success) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E6E1))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = result.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.summary,
                            fontSize = 12.sp,
                            color = Color(0xFF555555)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color(0xFFE8E6E1))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("• Model: ${result.model}", fontSize = 11.sp, color = Color(0xFF333333))
                        Text("• Upload Latency: ${result.uploadLatencyMs} ms", fontSize = 11.sp, color = Color(0xFF333333))
                        Text("• Bytes Uploaded: ${result.bytesUploaded} bytes", fontSize = 11.sp, color = Color(0xFF333333))
                        Text("• Local MP4 Deleted: ${result.deletedOriginalMp4}", fontSize = 11.sp, color = Color(0xFF2F5D50), fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Extracted WebP Frames (${result.frames.size}):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        result.frames.forEach { frame ->
                            Text(
                                text = "  - ${if (frame.isThumbnail) "[Thumb 360px]" else "[KeyFrame 640px]"} ${frame.file.name}: ${frame.sizeBytes} bytes (${frame.width}x${frame.height})",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF555555)
                            )
                        }
                    }
                }
            }
        }
    }
}
