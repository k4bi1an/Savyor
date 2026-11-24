package com.savyor.spike.share

import java.util.regex.Pattern

object InstagramUrlCleaner {

    private val REEL_PATTERN = Pattern.compile(
        "https?://(?:www\\.)?instagram\\.com/(?:reel|reels|p)/([A-Za-z0-9_-]+)",
        Pattern.CASE_INSENSITIVE
    )

    data class CleanResult(
        val originalText: String,
        val cleanUrl: String?,
        val shortcode: String?,
        val isValid: Boolean,
        val rejectionReason: String? = null
    )

    fun clean(sharedText: String?): CleanResult {
        if (sharedText.isNullOrBlank()) {
            return CleanResult(
                originalText = "",
                cleanUrl = null,
                shortcode = null,
                isValid = false,
                rejectionReason = "Empty or null shared text"
            )
        }

        val matcher = REEL_PATTERN.matcher(sharedText)
        return if (matcher.find()) {
            val shortcode = matcher.group(1)
            val cleanUrl = "https://www.instagram.com/reel/$shortcode/"
            CleanResult(
                originalText = sharedText,
                cleanUrl = cleanUrl,
                shortcode = shortcode,
                isValid = true
            )
        } else {
            CleanResult(
                originalText = sharedText,
                cleanUrl = null,
                shortcode = null,
                isValid = false,
                rejectionReason = "No Instagram reel URL found in text"
            )
        }
    }
}
