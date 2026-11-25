package com.savyor.app

import com.savyor.app.data.local.FtsQuerySanitizer
import com.savyor.app.data.local.SnapshotCacheManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HostileBugHuntTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFts5SanitizerHostileInputs() {
        // C++, O'Reilly, foo-bar, 50%, unclosed quote, Tamil, Hindi
        assertEquals("\"C++\"*", FtsQuerySanitizer.sanitizeForFts5("C++"))
        assertEquals("\"O'Reilly\"*", FtsQuerySanitizer.sanitizeForFts5("O'Reilly"))
        assertEquals("\"foo-bar\"*", FtsQuerySanitizer.sanitizeForFts5("foo-bar"))
        assertEquals("\"50%\"*", FtsQuerySanitizer.sanitizeForFts5("50%"))
        assertEquals("\"\"\"unclosed\"* \"quote\"*", FtsQuerySanitizer.sanitizeForFts5("\"unclosed quote"))
        assertEquals("\"கறி\"* \"தோசை\"*", FtsQuerySanitizer.sanitizeForFts5("கறி தோசை"))
        assertEquals("\"हाइड्रा\"* \"लॉन्चर\"*", FtsQuerySanitizer.sanitizeForFts5("हाइड्रा लॉन्चर"))

        // Pure symbols / blank / whitespace should return null (handled by LIKE fallback or normal list)
        assertNull(FtsQuerySanitizer.sanitizeForFts5("***"))
        assertNull(FtsQuerySanitizer.sanitizeForFts5(""))
        assertNull(FtsQuerySanitizer.sanitizeForFts5("   \t\n  "))

        // 500-char string should truncate safely without throwing
        val longQuery = "a".repeat(600)
        val sanitizedLong = FtsQuerySanitizer.sanitizeForFts5(longQuery)
        assertNotNull(sanitizedLong)
        assertTrue(sanitizedLong!!.length <= 505)
    }

    @Test
    fun testSnapshotLruEvictionPreservesCoverThumbnails() {
        val filesDir = tempFolder.newFolder("files")
        val snap1 = File(filesDir, "snapshots/reel1").apply { mkdirs() }
        val snap2 = File(filesDir, "snapshots/reel2").apply { mkdirs() }

        val thumb1 = File(snap1, "thumb.webp").apply { writeBytes(ByteArray(400)) }
        val thumb2 = File(snap2, "thumb.webp").apply { writeBytes(ByteArray(400)) }

        val oldMoment = File(snap1, "moment_0_2s.webp").apply {
            writeBytes(ByteArray(500))
            setLastModified(1_000_000L)
        }
        val newerMoment = File(snap2, "moment_0_5s.webp").apply {
            writeBytes(ByteArray(500))
            setLastModified(2_000_000L)
        }

        // Total = 1800 bytes. Cap = 1350 bytes -> should evict ONLY oldMoment (500 bytes), leaving 1300 bytes
        val evicted = SnapshotCacheManager.enforceSnapshotLruCap(filesDir, maxBytes = 1350L)

        assertEquals(1, evicted)
        assertFalse("Oldest moment should be evicted", oldMoment.exists())
        assertTrue("Newer moment should remain", newerMoment.exists())
        assertTrue("Cover thumb1 must NEVER be evicted", thumb1.exists())
        assertTrue("Cover thumb2 must NEVER be evicted", thumb2.exists())
    }

    @Test
    fun testOrphanedTempVideoCleanup() {
        val cacheDir = tempFolder.newFolder("cache")
        val tempDir = File(cacheDir, "reels_temp").apply { mkdirs() }
        val staleMp4 = File(tempDir, "crashed_reel.mp4").apply { writeBytes(ByteArray(1024)) }
        val stalePart = File(tempDir, "crashed_reel.mp4.part").apply { writeBytes(ByteArray(512)) }

        val cleaned = SnapshotCacheManager.cleanupOrphanedTempVideos(cacheDir)

        assertEquals(2, cleaned)
        assertFalse(staleMp4.exists())
        assertFalse(stalePart.exists())
    }
}
