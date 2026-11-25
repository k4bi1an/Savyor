package com.savyor.app

import com.google.gson.Gson
import com.savyor.app.data.local.entity.ReelEntity
import com.savyor.app.data.remote.GeminiPromptBuilder
import com.savyor.app.domain.model.ExtractedResource
import com.savyor.app.domain.model.GeminiReelAnalysis
import com.savyor.app.domain.model.StructuredContent
import com.savyor.app.ui.screens.detail.buildFormattedNoteText
import com.savyor.app.ui.screens.library.SortOrder
import com.savyor.app.ui.screens.library.ViewMode
import org.junit.Assert.*
import org.junit.Test

class PreReleaseFeaturesTest {

    @Test
    fun testBuildFormattedNoteTextContainsAllSections() {
        val reel = ReelEntity(
            shortcode = "SHARE1",
            originalUrl = "https://www.instagram.com/reel/SHARE1/",
            cleanUrl = "https://www.instagram.com/reel/SHARE1/",
            title = "60fps Motion Curves",
            category = "Design & UI",
            creator = "motion_pro",
            sourceLanguage = "English",
            useWhen = "Smoothing After Effects transitions",
            summary = "Use 80% outgoing influence in the speed graph.",
            userNote = "Works best with 180-degree shutter angle."
        )
        val takeaways = listOf("Avoid default Easy-Ease", "Enable motion blur")
        val resources = listOf(
            ExtractedResource("Motion Docs", "https://example.com/motion", "Reference guide")
        )
        val structured = StructuredContent(noteType = "tutorial", items = listOf("Set 80% speed"))

        val formatted = buildFormattedNoteText(
            reel = reel,
            takeaways = takeaways,
            structured = structured,
            resources = resources,
            includeTranscript = false
        )

        assertTrue(formatted.contains("# 60fps Motion Curves"))
        assertTrue(formatted.contains("> **Use when:** Smoothing After Effects transitions"))
        assertTrue(formatted.contains("## My Note"))
        assertTrue(formatted.contains("Works best with 180-degree shutter angle."))
        assertTrue(formatted.contains("## Summary"))
        assertTrue(formatted.contains("## Key Takeaways"))
        assertTrue(formatted.contains("- Avoid default Easy-Ease"))
        assertTrue(formatted.contains("## Links & Resources"))
        assertTrue(formatted.contains("https://example.com/motion"))
        assertTrue(formatted.contains("https://www.instagram.com/reel/SHARE1/"))
    }

    @Test
    fun testViewMode3WayCycleAndSortOrderParsing() {
        assertEquals(ViewMode.LIST, ViewMode.GRID.next())
        assertEquals(ViewMode.COMPACT, ViewMode.LIST.next())
        assertEquals(ViewMode.GRID, ViewMode.COMPACT.next())

        assertEquals(ViewMode.COMPACT, ViewMode.fromString("COMPACT"))
        assertEquals(ViewMode.GRID, ViewMode.fromString("INVALID_MODE"))

        assertEquals(SortOrder.OLDEST, SortOrder.fromString("OLDEST"))
        assertEquals(SortOrder.TITLE_AZ, SortOrder.fromString("TITLE_AZ"))
        assertEquals(SortOrder.NEWEST, SortOrder.fromString("INVALID_SORT"))
    }

    @Test
    fun testGeminiPromptAndSchemaIncludeVisualMemory() {
        val prompt = GeminiPromptBuilder.buildSystemPrompt(
            originalCaption = "Test caption",
            creatorHandle = "tester",
            availableCategories = listOf("Tech & Code", "Other")
        )
        assertTrue(prompt.contains("visual_memory"))
        assertTrue(prompt.contains("clothing/colors"))

        val sampleJson = """
            {
              "title": "Local RAG Setup",
              "category": "Tech & Code",
              "tags": ["rag"],
              "summary": "Run local embeddings.",
              "use_when": "Testing local vector search",
              "visual_memory": "Man in navy hoodie sitting in a bedroom with One Piece posters on the wall, showing a white canvas animation",
              "source_language": "English",
              "transcript": "[0:00] Hello",
              "key_takeaways": ["Fast setup"],
              "caveats": [],
              "key_moments": [],
              "extracted_resources": [],
              "structured_content": {"note_type": "tutorial"}
            }
        """.trimIndent()

        val parsed = Gson().fromJson(sampleJson, GeminiReelAnalysis::class.java)
        assertEquals(
            "Man in navy hoodie sitting in a bedroom with One Piece posters on the wall, showing a white canvas animation",
            parsed.visualMemory
        )
    }
}
