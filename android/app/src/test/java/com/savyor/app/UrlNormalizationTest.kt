package com.savyor.app

import com.savyor.app.work.WorkManagerScheduler
import org.junit.Assert.*
import org.junit.Test

class UrlNormalizationTest {

    @Test
    fun testShortcodeExtractionFromStandardUrl() {
        val url = "https://www.instagram.com/reel/DdKXX_mTXLx/?fqnt=NTc4MTIwNjQ2YQ=="
        val shortcode = WorkManagerScheduler.extractShortcode(url)
        assertEquals("DdKXX_mTXLx", shortcode)
        assertEquals("https://www.instagram.com/reel/DdKXX_mTXLx/", WorkManagerScheduler.normalizeUrl(shortcode!!))
    }

    @Test
    fun testShortcodeExtractionFromShareSheetText() {
        val shareText = "Look at this amazing recipe! https://www.instagram.com/reel/DgIMvyTCMzi/?igsh=MWx1eTNrbmR0&utm_source=qr check it out"
        val shortcode = WorkManagerScheduler.extractShortcode(shareText)
        assertEquals("DgIMvyTCMzi", shortcode)
        assertEquals("https://www.instagram.com/reel/DgIMvyTCMzi/", WorkManagerScheduler.normalizeUrl(shortcode!!))
    }

    @Test
    fun testShortcodeExtractionFromPostUrl() {
        val postUrl = "https://instagram.com/p/C3x9L0PqR_1/?utm_medium=copy_link"
        val shortcode = WorkManagerScheduler.extractShortcode(postUrl)
        assertEquals("C3x9L0PqR_1", shortcode)
        assertEquals("https://www.instagram.com/reel/C3x9L0PqR_1/", WorkManagerScheduler.normalizeUrl(shortcode!!))
    }

    @Test
    fun testRejectNonInstagramText() {
        val nonIgText1 = "https://youtube.com/shorts/dQw4w9WgXcQ"
        val nonIgText2 = "Just some random text without any links"
        val nonIgText3 = "https://twitter.com/user/status/123456789"

        assertNull(WorkManagerScheduler.extractShortcode(nonIgText1))
        assertNull(WorkManagerScheduler.extractShortcode(nonIgText2))
        assertNull(WorkManagerScheduler.extractShortcode(nonIgText3))
    }
}
