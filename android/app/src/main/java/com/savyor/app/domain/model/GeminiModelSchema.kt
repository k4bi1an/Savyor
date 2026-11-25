package com.savyor.app.domain.model

import com.google.gson.annotations.SerializedName

data class KeyMomentAnalysis(
    @SerializedName("timestamp_sec")
    val timestampSec: Int,
    @SerializedName("label")
    val label: String
)

data class ExtractedResource(
    @SerializedName("name")
    val name: String,
    @SerializedName("url")
    val url: String = "",
    @SerializedName("description")
    val description: String = ""
)

data class RecipeIngredient(
    @SerializedName("item")
    val item: String,
    @SerializedName("quantity")
    val quantity: String = ""
)

data class RecipeStep(
    @SerializedName("step_number")
    val stepNumber: Int,
    @SerializedName("instruction")
    val instruction: String
)

data class StructuredContent(
    @SerializedName("note_type")
    val noteType: String = "general", // recipe, tutorial, code, workout, listicle, product_review, concept_breakdown, travel_guide, general

    // Recipes
    @SerializedName("ingredients")
    val ingredients: List<RecipeIngredient>? = null,
    @SerializedName("steps")
    val steps: List<RecipeStep>? = null,

    // Code / Tech / Tutorial
    @SerializedName("prerequisites")
    val prerequisites: List<String>? = null,
    @SerializedName("commands")
    val commands: List<String>? = null,
    @SerializedName("code_snippets")
    val codeSnippets: List<String>? = null, // Fenced markdown code blocks

    // Workouts
    @SerializedName("exercises")
    val exercises: List<String>? = null,

    // Listicles / Recommendations
    @SerializedName("items")
    val items: List<String>? = null,

    // General sections
    @SerializedName("sections")
    val sections: Map<String, String>? = null
)

data class GeminiReelAnalysis(
    @SerializedName("title")
    val title: String,

    @SerializedName("category")
    val category: String,

    @SerializedName("tags")
    val tags: List<String> = emptyList(),

    @SerializedName("summary")
    val summary: String,

    @SerializedName("use_when")
    val useWhen: String = "",

    @SerializedName("visual_memory")
    val visualMemory: String = "",

    @SerializedName("source_language")
    val sourceLanguage: String = "English",

    @SerializedName("transcript")
    val transcript: String = "", // Timestamped English transcript

    @SerializedName("original_transcript")
    val originalTranscript: String? = null, // Optional native transcript if non-English

    @SerializedName("key_takeaways")
    val keyTakeaways: List<String> = emptyList(),

    @SerializedName("caveats")
    val caveats: List<String> = emptyList(),

    @SerializedName("key_moments")
    val keyMoments: List<KeyMomentAnalysis> = emptyList(),

    @SerializedName("extracted_resources")
    val extractedResources: List<ExtractedResource> = emptyList(),

    @SerializedName("structured_content")
    val structuredContent: StructuredContent? = null
)
