package com.savyor.app.data.remote

import com.savyor.app.domain.model.ExtractedResource
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Normalizes Gemini-extracted links and deterministically rescues URLs / repositories
 * from raw captions, summaries, takeaways, and transcripts.
 */
object LinkExtractor {

    private val FULL_URL_REGEX = Regex("""https?://[^\s)\]>,"'`]+""", RegexOption.IGNORE_CASE)
    private val BARE_REPO_HOST_REGEX = Regex(
        """(?<![/@\w])((?:github\.com|gitlab\.com|huggingface\.co)/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:/[^\s)\]>,"'`]*)?)""",
        RegexOption.IGNORE_CASE
    )
    private val GIT_CLONE_REGEX = Regex(
        """git\s+clone\s+([^\s;&|"'`]+)""",
        RegexOption.IGNORE_CASE
    )
    private val OWNER_REPO_REGEX = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")
    private val BARE_DOMAIN_PATH_REGEX = Regex(
        """^(?:www\.|(?:[a-z0-9-]+\.)+(?:com|org|dev|io|net|ai|app|site|co|edu|gov))(?:/[^\s]*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_PUNCTUATION = charArrayOf('.', ',', ')', ';', ']', '!', '?')

    fun normalizeUrl(rawUrl: String, kind: String? = null): String? {
        var trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null

        while (trimmed.isNotEmpty() && trimmed.last() in TRAILING_PUNCTUATION) {
            trimmed = trimmed.dropLast(1).trim()
        }
        if (trimmed.isEmpty()) return null

        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") || lower.startsWith("file://") || lower.startsWith("data:")) {
            return null
        }

        val normalized = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
            kind.equals("REPO", ignoreCase = true) && OWNER_REPO_REGEX.matches(trimmed) ->
                "https://github.com/$trimmed"
            lower.startsWith("github.com/") ||
                lower.startsWith("gitlab.com/") ||
                lower.startsWith("huggingface.co/") ||
                lower.startsWith("www.") ||
                BARE_DOMAIN_PATH_REGEX.matches(trimmed) -> "https://$trimmed"
            else -> return null
        }

        return try {
            val uri = URI(normalized)
            val host = uri.host
            if (host.isNullOrBlank() || !host.contains('.')) null else normalized
        } catch (_: Exception) {
            null
        }
    }

    fun buildGitHubSearchUrl(repoQuery: String): String {
        val encoded = URLEncoder.encode(repoQuery.trim(), StandardCharsets.UTF_8.name())
        return "https://github.com/search?q=$encoded&type=repositories"
    }

    fun extractAndMerge(
        aiLinks: List<ExtractedResource>,
        caption: String = "",
        summary: String = "",
        takeaways: List<String> = emptyList(),
        transcript: String = ""
    ): List<ExtractedResource> {
        val merged = mutableListOf<ExtractedResource>()
        val seenKeys = mutableSetOf<String>()

        fun addIfUnique(name: String, url: String, description: String, kind: String) {
            val key = deduplicationKey(url) ?: return
            if (seenKeys.add(key)) {
                val cleanKind = normalizeKind(kind, url)
                val cleanName = name.trim().ifBlank { deriveTitleFromUrl(url) }
                merged.add(
                    ExtractedResource(
                        name = cleanName,
                        url = url,
                        description = description.trim(),
                        kind = cleanKind
                    )
                )
            }
        }

        // 1. Normalize AI-returned links
        aiLinks.forEach { res ->
            val rawUrl = res.url.trim()
            if (rawUrl.isNotEmpty()) {
                val normalized = normalizeUrl(rawUrl, res.kind)
                if (normalized != null) {
                    addIfUnique(
                        name = res.name,
                        url = normalized,
                        description = res.description,
                        kind = res.kind
                    )
                }
            } else if (res.kind.equals("SEARCH", ignoreCase = true) && res.name.isNotBlank()) {
                val searchUrl = buildGitHubSearchUrl(res.name)
                addIfUnique(
                    name = "Search GitHub: ${res.name.trim()}",
                    url = searchUrl,
                    description = res.description,
                    kind = "SEARCH"
                )
            } else if (res.name.isNotBlank()) {
                // Keep unlinked named resources if they have no URL (e.g., comment-gated)
                val fallbackKey = "name:${res.name.trim().lowercase()}"
                if (seenKeys.add(fallbackKey)) {
                    merged.add(
                        res.copy(
                            name = res.name.trim(),
                            url = "",
                            kind = normalizeKind(res.kind, "")
                        )
                    )
                }
            }
        }

        // 2. Deterministic regex rescue from caption + summary + takeaways + transcript
        val corpus = buildString {
            if (caption.isNotBlank()) appendLine(caption)
            if (summary.isNotBlank()) appendLine(summary)
            takeaways.forEach { appendLine(it) }
            if (transcript.isNotBlank()) appendLine(transcript)
        }

        GIT_CLONE_REGEX.findAll(corpus).forEach { match ->
            val candidate = match.groupValues.getOrNull(1).orEmpty()
            val normalized = normalizeUrl(candidate, "REPO")?.removeSuffix(".git")
            if (normalized != null) {
                val kind = if (isRepoUrl(normalized)) "REPO" else "DIRECT"
                addIfUnique(
                    name = deriveTitleFromUrl(normalized),
                    url = normalized,
                    description = "Rescued from git clone command",
                    kind = kind
                )
            }
        }

        FULL_URL_REGEX.findAll(corpus).forEach { match ->
            val normalized = normalizeUrl(match.value)?.removeSuffix(".git")
            if (normalized != null && !isInstagramReelSelfLink(normalized)) {
                val kind = if (isRepoUrl(normalized)) "REPO" else "DIRECT"
                addIfUnique(
                    name = deriveTitleFromUrl(normalized),
                    url = normalized,
                    description = "Extracted from reel text",
                    kind = kind
                )
            }
        }

        BARE_REPO_HOST_REGEX.findAll(corpus).forEach { match ->
            val candidate = match.groupValues.getOrNull(1).orEmpty()
            val normalized = normalizeUrl(candidate, "REPO")?.removeSuffix(".git")
            if (normalized != null) {
                addIfUnique(
                    name = deriveTitleFromUrl(normalized),
                    url = normalized,
                    description = "Extracted repository reference",
                    kind = "REPO"
                )
            }
        }

        return merged
    }

    internal fun deduplicationKey(url: String): String? {
        return try {
            val uri = URI(url.trim())
            val host = (uri.host ?: return null).lowercase().removePrefix("www.")
            val path = (uri.path ?: "").trimEnd('/').lowercase()
            val query = uri.rawQuery
                ?.split("&")
                ?.filterNot { param ->
                    val k = param.substringBefore("=").lowercase()
                    k.startsWith("utm_") || k == "igsh" || k == "igshid" || k == "fbclid"
                }
                ?.sorted()
                ?.joinToString("&")
                .orEmpty()
            if (query.isNotEmpty()) "$host$path?$query" else "$host$path"
        } catch (_: Exception) {
            url.trim().trimEnd('/').lowercase()
        }
    }

    private fun normalizeKind(kind: String, url: String): String {
        val upper = kind.trim().uppercase()
        if (upper in setOf("REPO", "DIRECT", "OFFICIAL", "SEARCH")) return upper
        return when {
            url.contains("github.com/search", ignoreCase = true) -> "SEARCH"
            isRepoUrl(url) -> "REPO"
            url.isNotBlank() -> "DIRECT"
            else -> "DIRECT"
        }
    }

    private fun isRepoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("github.com/") || lower.contains("gitlab.com/") || lower.contains("huggingface.co/")) &&
            !lower.contains("/search?")
    }

    private fun isInstagramReelSelfLink(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("instagram.com/reel/") || lower.contains("instagram.com/p/")
    }

    private fun deriveTitleFromUrl(url: String): String {
        return try {
            val uri = URI(url)
            val host = (uri.host ?: url).removePrefix("www.")
            val segments = (uri.path ?: "").trim('/').split('/').filter { it.isNotBlank() }
            if (segments.size >= 2 && (host.contains("github") || host.contains("gitlab") || host.contains("huggingface"))) {
                "${segments[0]}/${segments[1]}"
            } else if (segments.isNotEmpty()) {
                "$host/${segments.first()}"
            } else {
                host
            }
        } catch (_: Exception) {
            url
        }
    }
}
