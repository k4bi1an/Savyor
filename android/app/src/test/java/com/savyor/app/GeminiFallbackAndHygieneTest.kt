package com.savyor.app

import com.savyor.app.data.remote.GeminiFilesClient
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GeminiFallbackAndHygieneTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val validAnalysisJson = """
        {
          "title": "Clean Reel Note",
          "category": "Tech & Code",
          "tags": ["kotlin", "android"],
          "summary": "Concise summary of the reel.",
          "use_when": "Testing fallback resilience",
          "visual_memory": "Terminal screen",
          "source_language": "English",
          "transcript": "[0:00] Hello world",
          "key_takeaways": ["Takeaway 1"],
          "caveats": [],
          "key_moments": [{"timestamp_sec": 2, "label": "Demo"}],
          "extracted_resources": [],
          "structured_content": {"note_type": "general"}
        }
    """.trimIndent()

    @Test
    fun testRequestPayloadHygieneAcrossModels() {
        val client = GeminiFilesClient()

        // Primary & fallback 1: gemini-4-hydrogen & gemini-3.8-flash -> thinkingLevel="low", maxOutputTokens=65536
        listOf("gemini-4-hydrogen", "gemini-3.8-flash").forEach { model ->
            val payload = client.buildGenerateContentPayload(model, "https://files/abc", "prompt")
            assertFalse("Must not send deprecated thinkingBudget on $model", payload.contains("thinkingBudget"))
            assertFalse("Must not send minimal thinkingLevel on $model", payload.contains("minimal"))
            assertTrue("Must set thinkingLevel low on $model", payload.contains("\"thinkingLevel\":\"low\""))
            assertTrue("Must set high maxOutputTokens (65536) on $model", payload.contains("\"maxOutputTokens\":65536"))
        }

        // Fallback 2 & 3: gemini-3.5-flash-lite & gemini-3.6-flash -> omit thinkingConfig
        listOf("gemini-3.5-flash-lite", "gemini-3.6-flash").forEach { model ->
            val payload = client.buildGenerateContentPayload(model, "https://files/abc", "prompt")
            assertFalse("Must not send thinkingConfig on $model", payload.contains("thinkingConfig"))
            assertFalse("Must not send thinkingBudget on $model", payload.contains("thinkingBudget"))
            assertTrue("Must set high maxOutputTokens (65536) on $model", payload.contains("\"maxOutputTokens\":65536"))
        }
    }

    @Test
    fun testThoughtPartsAreSkippedBeforeJsonParsing() {
        val client = GeminiFilesClient()
        val escapedJson = com.google.gson.Gson().toJson(validAnalysisJson)
        val responseWithThoughts = """
            {
              "candidates": [
                {
                  "finishReason": "STOP",
                  "content": {
                    "parts": [
                      {
                        "thought": true,
                        "text": "Let me think step-by-step about this video... {not valid json"
                      },
                      {
                        "thought": false,
                        "text": $escapedJson
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = client.parseGeminiResponse(responseWithThoughts)
        assertEquals("Clean Reel Note", parsed.title)
        assertEquals("Tech & Code", parsed.category)
    }

    @Test
    fun testFallbackChainReusesSameFileUriWithoutReuploading() = runBlocking {
        val videoFile: File = tempFolder.newFile("sample.mp4").apply {
            writeBytes(ByteArray(1024) { 1 })
        }

        var uploadStartCount = 0
        var uploadChunkCount = 0
        var deleteCount = 0
        val attemptedModels = mutableListOf<String>()
        val escapedJson = com.google.gson.Gson().toJson(validAnalysisJson)

        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()

            fun jsonResponse(code: Int, body: String, extraHeaders: Map<String, String> = emptyMap()): Response {
                val builder = Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(body.toResponseBody("application/json".toMediaType()))
                extraHeaders.forEach { (k, v) -> builder.header(k, v) }
                return builder.build()
            }

            when {
                url.contains("/upload/v1beta/files") -> {
                    uploadStartCount++
                    jsonResponse(200, "{}", mapOf("X-Goog-Upload-URL" to "https://generativelanguage.googleapis.com/upload/session/123"))
                }
                url.contains("/upload/session/123") -> {
                    uploadChunkCount++
                    jsonResponse(200, """{"file": {"name": "files/video123", "uri": "https://generativelanguage.googleapis.com/v1beta/files/video123"}}""")
                }
                req.method == "GET" && url.contains("/v1beta/files/video123") -> {
                    jsonResponse(200, """{"name": "files/video123", "state": "ACTIVE"}""")
                }
                req.method == "DELETE" && url.contains("/v1beta/files/video123") -> {
                    deleteCount++
                    jsonResponse(200, "{}")
                }
                url.contains(":generateContent") -> {
                    val model = url.substringAfter("/models/").substringBefore(":generateContent")
                    attemptedModels.add(model)
                    when (model) {
                        "gemini-4-hydrogen" -> jsonResponse(429, """{"error": {"message": "Rate limit exceeded"}}""")
                        "gemini-3.8-flash" -> jsonResponse(503, """{"error": {"message": "Service unavailable"}}""")
                        "gemini-3.5-flash-lite" -> jsonResponse(200, """{"candidates": [{"finishReason": "MAX_TOKENS", "content": {"parts": [{"text": "{truncated"}]}}]}""")
                        "gemini-3.6-flash" -> jsonResponse(200, """{"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": $escapedJson}]}}]}""")
                        else -> jsonResponse(404, """{"error": {"message": "Unknown model"}}""")
                    }
                }
                else -> jsonResponse(404, "{}")
            }
        }

        val okHttp = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = GeminiFilesClient(client = okHttp)

        val result = client.analyzeReel(
            apiKey = "test-api-key",
            videoFile = videoFile,
            prompt = "Analyze reel",
            modelMode = "AUTO"
        )

        assertEquals(1, uploadStartCount)
        assertEquals(1, uploadChunkCount)
        assertEquals(1, deleteCount)
        assertEquals(
            listOf("gemini-4-hydrogen", "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.6-flash"),
            attemptedModels
        )
        assertEquals("gemini-3.6-flash", result.modelUsed)
        assertEquals("Clean Reel Note", result.title)
    }
}
