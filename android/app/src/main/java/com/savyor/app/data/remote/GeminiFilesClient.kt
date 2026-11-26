package com.savyor.app.data.remote

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonParser
import com.savyor.app.domain.model.GeminiReelAnalysis
import com.savyor.app.domain.model.KeyMomentAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class GeminiApiException(val statusCode: Int, message: String) : Exception(message)

interface IGeminiClient {
    suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String
    ): GeminiReelAnalysis

    suspend fun testConnection(apiKey: String): Result<String>
}

class GeminiFilesClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson()
) : IGeminiClient {

    companion object {
        private const val TAG = "GeminiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com"
        const val MODEL_NAME = "gemini-2.5-flash"
        private const val CHUNK_SIZE = 8 * 1024 * 1024 // 8 MB required chunk granularity
        private const val INLINE_VIDEO_LIMIT_BYTES = 15L * 1024L * 1024L

        private val RESILIENT_GSON: Gson = GsonBuilder()
            .registerTypeAdapter(
                KeyMomentAnalysis::class.java,
                JsonDeserializer { json, _, _ ->
                    val obj = json.asJsonObject
                    val tsElem = obj.get("timestamp_sec") ?: obj.get("timestamp")
                    val sec = parseFlexibleTimestampSec(tsElem?.asString ?: tsElem?.toString() ?: "0")
                    val label = obj.get("label")?.asString ?: ""
                    KeyMomentAnalysis(timestampSec = sec, label = label)
                }
            )
            .create()

        internal fun parseFlexibleTimestampSec(raw: String?): Int {
            if (raw.isNullOrBlank()) return 0
            val clean = raw.trim().trim('"')
            clean.toDoubleOrNull()?.let { return it.toInt().coerceAtLeast(0) }
            val parts = clean.split(":")
            return when (parts.size) {
                2 -> {
                    val m = parts[0].trim().toIntOrNull() ?: 0
                    val s = parts[1].trim().toDoubleOrNull()?.toInt() ?: 0
                    (m * 60 + s).coerceAtLeast(0)
                }
                3 -> {
                    val h = parts[0].trim().toIntOrNull() ?: 0
                    val m = parts[1].trim().toIntOrNull() ?: 0
                    val s = parts[2].trim().toDoubleOrNull()?.toInt() ?: 0
                    (h * 3600 + m * 60 + s).coerceAtLeast(0)
                }
                else -> 0
            }
        }

        fun filterValidKeyMoments(
            moments: List<KeyMomentAnalysis>,
            durationSec: Int?
        ): List<KeyMomentAnalysis> {
            return moments.filter { m ->
                m.timestampSec >= 0 && (durationSec == null || durationSec <= 0 || m.timestampSec <= durationSec)
            }
        }
    }

    override suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/v1beta/models/$MODEL_NAME?key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Connected to $MODEL_NAME (HTTP ${response.code})")
                } else {
                    val err = response.body?.string() ?: "HTTP ${response.code}"
                    Result.failure(Exception("API Error: $err"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String
    ): GeminiReelAnalysis = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is not configured. Please enter your key in Settings." }
        require(videoFile.exists() && videoFile.length() > 0) { "Video file does not exist or is empty" }

        val fileLength = videoFile.length()
        if (fileLength < INLINE_VIDEO_LIMIT_BYTES) {
            Log.i(TAG, "Using fast inline video upload for ${videoFile.name} ($fileLength bytes) with $MODEL_NAME")
            return@withContext executeWithRetry("GenerateContentInline") {
                generateContentInline(apiKey, videoFile, prompt)
            }
        }

        var remoteFileName: String? = null
        try {
            // 1. Resumable Upload with retry
            Log.i(TAG, "Starting resumable upload for ${videoFile.name} (${videoFile.length()} bytes)")
            val fileResource = executeWithRetry("Upload") {
                uploadVideoResumable(apiKey, videoFile)
            }
            val fileUri = fileResource.fileUri
            remoteFileName = fileResource.fileName
            Log.i(TAG, "Uploaded video: name=$remoteFileName uri=$fileUri")

            // 2. Wait for ACTIVE state
            waitForFileActive(apiKey, remoteFileName)

            // 3. Generate structured content with exponential backoff retry (2s, 5s, 10s)
            Log.i(TAG, "Calling generateContent with model=$MODEL_NAME")
            val analysis = executeWithRetry("GenerateContent") {
                generateContentWithFile(apiKey, fileUri, prompt)
            }
            analysis
        } finally {
            // 4. Always clean up remote file from Google Cloud
            remoteFileName?.let { name ->
                try {
                    deleteRemoteFile(apiKey, name)
                    Log.i(TAG, "Deleted remote Gemini file: $name")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete remote file $name: ${e.message}")
                }
            }
        }
    }

    private suspend fun <T> executeWithRetry(
        tag: String = "API",
        maxAttempts: Int = 3,
        backoffDelaysMs: List<Long> = listOf(2000L, 5000L, 10000L),
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                val isTransient = isTransientError(e)
                if (!isTransient || attempt == maxAttempts) {
                    Log.e(TAG, "[$tag] Fatal or exhausted attempt $attempt/$maxAttempts (${e.message})")
                    throw e
                }
                val delayMs = backoffDelaysMs.getOrElse(attempt - 1) { 10000L }
                Log.w(TAG, "[$tag] Transient error on attempt $attempt/$maxAttempts (${e.message}). Retrying in ${delayMs}ms...")
                delay(delayMs)
            }
        }
        throw lastException ?: Exception("[$tag] Operation failed after $maxAttempts attempts")
    }

    private fun isTransientError(e: Exception): Boolean {
        if (e is java.net.SocketTimeoutException || e is java.io.IOException) return true
        if (e is GeminiApiException) {
            return e.statusCode in listOf(429, 500, 502, 503, 504)
        }
        val msg = e.message ?: ""
        return listOf("429", "500", "502", "503", "504", "timeout", "SocketTimeout", "UNAVAILABLE", "RESOURCE_EXHAUSTED").any {
            msg.contains(it, ignoreCase = true)
        }
    }

    private data class RemoteFileResource(val fileName: String, val fileUri: String)

    private fun uploadVideoResumable(apiKey: String, videoFile: File): RemoteFileResource {
        val fileLength = videoFile.length()
        val startUrl = "$BASE_URL/upload/v1beta/files?key=$apiKey"
        val metadataJson = """{"file": {"display_name": "${videoFile.nameWithoutExtension}"}}"""

        val startRequest = Request.Builder()
            .url(startUrl)
            .post(metadataJson.toRequestBody("application/json".toMediaType()))
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", fileLength.toString())
            .header("X-Goog-Upload-Header-Content-Type", "video/mp4")
            .build()

        val uploadUrl = client.newCall(startRequest).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw GeminiApiException(resp.code, "Failed to start upload session: HTTP ${resp.code} - $err")
            }
            resp.header("X-Goog-Upload-URL")
                ?: throw Exception("Missing X-Goog-Upload-URL in upload initiation response")
        }

        // Upload in 8MB chunks
        val raf = RandomAccessFile(videoFile, "r")
        var offset = 0L
        var finalResponseBody = ""

        try {
            while (offset < fileLength) {
                val remaining = fileLength - offset
                val currentChunkSize = minOf(CHUNK_SIZE.toLong(), remaining).toInt()
                val isLastChunk = (offset + currentChunkSize) >= fileLength

                val buffer = ByteArray(currentChunkSize)
                raf.seek(offset)
                raf.readFully(buffer)

                val command = if (isLastChunk) "upload, finalize" else "upload"
                val chunkRequest = Request.Builder()
                    .url(uploadUrl)
                    .post(buffer.toRequestBody("application/octet-stream".toMediaType()))
                    .header("X-Goog-Upload-Offset", offset.toString())
                    .header("X-Goog-Upload-Command", command)
                    .build()

                client.newCall(chunkRequest).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string().orEmpty()
                        throw GeminiApiException(resp.code, "Chunk upload failed at offset $offset: HTTP ${resp.code} - $err")
                    }
                    if (isLastChunk) {
                        finalResponseBody = resp.body?.string().orEmpty()
                    }
                }
                offset += currentChunkSize
            }
        } finally {
            raf.close()
        }

        val json = JsonParser.parseString(finalResponseBody).asJsonObject
        val fileObj = json.getAsJsonObject("file")
            ?: throw Exception("Invalid upload response: $finalResponseBody")

        val name = fileObj.get("name").asString
        val uri = fileObj.get("uri").asString
        return RemoteFileResource(fileName = name, fileUri = uri)
    }

    private suspend fun waitForFileActive(apiKey: String, fileName: String) {
        val checkUrl = "$BASE_URL/v1beta/$fileName?key=$apiKey"
        val backoffMs = listOf(500L, 800L, 1200L, 1500L)
        var attempts = 0
        while (attempts < 30) {
            val request = Request.Builder().url(checkUrl).get().build()
            val state = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw GeminiApiException(resp.code, "Failed checking file state: HTTP ${resp.code} - $err")
                }
                val json = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                json.get("state")?.asString ?: "UNKNOWN"
            }

            if (state == "ACTIVE") {
                Log.i(TAG, "File $fileName is ACTIVE")
                return
            }
            if (state == "FAILED") {
                throw Exception("Remote Gemini file processing failed state=$state")
            }

            delay(backoffMs.getOrElse(attempts) { 1500L })
            attempts++
        }
        throw Exception("Timeout waiting for file $fileName to become ACTIVE")
    }

    private fun generateContentInline(
        apiKey: String,
        videoFile: File,
        prompt: String
    ): GeminiReelAnalysis {
        val url = "$BASE_URL/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
        val encodedVideo = Base64.encodeToString(videoFile.readBytes(), Base64.NO_WRAP)

        val requestBodyJson = """
        {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {
                            "inline_data": {
                                "mime_type": "video/mp4",
                                "data": "$encodedVideo"
                            }
                        },
                        {
                            "text": ${gson.toJson(prompt)}
                        }
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "responseMimeType": "application/json",
                "thinkingConfig": {
                    "thinkingBudget": 0
                }
            }
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val (statusCode, responseBody) = client.newCall(request).execute().use { resp ->
            Pair(resp.code, resp.body?.string().orEmpty())
        }

        if (statusCode !in 200..299) {
            throw GeminiApiException(statusCode, "GenerateContent failed: HTTP $statusCode - $responseBody")
        }

        return parseGeminiResponse(responseBody)
    }

    private fun generateContentWithFile(
        apiKey: String,
        fileUri: String,
        prompt: String
    ): GeminiReelAnalysis {
        val url = "$BASE_URL/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        val requestBodyJson = """
        {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {
                            "file_data": {
                                "file_uri": "$fileUri",
                                "mime_type": "video/mp4"
                            }
                        },
                        {
                            "text": ${gson.toJson(prompt)}
                        }
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "responseMimeType": "application/json",
                "thinkingConfig": {
                    "thinkingBudget": 0
                }
            }
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val (statusCode, responseBody) = client.newCall(request).execute().use { resp ->
            Pair(resp.code, resp.body?.string().orEmpty())
        }

        if (statusCode !in 200..299) {
            throw GeminiApiException(statusCode, "GenerateContent failed: HTTP $statusCode - $responseBody")
        }

        return parseGeminiResponse(responseBody)
    }

    fun parseGeminiResponse(responseJson: String): GeminiReelAnalysis {
        val root = JsonParser.parseString(responseJson).asJsonObject
        val candidates = root.getAsJsonArray("candidates")
            ?: throw Exception("No candidates returned from Gemini: $responseJson")

        if (candidates.size() == 0) throw Exception("Empty candidates array from Gemini")

        val firstCandidate = candidates.get(0).asJsonObject
        val content = firstCandidate.getAsJsonObject("content")
        val parts = content.getAsJsonArray("parts")
        val text = parts.get(0).asJsonObject.get("text").asString

        // Clean any possible surrounding code block backticks
        val cleanedJson = text.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return RESILIENT_GSON.fromJson(cleanedJson, GeminiReelAnalysis::class.java)
    }

    private fun deleteRemoteFile(apiKey: String, fileName: String) {
        val url = "$BASE_URL/v1beta/$fileName?key=$apiKey"
        val request = Request.Builder().url(url).delete().build()
        client.newCall(request).execute().close()
    }
}
