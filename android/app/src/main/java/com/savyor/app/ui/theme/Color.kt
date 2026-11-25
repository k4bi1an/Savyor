package com.savyor.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class SavyorThemeMode(val label: String) {
    SYSTEM("System"),
    OBSIDIAN("Obsidian"),
    PORCELAIN("Porcelain");

    companion object {
        fun fromString(raw: String): SavyorThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

@Immutable
data class SavyorColors(
    val isDark: Boolean,
    val bgCanvas: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceRecessed: Color,
    val surfacePressed: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val borderSubtle: Color,
    val borderFocus: Color,
    val ctaBg: Color,
    val ctaFg: Color,
    // Liquid Glass tokens
    val glassSurface: Color,
    val glassSurfaceElevated: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    // Status badge tokens
    val unreadFg: Color,
    val unreadBg: Color,
    val reviewedFg: Color,
    val reviewedBg: Color,
    val triedFg: Color,
    val triedBg: Color,
    val errorFg: Color,
    val errorBg: Color,
    // Caveat callout tokens
    val caveatBg: Color,
    val caveatBorder: Color,
    val caveatText: Color,
    // Starred highlight
    val starAccent: Color
)

val ObsidianSavyorColors = SavyorColors(
    isDark = true,
    bgCanvas = Color(0xFF09090B),
    surfaceCard = Color(0xFF121215),
    surfaceElevated = Color(0xFF1A1A1F),
    surfaceRecessed = Color(0xFF0E0E11),
    surfacePressed = Color(0xFF232329),
    inkPrimary = Color(0xFFF4F4F5),
    inkSecondary = Color(0xFFA8A8B3),
    inkMuted = Color(0xFF878794),
    borderSubtle = Color(0xFF242429),
    borderFocus = Color(0xFF71717A),
    ctaBg = Color(0xFFF4F4F5),
    ctaFg = Color(0xFF09090B),
    glassSurface = Color(0xCC141418),
    glassSurfaceElevated = Color(0xD91C1C22),
    glassBorderTop = Color(0x38FFFFFF),
    glassBorderBottom = Color(0x12FFFFFF),
    unreadFg = Color(0xFF60A5FA),
    unreadBg = Color(0xFF1D2735),
    reviewedFg = Color(0xFFFBBF24),
    reviewedBg = Color(0xFF332A17),
    triedFg = Color(0xFF34D399),
    triedBg = Color(0xFF172D27),
    errorFg = Color(0xFFF87171),
    errorBg = Color(0xFF321F22),
    caveatBg = Color(0xFF201A12),
    caveatBorder = Color(0xFF3A2E1C),
    caveatText = Color(0xFFFBBF24),
    starAccent = Color(0xFFFBBF24)
)

val PorcelainSavyorColors = SavyorColors(
    isDark = false,
    bgCanvas = Color(0xFFF8F7F4),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceRecessed = Color(0xFFEFEEEA),
    surfacePressed = Color(0xFFE5E4DE),
    inkPrimary = Color(0xFF111113),
    inkSecondary = Color(0xFF4E4E57),
    inkMuted = Color(0xFF666670),
    borderSubtle = Color(0xFFE6E4DD),
    borderFocus = Color(0xFF71717A),
    ctaBg = Color(0xFF111113),
    ctaFg = Color(0xFFFAF9F6),
    glassSurface = Color(0xCCFFFFFF),
    glassSurfaceElevated = Color(0xE0F8F7F4),
    glassBorderTop = Color(0xFFFFFFFF),
    glassBorderBottom = Color(0x1F111113),
    unreadFg = Color(0xFF1D4ED8),
    unreadBg = Color(0xFFE8EDFB),
    reviewedFg = Color(0xFF92400E),
    reviewedBg = Color(0xFFFAEFE1),
    triedFg = Color(0xFF047857),
    triedBg = Color(0xFFE6F2EE),
    errorFg = Color(0xFFB91C1C),
    errorBg = Color(0xFFF8E8E8),
    caveatBg = Color(0xFFFAF4EA),
    caveatBorder = Color(0xFFEAD8B8),
    caveatText = Color(0xFF92400E),
    starAccent = Color(0xFFD97706)
)

val LocalSavyorColors = staticCompositionLocalOf { PorcelainSavyorColors }

// Compatibility aliases for existing references
val SavyorLightAccent = Color(0xFF111113)
val SavyorDarkAccent = Color(0xFFF4F4F5)

// --- CURATED CATEGORY SWATCHES & THEME-AWARE DOT COLORS ---
data class CategoryColorSwatch(
    val hex: String,
    val name: String,
    val color: Color
)

val CategorySwatches = listOf(
    CategoryColorSwatch("#2563EB", "Cobalt", Color(0xFF2563EB)),
    CategoryColorSwatch("#C2410C", "Terracotta", Color(0xFFC2410C)),
    CategoryColorSwatch("#047857", "Emerald", Color(0xFF047857)),
    CategoryColorSwatch("#7E22CE", "Plum", Color(0xFF7E22CE)),
    CategoryColorSwatch("#BE185D", "Rose", Color(0xFFBE185D)),
    CategoryColorSwatch("#854D0E", "Espresso", Color(0xFF854D0E)),
    CategoryColorSwatch("#0369A1", "Cerulean", Color(0xFF0369A1)),
    CategoryColorSwatch("#52525B", "Graphite", Color(0xFF52525B)),
    CategoryColorSwatch("#2F5D50", "Forest", Color(0xFF2F5D50)),
    CategoryColorSwatch("#9E5A63", "Dusty Rose", Color(0xFF9E5A63))
)

fun categoryDotColor(categoryName: String, colorHex: String, isDark: Boolean): Color {
    return when (categoryName.lowercase()) {
        "tech & code" -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
        "recipes & cooking" -> if (isDark) Color(0xFFFB923C) else Color(0xFFC2410C)
        "health & fitness" -> if (isDark) Color(0xFF34D399) else Color(0xFF047857)
        "design & ui" -> if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
        "motion design" -> if (isDark) Color(0xFFF472B6) else Color(0xFFBE185D)
        "coffee & brews" -> if (isDark) Color(0xFFD6A77A) else Color(0xFF854D0E)
        "productivity" -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
        "other" -> if (isDark) Color(0xFF9CA3AF) else Color(0xFF52525B)
        else -> parseCategoryColor(colorHex, if (isDark) Color(0xFF9CA3AF) else Color(0xFF52525B))
    }
}

fun parseCategoryColor(hex: String, fallback: Color = SavyorLightAccent): Color {
    return try {
        val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(cleanHex))
    } catch (e: Exception) {
        fallback
    }
}
