package com.savyor.app.ui.components

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
import com.savyor.app.ui.theme.LocalSavyorColors
import com.savyor.app.ui.theme.savyorGlass

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
    isFrosted: Boolean = false
) {
    val colors = LocalSavyorColors.current
    val upper = status.uppercase()

    val (bg, text) = when (upper) {
        "UNREAD" -> colors.unreadBg to colors.unreadFg
        "REVIEWED" -> colors.reviewedBg to colors.reviewedFg
        "TRIED" -> colors.triedBg to colors.triedFg
        "ARCHIVED" -> colors.surfaceRecessed to colors.inkMuted
        "FAILED", "ERROR" -> colors.errorBg to colors.errorFg
        else -> colors.unreadBg to colors.unreadFg
    }

    val shape = RoundedCornerShape(6.dp)
    if (isFrosted) {
        Surface(
            color = Color.Transparent,
            shape = shape,
            modifier = modifier.savyorGlass(shape = shape, elevated = false, borderWidth = 1.dp)
        ) {
            Text(
                text = upper,
                fontFamily = InterFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (colors.isDark) text else colors.inkPrimary,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    } else {
        Surface(
            color = bg,
            shape = shape,
            modifier = modifier
        ) {
            Text(
                text = upper,
                fontFamily = InterFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = text,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}
