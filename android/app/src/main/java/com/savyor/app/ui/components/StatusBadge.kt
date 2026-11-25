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
import com.savyor.app.ui.theme.CrystalTier
import com.savyor.app.ui.theme.LocalSavyorColors
import com.savyor.app.ui.theme.SavyorMonoFamily
import com.savyor.app.ui.theme.savyorLiquidCrystal

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

    val shape = RoundedCornerShape(8.dp)
    if (isFrosted) {
        Surface(
            color = Color.Transparent,
            shape = shape,
            modifier = modifier.savyorLiquidCrystal(tier = CrystalTier.THUMB_BADGE, shape = shape)
        ) {
            Text(
                text = upper,
                fontFamily = SavyorMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = text,
                letterSpacing = 0.7.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
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
                fontFamily = SavyorMonoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = text,
                letterSpacing = 0.7.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
            )
        }
    }
}
