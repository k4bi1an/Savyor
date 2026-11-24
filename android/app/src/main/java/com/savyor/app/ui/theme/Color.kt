package com.savyor.app.ui.theme

import androidx.compose.ui.graphics.Color

// --- LIGHT PALETTE ---
val SavyorLightBackground = Color(0xFFFAF9F6)
val SavyorLightCard = Color(0xFFFFFFFF)
val SavyorLightSubtle = Color(0xFFF2F0EB)
val SavyorLightBorder = Color(0xFFE6E2DA)
val SavyorLightTextPrimary = Color(0xFF1C1E1D)
val SavyorLightTextSecondary = Color(0xFF666A67)
val SavyorLightAccent = Color(0xFF2F5D50)

// --- DARK PALETTE ---
val SavyorDarkBackground = Color(0xFF111312)
val SavyorDarkCard = Color(0xFF191C1B)
val SavyorDarkSubtle = Color(0xFF222625)
val SavyorDarkBorder = Color(0xFF2C312F)
val SavyorDarkTextPrimary = Color(0xFFF2F1EC)
val SavyorDarkTextSecondary = Color(0xFF9BA19E)
val SavyorDarkAccent = Color(0xFF74B9A2) // Lifted sage for dark text/icons/active indicators
val SavyorDarkButtonAccent = Color(0xFF2F5D50) // Deep forest fill for prominent buttons

// --- STATUS BADGES (Soft, muted, harmonious tones) ---
object StatusBadgeColors {
    // Unread (Sage tint)
    val UnreadBgLight = Color(0xFFE2ECE8)
    val UnreadTextLight = Color(0xFF2F5D50)
    val UnreadBgDark = Color(0xFF1E2824)
    val UnreadTextDark = Color(0xFF74B9A2)

    // Reviewed (Warm Stone)
    val ReviewedBgLight = Color(0xFFEDEAE3)
    val ReviewedTextLight = Color(0xFF6E6758)
    val ReviewedBgDark = Color(0xFF282622)
    val ReviewedTextDark = Color(0xFFC8C2B4)

    // Tried (Muted Olive / Amber)
    val TriedBgLight = Color(0xFFF2ECE1)
    val TriedTextLight = Color(0xFF80632E)
    val TriedBgDark = Color(0xFF2B261D)
    val TriedTextDark = Color(0xFFD4B87E)

    // Archived (Muted Slate)
    val ArchivedBgLight = Color(0xFFE8ECEB)
    val ArchivedTextLight = Color(0xFF566461)
    val ArchivedBgDark = Color(0xFF222726)
    val ArchivedTextDark = Color(0xFFA5B4B0)

    // Caveat / Callout (Warm Amber)
    val CaveatBgLight = Color(0xFFFAF4EA)
    val CaveatBorderLight = Color(0xFFEAD8B8)
    val CaveatTextLight = Color(0xFF8C6527)
    val CaveatBgDark = Color(0xFF282218)
    val CaveatBorderDark = Color(0xFF423724)
    val CaveatTextDark = Color(0xFFDFC28E)
}
