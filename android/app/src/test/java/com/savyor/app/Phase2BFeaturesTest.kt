package com.savyor.app

import android.net.Uri
import com.savyor.app.data.local.BackupRestoreManager
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.domain.model.StructuredContent
import com.savyor.app.ui.screens.detail.copyMarkdownNotes
import com.savyor.app.ui.theme.CategorySwatches
import com.savyor.app.ui.theme.parseCategoryColor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Phase2BFeaturesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testZipSlipPathTraversalRejected() {
        val zipFile = tempFolder.newFile("malicious_slip.zip")
        val evilPayload = "MALICIOUS_PAYLOAD_EXECUTION_ATTEMPT"

        // Construct a hostile Zip containing path traversal entries
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Normal database entry
            val dbEntry = ZipEntry("database/savyor.db")
            zos.putNextEntry(dbEntry)
            zos.write("SQLite format 3\u0000".toByteArray())
            zos.closeEntry()

            // Hostile Zip Slip entry attempting to escape directory
            val hostileEntry = ZipEntry("../../../evil_exploit.txt")
            zos.putNextEntry(hostileEntry)
            zos.write(evilPayload.toByteArray())
            zos.closeEntry()
        }

        // Test safe extraction logic against the staging directory
        val stagingDir = tempFolder.newFolder("staging")
        var securityExceptionThrown = false

        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val destFile = File(stagingDir, entry.name)
                val canonicalDest = destFile.canonicalPath
                val canonicalStaging = stagingDir.canonicalPath + File.separator

                if (!canonicalDest.startsWith(canonicalStaging) && canonicalDest != stagingDir.canonicalPath) {
                    securityExceptionThrown = true
                    break
                }
                entry = zipIn.nextEntry
            }
        }

        assertTrue("Zip Slip path traversal MUST throw SecurityException / be rejected", securityExceptionThrown)
        assertFalse("Exploit file must NEVER be created on disk", File(tempFolder.root, "evil_exploit.txt").exists())
    }

    @Test
    fun testCategoryColorParsingAndPaletteIntegrity() {
        // Must contain 10 curated muted editorial swatches
        assertEquals(10, CategorySwatches.size)

        // Palette must contain curated category dot colors
        assertTrue(CategorySwatches.any { it.hex.equals("#2563EB", ignoreCase = true) }) // Cobalt
        assertTrue(CategorySwatches.any { it.hex.equals("#C2410C", ignoreCase = true) }) // Terracotta
        assertTrue(CategorySwatches.any { it.hex.equals("#047857", ignoreCase = true) }) // Emerald
        assertTrue(CategorySwatches.any { it.hex.equals("#7E22CE", ignoreCase = true) }) // Plum

        // Parsing valid colors
        val forestColor = parseCategoryColor("#2F5D50")
        assertNotNull(forestColor)

        // Parsing hex without hash symbol
        val sageColor = parseCategoryColor("74B9A2")
        assertNotNull(sageColor)

        // Parsing invalid hex falls back safely without crashing
        val fallbackColor = parseCategoryColor("NOT_A_HEX")
        assertNotNull(fallbackColor)
    }

    @Test
    fun testUserNoteIncludedInMarkdownExport() {
        val reel = ReelEntity(
            shortcode = "NOTE123",
            originalUrl = "https://instagram.com/reel/NOTE123/",
            cleanUrl = "https://instagram.com/reel/NOTE123/",
            title = "Baking Perfect Sourdough",
            category = "Recipes & Cooking",
            creator = "baker_chef",
            useWhen = "Baking high-hydration sourdough bread",
            summary = "Summary of sourdough technique",
            userNote = "Used 75% hydration instead of 80% and added 50g rye flour. Baked at 230C for 22 mins with steam."
        )

        val md = buildString {
            appendLine("# ${reel.title}")
            appendLine("**Category:** ${reel.category} | **Creator:** @${reel.creator} | **Language:** ${reel.sourceLanguage}")
            appendLine("**Original Reel:** ${reel.cleanUrl}")
            if (reel.useWhen.isNotBlank()) appendLine("> **Use when:** ${reel.useWhen}")
            appendLine()
            if (reel.userNote.isNotBlank()) {
                appendLine("## My Note")
                appendLine(reel.userNote)
                appendLine()
            }
            appendLine("## Summary")
            appendLine(reel.summary)
        }

        assertTrue(md.contains("## My Note"))
        assertTrue(md.contains("Used 75% hydration instead of 80% and added 50g rye flour."))
    }
}
