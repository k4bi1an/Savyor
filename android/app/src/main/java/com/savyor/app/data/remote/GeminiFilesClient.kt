package com.savyor.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.savyor.app.domain.model.GeminiReelAnalysis
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
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val CHUNK_SIZE = 8 * 1024 * 1024 // 8 MB required chunk granularity
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

        var remoteFileName: String? = null
        try {
            // 1. Resumable Upload
            Log.i(TAG, "Starting resumable upload for ${videoFile.name} (${videoFile.length()} bytes)")
            val fileResource = uploadVideoResumable(apiKey, videoFile)
            val fileUri = fileResource.fileUri
            remoteFileName = fileResource.fileName
            Log.i(TAG, "Uploaded video: name=$remoteFileName uri=$fileUri")

            // 2. Wait for ACTIVE state
            waitForFileActive(apiKey, remoteFileName)

            // 3. Generate structured content
            Log.i(TAG, "Calling generateContent with model=$MODEL_NAME")
            val analysis = generateContentWithFile(apiKey, fileUri, prompt)
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
                throw Exception("Failed to start upload session: HTTP ${resp.code} - ${resp.body?.string()}")
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
                        throw Exception("Chunk upload failed at offset $offset: HTTP ${resp.code} - ${resp.body?.string()}")
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
        var attempts = 0
        while (attempts < 30) {
            val request = Request.Builder().url(checkUrl).get().build()
            val state = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Failed checking file state: HTTP ${resp.code}")
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

            attempts++
            delay(1500)
        }
        throw Exception("Timeout waiting for file $fileName to become ACTIVE")
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
                "responseMimeType": "application/json"
            }
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("GenerateContent failed: HTTP ${resp.code} - ${resp.body?.string()}")
            }
            resp.body?.string().orEmpty()
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

        return gson.fromJson(cleanedJson, GeminiReelAnalysis::class.java)
    }

    private fun deleteRemoteFile(apiKey: String, fileName: String) {
        val url = "$BASE_URL/v1beta/$fileName?key=$apiKey"
        val request = Request.Builder().url(url).delete().build()
        client.newCall(request).execute().close()
    }
}
