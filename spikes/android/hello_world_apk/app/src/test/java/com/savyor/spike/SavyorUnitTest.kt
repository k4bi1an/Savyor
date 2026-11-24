package com.savyor.spike

import org.junit.Assert.*
import org.junit.Test
import java.util.regex.Pattern

class SavyorUnitTest {

    private val instagramPattern = Pattern.compile(
        "https?://(?:www\\.)?instagram\\.com/(?:reel|p)/([A-Za-z0-9_-]+)",
        Pattern.CASE_INSENSITIVE
    )

    private fun extractShortcode(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = instagramPattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }

    @Test
    fun testUrlExtractionFromComplexShareText() {
        val shareText = "Look at this amazing workout! https://www.instagram.com/reel/C8XYZ_99a/?igsh=MWQ1NDc4MmVmZQ== sent via IG"
        val shortcode = extractShortcode(shareText)
        assertEquals("C8XYZ_99a", shortcode)
    }

    @Test
    fun testNonInstagramTextRejected() {
        val nonIgText = "Hey check out https://youtube.com/shorts/abcdef"
        val shortcode = extractShortcode(nonIgText)
        assertNull(shortcode)
    }

    @Test
    fun testPostUrlTreatedAsReel() {
        val postText = "https://www.instagram.com/p/DF123abc/"
        val shortcode = extractShortcode(postText)
        assertEquals("DF123abc", shortcode)
    }

    @Test
    fun testStorageBudgetCalculation() {
        val appSizeMb = 22.0
        val dbPerReelMb = 0.015
        val thumbPerReelMb = 0.018
        val snapPerReelMb = 0.180
        val reelCount = 100

        val totalDataMb = appSizeMb + (reelCount * (dbPerReelMb + thumbPerReelMb + snapPerReelMb))
        assertTrue("Total 100-reel storage ($totalDataMb MB) must stay under 200 MB budget", totalDataMb < 200.0)
    }
}
