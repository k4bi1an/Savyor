package com.savyor.app

import com.google.gson.Gson
import com.savyor.app.data.remote.GeminiFilesClient
import com.savyor.app.domain.model.GeminiReelAnalysis
import org.junit.Assert.*
import org.junit.Test

class GeminiResponseParsingTest {

    private val client = GeminiFilesClient()

    @Test
    fun testMultilingualTamilReelParsing() {
        val sampleResponse = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "{\n  \"title\": \"Authentic Madurai Kari Dosa (Spicy Mutton Crepe)\",\n  \"category\": \"Recipes & Cooking\",\n  \"tags\": [\"madurai food\", \"kari dosa\", \"mutton\", \"south indian recipe\"],\n  \"summary\": \"Direct step-by-step method to cook traditional Madurai street-style Kari Dosa layered with spiced minced mutton, whipped eggs, and fragrant shallot masala.\",\n  \"use_when\": \"Use when cooking a high-protein Sunday South Indian brunch.\",\n  \"source_language\": \"Tamil\",\n  \"transcript\": \"[0:00] First prepare the mutton chuka with freshly ground fennel and black pepper. [0:08] Spread thick dosa batter on a cast iron tawa. [0:14] Pour whisked egg over the batter, layer generous spiced mutton, and flip with ghee.\",\n  \"original_transcript\": \"[0:00] Mudhalla nalla milagu sombu sethu kari chuka senjidunga. [0:08] Kal la kettiyaana maavu oothitu. [0:14] Mela muttai oothi kothina kari pottu nalla nei oothi thiruppi podunga.\",\n  \"key_takeaways\": [\n    \"Cast iron skillet on medium-high heat gives the essential crisp base.\",\n    \"Layer egg before mutton so the meat binds securely to the crepe.\",\n    \"Use cold-pressed gingelly oil or pure ghee for authenticity.\"\n  ],\n  \"caveats\": [\n    \"Oven cooking is not recommended; requires stovetop tawa.\"\n  ],\n  \"key_moments\": [\n    {\"timestamp_sec\": 4, \"label\": \"Spiced minced mutton chukku consistency\"},\n    {\"timestamp_sec\": 12, \"label\": \"Layering egg and meat over thick dosa batter\"}\n  ],\n  \"extracted_resources\": [\n    {\"name\": \"Simmakkal Kari Dosa Masala Powder\", \"url\": \"\", \"description\": \"Special fennel-heavy spice blend\"}\n  ],\n  \"structured_content\": {\n    \"note_type\": \"recipe\",\n    \"ingredients\": [\n      {\"item\": \"Thick dosa batter\", \"quantity\": \"1 ladle\"},\n      {\"item\": \"Minced mutton keema\", \"quantity\": \"150g\"},\n      {\"item\": \"Eggs\", \"quantity\": \"2 whisked\"},\n      {\"item\": \"Ghee\", \"quantity\": \"2 tbsp\"}\n    ],\n    \"steps\": [\n      {\"step_number\": 1, \"instruction\": \"Heat seasoned cast iron tawa and pour one thick ladle of batter without spreading thin.\"},\n      {\"step_number\": 2, \"instruction\": \"Immediately pour seasoned beaten egg across the wet surface.\"},\n      {\"step_number\": 3, \"instruction\": \"Press 150g cooked mutton keema on top and drizzle ghee around the perimeter.\"}\n    ]\n  }\n}"
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val parsed: GeminiReelAnalysis = client.parseGeminiResponse(sampleResponse)

        assertNotNull(parsed)
        assertEquals("Authentic Madurai Kari Dosa (Spicy Mutton Crepe)", parsed.title)
        assertEquals("Recipes & Cooking", parsed.category)
        assertEquals("Tamil", parsed.sourceLanguage)

        // Notes and summary are in clear English
        assertTrue(parsed.summary.contains("Direct step-by-step method"))
        assertTrue(parsed.useWhen.contains("Sunday South Indian brunch"))

        // English transcript is present and timestamped
        assertTrue(parsed.transcript.startsWith("[0:00]"))
        assertTrue(parsed.transcript.contains("First prepare the mutton chuka"))

        // Original spoken Tamil transcript is present
        assertNotNull(parsed.originalTranscript)
        assertTrue(parsed.originalTranscript!!.contains("kari chuka senjidunga"))

        // Recipe specifics
        assertNotNull(parsed.structuredContent)
        assertEquals("recipe", parsed.structuredContent?.noteType)
        assertEquals(4, parsed.structuredContent?.ingredients?.size)
        assertEquals("Thick dosa batter", parsed.structuredContent?.ingredients?.first()?.item)
        assertEquals(3, parsed.structuredContent?.steps?.size)

        // Key moments
        assertEquals(2, parsed.keyMoments.size)
        assertEquals(4, parsed.keyMoments[0].timestampSec)
    }

    @Test
    fun testCodeTutorialReelParsingWithVerbatimSnippets() {
        val sampleResponse = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "```json\n{\n  \"title\": \"Spin Up a Local PostgreSQL Vector DB with pgvector in 1 Command\",\n  \"category\": \"Tech & Code\",\n  \"tags\": [\"postgres\", \"docker\", \"pgvector\", \"database\"],\n  \"summary\": \"Execute docker run with pgvector/pgvector:pg16 to launch a local PostgreSQL database with vector indexing pre-configured on port 5432.\",\n  \"use_when\": \"Use when testing RAG embeddings locally without Cloud database costs.\",\n  \"source_language\": \"English\",\n  \"transcript\": \"[0:00] Stop installing Postgres manually for vector search. Run this single docker command.\",\n  \"original_transcript\": null,\n  \"key_takeaways\": [\n    \"Image pgvector/pgvector:pg16 includes both PostgreSQL 16 and HNSW/IVFFlat vector extensions.\",\n    \"Exposes standard port 5432 with password set in POSTGRES_PASSWORD environment variable.\"\n  ],\n  \"caveats\": [\n    \"Requires Docker daemon running locally.\"\n  ],\n  \"key_moments\": [\n    {\"timestamp_sec\": 2, \"label\": \"Terminal docker run command execution\"}\n  ],\n  \"extracted_resources\": [\n    {\"name\": \"pgvector Docker Image\", \"url\": \"https://hub.docker.com/r/pgvector/pgvector\", \"description\": \"Official pre-built vector DB image\"}\n  ],\n  \"structured_content\": {\n    \"note_type\": \"code\",\n    \"commands\": [\n      \"docker run -d --name pgvector-local -e POSTGRES_PASSWORD=mysecretpassword -p 5432:5432 pgvector/pgvector:pg16\"\n    ],\n    \"code_snippets\": [\n      \"CREATE EXTENSION IF NOT EXISTS vector;\\nCREATE TABLE items (id bigserial PRIMARY KEY, embedding vector(3));\"\n    ]\n  }\n}\n```"
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val parsed = client.parseGeminiResponse(sampleResponse)

        assertNotNull(parsed)
        assertEquals("Spin Up a Local PostgreSQL Vector DB with pgvector in 1 Command", parsed.title)
        assertEquals("Tech & Code", parsed.category)
        assertNotNull(parsed.structuredContent?.commands)
        assertTrue(parsed.structuredContent!!.commands!!.first().contains("docker run"))
        assertNotNull(parsed.structuredContent?.codeSnippets)
        assertTrue(parsed.structuredContent!!.codeSnippets!!.first().contains("CREATE EXTENSION"))
    }
}
