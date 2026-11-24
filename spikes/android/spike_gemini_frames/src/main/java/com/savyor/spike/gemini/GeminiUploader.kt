package com.savyor.spike.gemini

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

data class GeminiAnalysisResult(
    val success: Boolean,
    val title: String,
    val summary: String,
    val keyMoments: List<KeyMoment>,
    val transcript: String,
    val onScreenUrls: List<String>,
    val uploadLatencyMs: Long,
    val bytesUploaded: Long,
    val modelName: String,
    val rawJson: String,
    val errorMessage: String? = null
)

data class KeyMoment(
    val timestampSeconds: Double,
    val label: String
)

class GeminiUploader(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val candidateModels = listOf("gemini-3.5-flash", "gemini-3.8-flash", "gemini-3-flash-preview")
    private var model = "gemini-3.5-flash"

    suspend fun processVideo(videoFile: File): GeminiAnalysisResult = withContext(Dispatchers.IO) {
        val totalBytes = videoFile.length()
        val uploadStart = System.currentTimeMillis()

        var remoteFileName: String? = null
        try {
            // Step 1: Initiate resumable upload session
            val initUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey"
            val initJson = JSONObject().apply {
                put("file", JSONObject().apply {
                    put("display_name", "savyor_spike_${videoFile.nameWithoutExtension}")
                })
            }

            val initReq = Request.Builder()
                .url(initUrl)
                .addHeader("X-Goog-Upload-Protocol", "resumable")
                .addHeader("X-Goog-Upload-Command", "start")
                .addHeader("X-Goog-Upload-Header-Content-Length", totalBytes.toString())
                .addHeader("X-Goog-Upload-Header-Content-Type", "video/mp4")
                .post(initJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val initResp = client.newCall(initReq).execute()
            if (!initResp.isSuccessful) {
                val err = initResp.body?.string() ?: "Init failed: HTTP ${initResp.code}"
                return@withContext GeminiAnalysisResult(
                    success = false,
                    title = "",
                    summary = "",
                    keyMoments = emptyList(),
                    transcript = "",
                    onScreenUrls = emptyList(),
                    uploadLatencyMs = 0L,
                    bytesUploaded = 0L,
                    modelName = model,
                    rawJson = "",
                    errorMessage = "Failed to initiate resumable upload: $err"
                )
            }

            val uploadUrl = initResp.header("X-Goog-Upload-URL")
                ?: return@withContext GeminiAnalysisResult(
                    success = false,
                    title = "",
                    summary = "",
                    keyMoments = emptyList(),
                    transcript = "",
                    onScreenUrls = emptyList(),
                    uploadLatencyMs = 0L,
                    bytesUploaded = 0L,
                    modelName = model,
                    rawJson = "",
                    errorMessage = "Missing X-Goog-Upload-URL header in response"
                )

            // Step 2: Upload in 8MB chunks (Google resumable upload requirement: multiple of 8,388,608 bytes)
            val chunkSize = 8 * 1024 * 1024 // 8MB granularity
            var offset = 0L
            val raf = RandomAccessFile(videoFile, "r")
            var fileUri = ""

            try {
                while (offset < totalBytes) {
                    val bytesToRead = minOf(chunkSize.toLong(), totalBytes - offset).toInt()
                    val chunkBuffer = ByteArray(bytesToRead)
                    raf.seek(offset)
                    raf.readFully(chunkBuffer)

                    val isLastChunk = (offset + bytesToRead >= totalBytes)
                    val command = if (isLastChunk) "upload, finalize" else "upload"

                    val chunkReq = Request.Builder()
                        .url(uploadUrl)
                        .addHeader("Content-Length", bytesToRead.toString())
                        .addHeader("X-Goog-Upload-Offset", offset.toString())
                        .addHeader("X-Goog-Upload-Command", command)
                        .post(chunkBuffer.toRequestBody("application/octet-stream".toMediaType()))
                        .build()

                    val chunkResp = client.newCall(chunkReq).execute()
                    val chunkBody = chunkResp.body?.string() ?: ""

                    if (!chunkResp.isSuccessful) {
                        return@withContext GeminiAnalysisResult(
                            success = false,
                            title = "",
                            summary = "",
                            keyMoments = emptyList(),
                            transcript = "",
                            onScreenUrls = emptyList(),
                            uploadLatencyMs = System.currentTimeMillis() - uploadStart,
                            bytesUploaded = offset,
                            modelName = model,
                            rawJson = "",
                            errorMessage = "Chunk upload failed at offset $offset: HTTP ${chunkResp.code} $chunkBody"
                        )
                    }

                    if (isLastChunk) {
                        val fileObj = JSONObject(chunkBody).getJSONObject("file")
                        remoteFileName = fileObj.getString("name") // e.g. "files/xyz123"
                        fileUri = fileObj.getString("uri")
                    }

                    offset += bytesToRead
                }
            } finally {
                raf.close()
            }

            val uploadLatency = System.currentTimeMillis() - uploadStart

            // Step 3: Poll until ACTIVE state
            if (remoteFileName != null) {
                var state = "PROCESSING"
                var attempts = 0
                while (state != "ACTIVE" && attempts < 20) {
                    delay(1500)
                    attempts++
                    val pollReq = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/$remoteFileName?key=$apiKey")
                        .get()
                        .build()
                    val pollResp = client.newCall(pollReq).execute()
                    if (pollResp.isSuccessful) {
                        val pollJson = JSONObject(pollResp.body?.string() ?: "{}")
                        state = pollJson.optString("state", "UNKNOWN")
                        if (state == "FAILED") {
                            return@withContext GeminiAnalysisResult(
                                success = false,
                                title = "",
                                summary = "",
                                keyMoments = emptyList(),
                                transcript = "",
                                onScreenUrls = emptyList(),
                                uploadLatencyMs = uploadLatency,
                                bytesUploaded = totalBytes,
                                modelName = model,
                                rawJson = "",
                                errorMessage = "Gemini File processing FAILED"
                            )
                        }
                    }
                }
            }

            // Step 4: Request structured JSON analysis
            val prompt = """
                Analyze this short Instagram Reel video. Extract key insights into a strict JSON object with:
                - "title": a clear, concise headline (max 8 words)
                - "summary": 2-3 sentence overview of the video
                - "timestamped_transcript": full spoken words with timestamps
                - "key_moments": array of objects with {"timestamp_seconds": float, "label": string} for up to 4 key visual points
                - "on_screen_urls": array of any URLs or handles visible in the video
                Respond ONLY with valid JSON matching this schema.
            """.trimIndent()

            var genBody = ""
            var genSuccess = false
            var lastErrorCode = 0
            for (candidate in candidateModels) {
                model = candidate
                val genContentUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val genJson = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("file_data", JSONObject().apply {
                                        put("mime_type", "video/mp4")
                                        put("file_uri", fileUri)
                                    })
                                })
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("response_mime_type", "application/json")
                    })
                }

                val genReq = Request.Builder()
                    .url(genContentUrl)
                    .post(genJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val genResp = client.newCall(genReq).execute()
                genBody = genResp.body?.string() ?: ""
                lastErrorCode = genResp.code
                if (genResp.isSuccessful) {
                    genSuccess = true
                    break
                } else if (genResp.code == 503 || genResp.code == 404) {
                    Log.w("SpikeGemini", "Model $candidate returned ${genResp.code}, trying next model...")
                    delay(1000)
                } else {
                    break
                }
            }

            if (!genSuccess) {
                return@withContext GeminiAnalysisResult(
                    success = false,
                    title = "",
                    summary = "",
                    keyMoments = emptyList(),
                    transcript = "",
                    onScreenUrls = emptyList(),
                    uploadLatencyMs = uploadLatency,
                    bytesUploaded = totalBytes,
                    modelName = model,
                    rawJson = genBody,
                    errorMessage = "Generation failed: HTTP $lastErrorCode $genBody"
                )
            }

            val genResultJson = JSONObject(genBody)
            val candidateText = genResultJson
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val parsedOutput = JSONObject(candidateText)
            val title = parsedOutput.optString("title", "Untitled Reel")
            val summary = parsedOutput.optString("summary", "")
            val transcript = parsedOutput.optString("timestamped_transcript", "")

            val keyMomentsList = mutableListOf<KeyMoment>()
            val kmArray = parsedOutput.optJSONArray("key_moments")
            if (kmArray != null) {
                for (i in 0 until kmArray.length()) {
                    val obj = kmArray.getJSONObject(i)
                    keyMomentsList.add(
                        KeyMoment(
                            timestampSeconds = obj.optDouble("timestamp_seconds", 0.0),
                            label = obj.optString("label", "Moment $i")
                        )
                    )
                }
            }

            val urlsList = mutableListOf<String>()
            val urlArray = parsedOutput.optJSONArray("on_screen_urls")
            if (urlArray != null) {
                for (i in 0 until urlArray.length()) {
                    urlsList.add(urlArray.getString(i))
                }
            }

            GeminiAnalysisResult(
                success = true,
                title = title,
                summary = summary,
                keyMoments = keyMomentsList,
                transcript = transcript,
                onScreenUrls = urlsList,
                uploadLatencyMs = uploadLatency,
                bytesUploaded = totalBytes,
                modelName = model,
                rawJson = candidateText
            )
        } catch (e: Exception) {
            Log.e("SpikeGemini", "Gemini analysis error", e)
            GeminiAnalysisResult(
                success = false,
                title = "",
                summary = "",
                keyMoments = emptyList(),
                transcript = "",
                onScreenUrls = emptyList(),
                uploadLatencyMs = System.currentTimeMillis() - uploadStart,
                bytesUploaded = totalBytes,
                modelName = model,
                rawJson = "",
                errorMessage = e.message ?: e.toString()
            )
        } finally {
            // Step 5: Always delete remote file to keep storage clean
            if (remoteFileName != null) {
                try {
                    val delReq = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/$remoteFileName?key=$apiKey")
                        .delete()
                        .build()
                    client.newCall(delReq).execute()
                    Log.i("SpikeGemini", "Deleted remote file: $remoteFileName")
                } catch (delEx: Exception) {
                    Log.w("SpikeGemini", "Failed to delete remote file $remoteFileName", delEx)
                }
            }
        }
    }
}
