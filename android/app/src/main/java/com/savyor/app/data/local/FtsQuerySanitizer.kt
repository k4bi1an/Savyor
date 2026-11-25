package com.savyor.app.data.local

object FtsQuerySanitizer {

    /**
     * Sanitizes raw user search input into a safe FTS5 MATCH query.
     * Wraps each token in double quotes with internal quotes escaped,
     * followed by * for prefix matching (e.g. `"C++"*` or `"O'Reilly"*`).
     * Returns null if there are no searchable alphanumeric/Unicode tokens.
     */
    fun sanitizeForFts5(rawQuery: String): String? {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return null

        val tokens = trimmed
            .take(500)
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(20)

        val validTokens = tokens.filter { token ->
            token.any { it.isLetterOrDigit() }
        }

        if (validTokens.isEmpty()) return null

        return validTokens.joinToString(" ") { token ->
            val escaped = token.replace("\"", "\"\"")
            "\"$escaped\"*"
        }
    }
}
