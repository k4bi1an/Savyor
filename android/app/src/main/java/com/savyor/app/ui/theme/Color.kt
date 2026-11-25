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
    // CrystalTier.CHROME tokens
    val chromeGradTop: Color,
    val chromeGradBottom: Color,
    val chromeDomeTop: Color,
    val chromeDomeMid: Color,
    val chromeRim0: Color,
    val chromeRim28: Color,
    val chromeRim55: Color,
    val chromeRim82: Color,
    val chromeRim100: Color,
    val chromeBottomCaustic: Color,
    // CrystalTier.PILL tokens
    val pillFill: Color,
    val pillSheenTop: Color,
    val pillRimTop: Color,
    val pillRimBottom: Color,
    // CrystalTier.THUMB_BADGE tokens
    val thumbBadgeFill: Color,
    val thumbBadgeRimTop: Color,
    val thumbBadgeRimBottom: Color,
    // CrystalTier.SHEET tokens
    val sheetFill: Color,
    val sheetRimTop: Color,
    val sheetRimBottom: Color,
    val sheetCausticGlow: Color,
    // Sculpted Card Border
    val cardRimTop: Color,
    val cardRimBottom: Color,
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
    // CHROME (high-opacity frosted obsidian so scrolled text never bleeds through)
    chromeGradTop = Color(0xF214141A),
    chromeGradBottom = Color(0xF60F0F14),
    chromeDomeTop = Color(0x24FFFFFF),
    chromeDomeMid = Color(0x08E0F2FE),
    chromeRim0 = Color(0x7AFFFFFF),
    chromeRim28 = Color(0x28BAE6FD),
    chromeRim55 = Color(0x14FFFFFF),
    chromeRim82 = Color(0x20E9D5FF),
    chromeRim100 = Color(0x38FFFFFF),
    chromeBottomCaustic = Color(0x14FFFFFF),
    // PILL
    pillFill = Color(0xB8191921),
    pillSheenTop = Color(0x1CFFFFFF),
    pillRimTop = Color(0x5CFFFFFF),
    pillRimBottom = Color(0x14FFFFFF),
    // THUMB_BADGE
    thumbBadgeFill = Color(0xCC0D0D12),
    thumbBadgeRimTop = Color(0x66FFFFFF),
    thumbBadgeRimBottom = Color(0x18FFFFFF),
    // SHEET
    sheetFill = Color(0xEB15151B),
    sheetRimTop = Color(0x55FFFFFF),
    sheetRimBottom = Color(0x14FFFFFF),
    sheetCausticGlow = Color(0x18FFFFFF),
    // Sculpted card rim
    cardRimTop = Color(0x28FFFFFF),
    cardRimBottom = Color(0xFF242429),
    // Status badges
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
    // CHROME (high-opacity frosted porcelain so scrolled text never bleeds through)
    chromeGradTop = Color(0xF5FFFFFF),
    chromeGradBottom = Color(0xF8F5F4EF),
    chromeDomeTop = Color(0x99FFFFFF),
    chromeDomeMid = Color(0x28FFFFFF),
    chromeRim0 = Color(0xFFFFFFFF),
    chromeRim28 = Color(0x88E0F2FE),
    chromeRim55 = Color(0x1C111113),
    chromeRim82 = Color(0x24111113),
    chromeRim100 = Color(0x30111113),
    chromeBottomCaustic = Color(0x18111113),
    // PILL
    pillFill = Color(0xCCFFFFFF),
    pillSheenTop = Color(0x66FFFFFF),
    pillRimTop = Color(0xFFFFFFFF),
    pillRimBottom = Color(0x22111113),
    // THUMB_BADGE
    thumbBadgeFill = Color(0xE0FFFFFF),
    thumbBadgeRimTop = Color(0xFFFFFFFF),
    thumbBadgeRimBottom = Color(0x28111113),
    // SHEET
    sheetFill = Color(0xF2FAF9F6),
    sheetRimTop = Color(0xFFFFFFFF),
    sheetRimBottom = Color(0x1F111113),
    sheetCausticGlow = Color(0x55FFFFFF),
    // Sculpted card rim
    cardRimTop = Color(0xFFFFFFFF),
    cardRimBottom = Color(0xFFE6E4DD),
    // Status badges
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

// Compatibility aliases
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
