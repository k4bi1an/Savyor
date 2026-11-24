package com.savyor.app

import com.savyor.app.data.local.entity.CategoryCorrectionEntity
import com.savyor.app.data.remote.GeminiPromptBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptBuilderTest {

    @Test
    fun testPromptIncludesMultimodalAndQualityRules() {
        val prompt = GeminiPromptBuilder.buildSystemPrompt(
            originalCaption = "Easy 5 minute pasta recipe with garlic butter sauce",
            creatorHandle = "chef_chef",
            availableCategories = listOf("Tech & Code", "Recipes & Cooking", "Health & Fitness", "Other"),
            recentCorrections = emptyList()
        )

        assertTrue(prompt.contains("Multimodal Fusion"))
        assertTrue(prompt.contains("Multilingual → Always Clear English Notes"))
        assertTrue(prompt.contains("Anti-Clickbait & Direct Answers"))
        assertTrue(prompt.contains("Verbatim Code, Prompts & Recipes"))
        assertTrue(prompt.contains("Zero Hallucination & Comment-Gated Links"))
        assertTrue(prompt.contains("Original Creator Caption & Notes"))
        assertTrue(prompt.contains("@chef_chef"))
        assertTrue(prompt.contains("WITHOUT starting with \"Use when\""))
        assertTrue(prompt.contains("(No speech — visual)"))
        assertTrue(prompt.contains("prerequisites"))
        assertTrue(prompt.contains("exercises"))
    }

    @Test
    fun testPromptIncludesDynamicCategories() {
        val customCategories = listOf("Workouts", "Finance & Crypto", "Photography", "Other")
        val prompt = GeminiPromptBuilder.buildSystemPrompt(
            originalCaption = null,
            creatorHandle = null,
            availableCategories = customCategories,
            recentCorrections = emptyList()
        )

        assertTrue(prompt.contains("Workouts | Finance & Crypto | Photography | Other"))
    }

    @Test
    fun testPromptIncludesCategoryCorrectionsFewShot() {
        val corrections = listOf(
            CategoryCorrectionEntity(
                reelShortcode = "abc123xyz",
                title = "10 Tailwind CSS Tricks",
                fromCategory = "Other",
                toCategory = "Tech & Code"
            ),
            CategoryCorrectionEntity(
                reelShortcode = "def456uvw",
                title = "Minimalist Living Room Makeover",
                fromCategory = "Other",
                toCategory = "Design & UI"
            )
        )

        val prompt = GeminiPromptBuilder.buildSystemPrompt(
            originalCaption = null,
            creatorHandle = null,
            availableCategories = emptyList(),
            recentCorrections = corrections
        )

        assertTrue(prompt.contains("User Category Correction History"))
        assertTrue(prompt.contains("10 Tailwind CSS Tricks"))
        assertTrue(prompt.contains("Tech & Code"))
        assertTrue(prompt.contains("Minimalist Living Room Makeover"))
        assertTrue(prompt.contains("Design & UI"))
    }
}
