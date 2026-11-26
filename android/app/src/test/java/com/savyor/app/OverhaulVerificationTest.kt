package com.savyor.app

import com.savyor.app.data.remote.GeminiFilesClient
import com.savyor.app.data.remote.LinkExtractor
import com.savyor.app.domain.model.KeyMomentAnalysis
import com.savyor.app.ui.screens.library.DateFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class OverhaulVerificationTest {

    @Test
    fun `timestamp and json parser handles MM SS, int, float, bounds, and preserves links`() {
        val sampleJson = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "{\"title\":\"LibreOffice vs OnlyOffice Setup\",\"category\":\"Tech & Code\",\"summary\":\"Download LibreOffice or OnlyOffice cleanly.\",\"key_moments\":[{\"timestamp_sec\":\"0:14\",\"label\":\"LibreOffice download page\"},{\"timestamp_sec\":22,\"label\":\"OnlyOffice desktop installer\"},{\"timestamp_sec\":28.5,\"label\":\"Comparison matrix\"},{\"timestamp_sec\":95,\"label\":\"Out of bounds moment\"}],\"links\":[{\"title\":\"LibreOffice Download\",\"url\":\"https://www.libreoffice.org/download/download-libreoffice/\",\"description\":\"Official LibreOffice download\",\"kind\":\"OFFICIAL\"},{\"title\":\"OnlyOffice\",\"url\":\"onlyoffice.com/download-desktop.aspx\",\"description\":\"Official OnlyOffice desktop\",\"kind\":\"OFFICIAL\"}]}"
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val client = GeminiFilesClient()
        val parsed = client.parseGeminiResponse(sampleJson)

        assertEquals(4, parsed.keyMoments.size)
        assertEquals(14, parsed.keyMoments[0].timestampSec)
        assertEquals(22, parsed.keyMoments[1].timestampSec)
        assertEquals(28, parsed.keyMoments[2].timestampSec)
        assertEquals(95, parsed.keyMoments[3].timestampSec)

        // Filter out-of-bounds timestamps (< 0 or > duration 45s)
        val bounded = GeminiFilesClient.filterValidKeyMoments(
            moments = parsed.keyMoments + KeyMomentAnalysis(timestampSec = -2, label = "Negative"),
            durationSec = 45
        )
        assertEquals(3, bounded.size)
        assertTrue(bounded.all { it.timestampSec in 0..45 })

        // Preserves direct & official tool download links
        val mergedLinks = LinkExtractor.extractAndMerge(parsed.extractedResources)
        assertEquals(2, mergedLinks.size)
        assertEquals("https://www.libreoffice.org/download/download-libreoffice/", mergedLinks[0].url)
        assertEquals("https://onlyoffice.com/download-desktop.aspx", mergedLinks[1].url)
    }

    @Test
    fun `date filter matches Today, Last 7 Days, SingleDay, and Range accurately`() {
        val now = System.currentTimeMillis()
        val threeDaysAgo = now - 3L * 24L * 3600L * 1000L
        val tenDaysAgo = now - 10L * 24L * 3600L * 1000L

        assertTrue(DateFilter.Today.matches(now))
        assertFalse(DateFilter.Today.matches(threeDaysAgo))

        assertTrue(DateFilter.Last7Days.matches(now))
        assertTrue(DateFilter.Last7Days.matches(threeDaysAgo))
        assertFalse(DateFilter.Last7Days.matches(tenDaysAgo))

        // Single calendar day
        val targetDate = LocalDate.of(2026, 11, 20)
        val utcPickerMillis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val localNoonMillis = targetDate.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nextDayNoonMillis = targetDate.plusDays(1).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val singleDayFilter = DateFilter.SingleDay(utcPickerMillis)
        assertTrue(singleDayFilter.matches(localNoonMillis))
        assertFalse(singleDayFilter.matches(nextDayNoonMillis))

        // Custom date range
        val startUtc = LocalDate.of(2026, 11, 18).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endUtc = LocalDate.of(2026, 11, 22).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val rangeFilter = DateFilter.Range(startUtc, endUtc)
        assertTrue(rangeFilter.matches(localNoonMillis))
        assertTrue(rangeFilter.matches(nextDayNoonMillis))
        assertFalse(rangeFilter.matches(LocalDate.of(2026, 11, 25).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
    }
}
