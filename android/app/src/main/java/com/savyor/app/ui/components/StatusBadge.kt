package com.savyor.app.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savyor.app.ui.theme.InterFontFamily
import com.savyor.app.ui.theme.StatusBadgeColors

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    isFrosted: Boolean = false
) {
    val dark = isSystemInDarkTheme()
    val upper = status.uppercase()

    val (bg, text) = when (upper) {
        "UNREAD" -> if (dark) StatusBadgeColors.UnreadBgDark to StatusBadgeColors.UnreadTextDark
                    else StatusBadgeColors.UnreadBgLight to StatusBadgeColors.UnreadTextLight
        "REVIEWED" -> if (dark) StatusBadgeColors.ReviewedBgDark to StatusBadgeColors.ReviewedTextDark
                      else StatusBadgeColors.ReviewedBgLight to StatusBadgeColors.ReviewedTextLight
        "TRIED" -> if (dark) StatusBadgeColors.TriedBgDark to StatusBadgeColors.TriedTextDark
                   else StatusBadgeColors.TriedBgLight to StatusBadgeColors.TriedTextLight
        "ARCHIVED" -> if (dark) StatusBadgeColors.ArchivedBgDark to StatusBadgeColors.ArchivedTextDark
                      else StatusBadgeColors.ArchivedBgLight to StatusBadgeColors.ArchivedTextLight
        else -> if (dark) StatusBadgeColors.UnreadBgDark to StatusBadgeColors.UnreadTextDark
                else StatusBadgeColors.UnreadBgLight to StatusBadgeColors.UnreadTextLight
    }

    val finalBg = if (isFrosted) bg.copy(alpha = 0.90f) else bg

    Surface(
        color = finalBg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = upper,
            fontFamily = InterFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = text,
            letterSpacing = 0.4.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
