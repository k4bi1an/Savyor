package com.savyor.app.data.remote

import com.savyor.app.domain.model.ExtractedResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkExtractorTest {

    @Test
    fun `bare github url normalizes to https instead of being dropped`() {
        val merged = LinkExtractor.extractAndMerge(
            aiLinks = listOf(
                ExtractedResource(
                    name = "Hydra Launcher",
                    url = "github.com/hydralauncher/hydra",
                    description = "Open-source game launcher",
                    kind = "REPO"
                )
            )
        )

        assertEquals(1, merged.size)
        assertEquals("https://github.com/hydralauncher/hydra", merged[0].url)
        assertEquals("REPO", merged[0].kind)
    }

    @Test
    fun `owner slash repo with REPO kind normalizes to full GitHub URL`() {
        val merged = LinkExtractor.extractAndMerge(
            aiLinks = listOf(
                ExtractedResource(
                    name = "yt-dlp",
                    url = "yt-dlp/yt-dlp",
                    description = "CLI video downloader",
                    kind = "REPO"
                )
            )
        )

        assertEquals(1, merged.size)
        assertEquals("https://github.com/yt-dlp/yt-dlp", merged[0].url)
        assertEquals("REPO", merged[0].kind)
    }

    @Test
    fun `git clone and bare github url in caption or transcript are rescued and deduplicated`() {
        val merged = LinkExtractor.extractAndMerge(
            aiLinks = listOf(
                ExtractedResource(
                    name = "Hydra",
                    url = "https://github.com/hydralauncher/hydra/",
                    kind = "REPO"
                )
            ),
            caption = "Check out github.com/hydralauncher/hydra?utm_source=ig_web_copy_link",
            transcript = "[0:05] Run git clone https://github.com/ollama/ollama.git to start"
        )

        assertEquals(2, merged.size)
        assertTrue(merged.any { it.url == "https://github.com/hydralauncher/hydra/" })
        assertTrue(merged.any { it.url == "https://github.com/ollama/ollama" && it.kind == "REPO" })
    }

    @Test
    fun `uncertain repo fallback generates properly URL-encoded GitHub search link`() {
        val merged = LinkExtractor.extractAndMerge(
            aiLinks = listOf(
                ExtractedResource(
                    name = "Fast PDF Converter",
                    url = "",
                    description = "Search GitHub for Fast PDF Converter",
                    kind = "SEARCH"
                )
            )
        )

        assertEquals(1, merged.size)
        assertEquals("SEARCH", merged[0].kind)
        assertEquals(
            "https://github.com/search?q=Fast+PDF+Converter&type=repositories",
            merged[0].url
        )
    }
}
