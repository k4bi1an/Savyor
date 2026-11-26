package com.savyor.app.data.remote

import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.savyor.app.domain.model.GeminiReelAnalysis
import com.savyor.app.domain.model.KeyMomentAnalysis
import com.savyor.app.domain.model.StructuredContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class GeminiApiException(val statusCode: Int, message: String) : Exception(message)
class GeminiStageException(val stage: String, message: String, cause: Throwable? = null) : Exception(message, cause)

@Keep
data class GeminiPart(
    @SerializedName("text") val text: String? = null,
    @SerializedName("thought") val thought: Boolean? = null
)

@Keep
data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>? = null
)

@Keep
data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent? = null,
    @SerializedName("finishReason") val finishReason: String? = null
)

@Keep
data class GeminiApiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>? = null
)

enum class GeminiModelOption(val modeKey: String, val displayName: String, val pingModelId: String) {
    AUTO("AUTO", "Auto (Recommended)", "gemini-4-hydrogen"),
    GEMINI_4_HYDROGEN("gemini-4-hydrogen", "Gemini 4 Hydrogen", "gemini-4-hydrogen"),
    GEMINI_3_8_FLASH("gemini-3.8-flash", "Gemini 3.8 Flash", "gemini-3.8-flash"),
    GEMINI_3_5_FLASH_LITE("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "gemini-3.5-flash-lite");

    companion object {
        fun fromModeKey(key: String?): GeminiModelOption {
            return entries.firstOrNull { it.modeKey.equals(key, ignoreCase = true) } ?: AUTO
        }
    }
}

interface IGeminiClient {
    suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String
    ): GeminiReelAnalysis

    suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String,
        modelMode: String
    ): GeminiReelAnalysis = analyzeReel(apiKey, videoFile, prompt)

    suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String,
        modelMode: String,
        onProgress: (stage: String, progressPct: Float, detail: String) -> Unit
    ): GeminiReelAnalysis = analyzeReel(apiKey, videoFile, prompt, modelMode)

    suspend fun testConnection(apiKey: String): Result<String>

    suspend fun testConnection(apiKey: String, modelMode: String): Result<String> = testConnection(apiKey)
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
        const val MODEL_NAME = "gemini-4-hydrogen"
        private const val CHUNK_SIZE = 8 * 1024 * 1024 // 8 MB required chunk granularity
        private const val MAX_OUTPUT_TOKENS = 65536

        private fun logI(msg: String) {
            runCatching { Log.i(TAG, msg) }
        }

        private fun logW(msg: String) {
            runCatching { Log.w(TAG, msg) }
        }

        private fun logE(msg: String) {
            runCatching { Log.e(TAG, msg) }
        }

        val FALLBACK_CHAIN = listOf(
            "gemini-4-hydrogen",
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash"
        )

        private val THINKING_LOW_MODELS = setOf(
            "gemini-4-hydrogen",
            "gemini-3.8-flash"
        )

        fun resolveModelChain(modelMode: String?): List<String> {
            val trimmed = modelMode?.trim().orEmpty()
            return if (trimmed.isBlank() || trimmed.equals("AUTO", ignoreCase = true)) {
                FALLBACK_CHAIN
            } else {
                listOf(trimmed)
            }
        }

        private val DEFAULT_GSON: Gson = Gson()

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
            .registerTypeAdapter(
                StructuredContent::class.java,
                JsonDeserializer { json, _, _ ->
                    runCatching {
                        if (json.isJsonObject) {
                            DEFAULT_GSON.fromJson(json, StructuredContent::class.java)
                        } else {
                            StructuredContent(noteType = "general")
                        }
                    }.getOrElse {
                        val noteType = runCatching {
                            if (json.isJsonObject) json.asJsonObject.get("note_type")?.asString else null
                        }.getOrNull() ?: "general"
                        StructuredContent(noteType = noteType)
                    }
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

    override suspend fun testConnection(apiKey: String): Result<String> = testConnection(apiKey, "AUTO")

    override suspend fun testConnection(apiKey: String, modelMode: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(Exception("API key is blank"))
        }
        val modelChain = resolveModelChain(modelMode)
        val pingClient = client.newBuilder()
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .build()

        var lastError: Exception? = null
        for ((index, modelId) in modelChain.withIndex()) {
            try {
                val url = "$BASE_URL/v1beta/models/$modelId:generateContent?key=$cleanKey"
                val payload = buildPingPayload(modelId)
                val request = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()

                pingClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val msg = if (modelMode.equals("AUTO", ignoreCase = true) && index > 0) {
                            "Connected via fallback ($modelId)"
                        } else {
                            "Connected ($modelId)"
                        }
                        return@withContext Result.success(msg)
                    } else {
                        val err = extractConciseApiError(body, response.code)
                        throw GeminiApiException(response.code, "HTTP ${response.code} ($modelId): $err")
                    }
                }
            } catch (e: Exception) {
                lastError = e
                val hasNext = index < modelChain.lastIndex
                if (hasNext && shouldFallbackToNextModel(e)) {
                    logW("testConnection $modelId failed (${e.message}); falling back to ${modelChain[index + 1]}")
                    continue
                }
                return@withContext Result.failure(e)
            }
        }
        Result.failure(lastError ?: Exception("Connection test failed"))
    }

    override suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String
    ): GeminiReelAnalysis = analyzeReel(apiKey, videoFile, prompt, "AUTO") { _, _, _ -> }

    override suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String,
        modelMode: String
    ): GeminiReelAnalysis = analyzeReel(apiKey, videoFile, prompt, modelMode) { _, _, _ -> }

    override suspend fun analyzeReel(
        apiKey: String,
        videoFile: File,
        prompt: String,
        modelMode: String,
        onProgress: (stage: String, progressPct: Float, detail: String) -> Unit
    ): GeminiReelAnalysis = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API key is not configured. Please enter your key in Settings." }
        require(videoFile.exists() && videoFile.length() > 0) { "Video file does not exist or is empty" }

        val modelChain = resolveModelChain(modelMode)
        var remoteFileName: String? = null
        val analyzeClient = client.newBuilder()
            .readTimeout(55, TimeUnit.SECONDS)
            .callTimeout(65, TimeUnit.SECONDS)
            .build()

        try {
            // 1. Upload once via Resumable Files API
            logI("Starting resumable upload for ${videoFile.name} (${videoFile.length()} bytes)")
            onProgress("UPLOADING", 0f, "Starting upload...")
            val fileResource = try {
                executeWithRetry("Upload") {
                    uploadVideoResumable(apiKey, videoFile) { pct ->
                        onProgress("UPLOADING", pct * 0.85f, "Uploading video · ${pct.toInt()}%")
                    }
                }
            } catch (e: Exception) {
                throw GeminiStageException("Upload", e.message ?: "Video upload failed", e)
            }
            val fileUri = fileResource.fileUri
            remoteFileName = fileResource.fileName
            logI("Uploaded video: name=$remoteFileName uri=$fileUri")

            // 2. Poll until ACTIVE before calling generateContent
            onProgress("UPLOADING", 90f, "Processing video on Gemini...")
            try {
                waitForFileActive(apiKey, remoteFileName)
            } catch (e: Exception) {
                throw GeminiStageException("Upload", e.message ?: "Uploaded file processing failed", e)
            }
            onProgress("UPLOADING", 100f, "Upload complete")

            // 3. Iterate model fallback chain reusing the exact same fileUri
            var lastError: Exception? = null
            for ((index, modelId) in modelChain.withIndex()) {
                try {
                    val label = GeminiModelOption.fromModeKey(modelId).let {
                        if (it == GeminiModelOption.AUTO) modelId else it.displayName
                    }
                    val detailMsg = if (index == 0) {
                        "Analyzing with $label..."
                    } else {
                        "Switching to $label..."
                    }
                    onProgress("ANALYZING", (index * 15f).coerceAtMost(45f), detailMsg)
                    logI("Attempting generateContent with model=$modelId (${index + 1}/${modelChain.size})")
                    val analysis = generateContentWithFile(analyzeClient, apiKey, modelId, fileUri, prompt)
                    return@withContext analysis.copy(modelUsed = modelId)
                } catch (e: Exception) {
                    lastError = e
                    val hasNext = index < modelChain.lastIndex
                    if (hasNext && shouldFallbackToNextModel(e)) {
                        logW("Model $modelId failed (${e.message}); falling back to ${modelChain[index + 1]}")
                        continue
                    }
                    throw GeminiStageException("AI Analysis", e.message ?: "AI analysis failed", e)
                }
            }
            throw GeminiStageException(
                "AI Analysis",
                lastError?.message ?: "All Gemini models failed",
                lastError
            )
        } finally {
            // 4. Always clean up remote file from Google Cloud
            remoteFileName?.let { name ->
                try {
                    deleteRemoteFile(apiKey, name)
                    logI("Deleted remote Gemini file: $name")
                } catch (e: Exception) {
                    logW("Failed to delete remote file $name: ${e.message}")
                }
            }
        }
    }

    internal fun shouldFallbackToNextModel(e: Exception): Boolean {
        val msg = e.message.orEmpty()
        if (msg.contains("API key not valid", ignoreCase = true) ||
            msg.contains("API_KEY_INVALID", ignoreCase = true) ||
            msg.contains("PERMISSION_DENIED", ignoreCase = true)
        ) {
            return false
        }
        if (e is InterruptedIOException || e is java.net.SocketTimeoutException || e is java.io.IOException) return true
        if (e is GeminiApiException) {
            // Never fall back on auth errors (401/403)
            if (e.statusCode in listOf(401, 403)) return false
            return e.statusCode in listOf(400, 404, 429, 500, 502, 503, 504)
        }
        return listOf(
            "MAX_TOKENS",
            "truncated",
            "timeout",
            "canceled",
            "429",
            "503",
            "500",
            "404",
            "400",
            "JsonSyntaxException",
            "MalformedJsonException",
            "End of input"
        ).any { msg.contains(it, ignoreCase = true) } || e is JsonSyntaxException
    }

    private suspend fun <T> executeWithRetry(
        tag: String = "API",
        maxAttempts: Int = 3,
        backoffDelaysMs: List<Long> = listOf(1500L, 3500L, 7000L),
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
                    logE("[$tag] Fatal or exhausted attempt $attempt/$maxAttempts (${e.message})")
                    throw e
                }
                val delayMs = backoffDelaysMs.getOrElse(attempt - 1) { 7000L }
                logW("[$tag] Transient error on attempt $attempt/$maxAttempts (${e.message}). Retrying in ${delayMs}ms...")
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

    @Keep
    private data class RemoteFileResource(val fileName: String, val fileUri: String)

    private fun uploadVideoResumable(
        apiKey: String,
        videoFile: File,
        onUploadProgress: (Float) -> Unit = {}
    ): RemoteFileResource {
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
                val err = extractConciseApiError(resp.body?.string().orEmpty(), resp.code)
                throw GeminiApiException(resp.code, "HTTP ${resp.code}: $err")
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
                        val err = extractConciseApiError(resp.body?.string().orEmpty(), resp.code)
                        throw GeminiApiException(resp.code, "Chunk upload HTTP ${resp.code}: $err")
                    }
                    if (isLastChunk) {
                        finalResponseBody = resp.body?.string().orEmpty()
                    }
                }
                offset += currentChunkSize
                val pct = ((offset.toFloat() / fileLength.coerceAtLeast(1L).toFloat()) * 100f).coerceIn(0f, 100f)
                onUploadProgress(pct)
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
                    val err = extractConciseApiError(resp.body?.string().orEmpty(), resp.code)
                    throw GeminiApiException(resp.code, "File status HTTP ${resp.code}: $err")
                }
                val json = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                json.get("state")?.asString
                    ?: json.getAsJsonObject("file")?.get("state")?.asString
                    ?: "UNKNOWN"
            }

            if (state == "ACTIVE") {
                logI("File $fileName is ACTIVE")
                return
            }
            if (state == "FAILED") {
                throw Exception("Remote Gemini file processing failed (state=FAILED)")
            }

            delay(backoffMs.getOrElse(attempts) { 1500L })
            attempts++
        }
        throw Exception("Timeout waiting for file $fileName to become ACTIVE")
    }

    internal fun buildGenerateContentPayload(
        modelId: String,
        fileUri: String,
        prompt: String
    ): String {
        val generationConfig = LinkedHashMap<String, Any>().apply {
            put("responseMimeType", "application/json")
            put("maxOutputTokens", MAX_OUTPUT_TOKENS)
            if (modelId in THINKING_LOW_MODELS) {
                put("thinkingConfig", mapOf("thinkingLevel" to "low"))
            }
        }

        val payload = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf(
                            "file_data" to mapOf(
                                "file_uri" to fileUri,
                                "mime_type" to "video/mp4"
                            )
                        ),
                        mapOf(
                            "text" to prompt
                        )
                    )
                )
            ),
            "generationConfig" to generationConfig
        )
        return gson.toJson(payload)
    }

    internal fun buildPingPayload(modelId: String): String {
        val generationConfig = LinkedHashMap<String, Any>().apply {
            put("maxOutputTokens", 256)
            if (modelId in THINKING_LOW_MODELS) {
                put("thinkingConfig", mapOf("thinkingLevel" to "low"))
            }
        }
        val payload = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to "Reply with OK"))
                )
            ),
            "generationConfig" to generationConfig
        )
        return gson.toJson(payload)
    }

    private fun generateContentWithFile(
        httpClient: OkHttpClient,
        apiKey: String,
        modelId: String,
        fileUri: String,
        prompt: String
    ): GeminiReelAnalysis {
        val url = "$BASE_URL/v1beta/models/$modelId:generateContent?key=$apiKey"
        val requestBodyJson = buildGenerateContentPayload(modelId, fileUri, prompt)

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val (statusCode, responseBody) = httpClient.newCall(request).execute().use { resp ->
            Pair(resp.code, resp.body?.string().orEmpty())
        }

        if (statusCode !in 200..299) {
            val err = extractConciseApiError(responseBody, statusCode)
            throw GeminiApiException(statusCode, "HTTP $statusCode ($modelId): $err")
        }

        return parseGeminiResponse(responseBody)
    }

    fun parseGeminiResponse(responseJson: String): GeminiReelAnalysis {
        val apiResponse = gson.fromJson(responseJson, GeminiApiResponse::class.java)
            ?: throw Exception("Empty response from Gemini")
        val candidates = apiResponse.candidates
            ?: throw Exception("No candidates returned from Gemini")

        if (candidates.isEmpty()) throw Exception("Empty candidates array from Gemini")

        val firstCandidate = candidates[0]
        val finishReason = firstCandidate.finishReason
        if (finishReason.equals("MAX_TOKENS", ignoreCase = true)) {
            throw Exception("Response truncated (finishReason=MAX_TOKENS)")
        }

        val parts = firstCandidate.content?.parts
        if (parts.isNullOrEmpty()) {
            throw Exception("Gemini returned no parts (finishReason=${finishReason ?: "UNKNOWN"})")
        }

        // Skip internal reasoning parts where thought == true
        val nonThoughtText = parts
            .filter { part -> part.thought != true && !part.text.isNullOrBlank() }
            .joinToString("") { it.text!! }
            .ifBlank {
                throw Exception("Gemini returned no non-thought text payload (finishReason=${finishReason ?: "UNKNOWN"})")
            }

        // Clean any possible surrounding code block backticks or prose
        var cleanedJson = nonThoughtText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (!cleanedJson.startsWith("{")) {
            val firstBrace = cleanedJson.indexOf('{')
            val lastBrace = cleanedJson.lastIndexOf('}')
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                cleanedJson = cleanedJson.substring(firstBrace, lastBrace + 1)
            }
        }

        val raw = RESILIENT_GSON.fromJson(cleanedJson, GeminiReelAnalysis::class.java)
            ?: throw Exception("Failed to deserialize Gemini JSON response")

        // Sanitize against Gson null injection on non-null Kotlin fields
        @Suppress("USELESS_ELVIS")
        return raw.copy(
            title = raw.title ?: "",
            category = raw.category ?: "Other",
            tags = raw.tags ?: emptyList(),
            summary = raw.summary ?: "",
            useWhen = raw.useWhen ?: "",
            visualMemory = raw.visualMemory ?: "",
            sourceLanguage = raw.sourceLanguage ?: "English",
            transcript = raw.transcript ?: "",
            keyTakeaways = raw.keyTakeaways ?: emptyList(),
            caveats = raw.caveats ?: emptyList(),
            keyMoments = raw.keyMoments ?: emptyList(),
            extractedResources = raw.extractedResources ?: emptyList(),
            modelUsed = raw.modelUsed ?: ""
        )
    }

    private fun extractConciseApiError(body: String, statusCode: Int): String {
        if (body.isBlank()) return "Request failed"
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            root.getAsJsonObject("error")?.get("message")?.asString ?: body.take(140)
        } catch (_: Exception) {
            body.replace(Regex("\\s+"), " ").trim().take(140)
        }
    }

    private fun deleteRemoteFile(apiKey: String, fileName: String) {
        val url = "$BASE_URL/v1beta/$fileName?key=$apiKey"
        val request = Request.Builder().url(url).delete().build()
        client.newCall(request).execute().close()
    }
}
