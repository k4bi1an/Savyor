package com.savyor.spikes.ytdlp

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Spike 2A: Demonstrates yt-dlp execution, error categorisation,
 * and safe runtime update with rollback on Android.
 */
class YtDlpAndroidSpike(private val context: Context) {

    enum class ErrorCategory {
        LOGIN_WALL,
        RATE_LIMITED,
        PRIVATE_OR_UNAVAILABLE,
        NETWORK_TIMEOUT,
        PARSER_ERROR,
        UNKNOWN
    }

    data class FetchResult(
        val success: Boolean,
        val videoPath: File?,
        val thumbPath: File?,
        val timeMs: Long,
        val errorCategory: ErrorCategory? = null,
        val errorMessage: String? = null
    )

    fun categorizeError(stderr: String): ErrorCategory {
        val lower = stderr.lowercase()
        return when {
            "login" in lower || "checkpoint" in lower || "sign in" in lower -> ErrorCategory.LOGIN_WALL
            "rate" in lower || "429" in lower || "too many requests" in lower -> ErrorCategory.RATE_LIMITED
            "private" in lower || "unavailable" in lower || "deleted" in lower || "not found" in lower -> ErrorCategory.PRIVATE_OR_UNAVAILABLE
            "timeout" in lower || "timed out" in lower || "connection" in lower || "unreachable" in lower -> ErrorCategory.NETWORK_TIMEOUT
            "format" in lower || "extractor" in lower || "json" in lower -> ErrorCategory.PARSER_ERROR
            else -> ErrorCategory.UNKNOWN
        }
    }

    /**
     * Demonstrates update verification: checks sha256 before applying update.
     */
    fun verifyUpdateHash(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.forEachBlock { buffer, bytesRead ->
            md.update(buffer, 0, bytesRead)
        }
        val digest = md.digest().joinToString("") { "%02x".format(it) }
        return digest.equals(expectedSha256.trim(), ignoreCase = true)
    }
}
