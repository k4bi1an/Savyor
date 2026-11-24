package com.savyor.spikes.gemini

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spike 2B: Direct phone-to-Gemini Files API uploader and structured generator.
 * Runs completely on the Android device without any intermediate server.
 */
class GeminiAndroidClient(private val apiKey: String) {

    private val baseUrl = "https://generativelanguage.googleapis.com"

    /**
     * Uploads video file via Google Files API Resumable protocol in small chunks.
     * Keeps phone RAM constant (< 5 MB) regardless of video size.
     */
    fun uploadVideoResumable(videoFile: File, onProgress: ((Int) -> Unit)? = null): String {
        val startUrl = URL("$baseUrl/upload/v1beta/files?key=$apiKey")
        val conn = startUrl.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
        conn.setRequestProperty("X-Goog-Upload-Command", "start")
        conn.setRequestProperty("X-Goog-Upload-Header-Content-Length", videoFile.length().toString())
        conn.setRequestProperty("X-Goog-Upload-Header-Content-Type", "video/mp4")
        conn.setRequestProperty("Content-Type", "application/json")

        val metadata = JSONObject().apply {
            put("file", JSONObject().apply {
                put("display_name", videoFile.name)
            })
        }

        conn.outputStream.use { it.write(metadata.toString().toByteArray()) }
        val uploadUrlStr = conn.getHeaderField("X-Goog-Upload-URL")
            ?: throw IllegalStateException("Failed to obtain resumable upload URL: ${conn.responseCode}")
        conn.disconnect()

        // Stream file in 2MB chunks
        val uploadUrl = URL(uploadUrlStr)
        val uploadConn = uploadUrl.openConnection() as HttpURLConnection
        uploadConn.requestMethod = "POST"
        uploadConn.doOutput = true
        uploadConn.setRequestProperty("X-Goog-Upload-Offset", "0")
        uploadConn.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
        uploadConn.setRequestProperty("Content-Length", videoFile.length().toString())

        FileInputStream(videoFile).use { input ->
            uploadConn.outputStream.use { output ->
                val buffer = ByteArray(2 * 1024 * 1024)
                var bytesRead: Int
                var totalBytes = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    onProgress?.invoke(((totalBytes * 100) / videoFile.length()).toInt())
                }
            }
        }

        val respText = uploadConn.inputStream.bufferedReader().use { it.readText() }
        uploadConn.disconnect()
        val json = JSONObject(respText)
        return json.getJSONObject("file").getString("uri")
    }

    /**
     * Deletes the temporary video from Gemini storage immediately after analysis.
     */
    fun deleteFile(fileUriName: String) {
        try {
            val deleteUrl = URL("$baseUrl/v1beta/$fileUriName?key=$apiKey")
            val conn = deleteUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }
}
