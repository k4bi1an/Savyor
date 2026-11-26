package com.savyor.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
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
    val isUnread = upper == "UNREAD"
    val isProcessing = upper in setOf("PROCESSING", "QUEUED", "DOWNLOADING", "UPLOADING", "ANALYZING", "FRAMES")

    val displayLabel = if (isProcessing) "PROCESSING" else upper

    val (bg, text) = when {
        isProcessing -> colors.unreadBg to colors.unreadFg
        upper == "UNREAD" -> colors.unreadBg to colors.unreadFg
        upper == "REVIEWED" -> colors.reviewedBg to colors.reviewedFg
        upper == "TRIED" -> colors.triedBg to colors.triedFg
        upper == "ARCHIVED" -> colors.surfaceRecessed to colors.inkMuted
        upper == "FAILED" || upper == "ERROR" -> colors.errorBg to colors.errorFg
        else -> colors.unreadBg to colors.unreadFg
    }

    val shape = RoundedCornerShape(8.dp)
    val baseModifier = if (isFrosted) {
        modifier.savyorLiquidCrystal(tier = CrystalTier.THUMB_BADGE, shape = shape)
    } else {
        modifier
    }

    val unreadModifier = if (isUnread) {
        baseModifier
            .border(
                width = 1.dp,
                color = text.copy(alpha = 0.45f),
                shape = shape
            )
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(text.copy(alpha = 0.20f), Color.Transparent),
                        center = center,
                        radius = size.maxDimension * 0.7f
                    )
                )
            }
    } else {
        baseModifier
    }

    Surface(
        color = if (isFrosted) Color.Transparent else bg,
        shape = shape,
        modifier = unreadModifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
        ) {
            if (isProcessing) {
                val pulseTransition = rememberInfiniteTransition(label = "processingPulse")
                val dotAlpha by pulseTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 700),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotAlpha"
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(text.copy(alpha = dotAlpha))
                )
            }

            Text(
                text = displayLabel,
                style = TextStyle(
                    fontFamily = SavyorMonoFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = text,
                    letterSpacing = 0.7.sp,
                    shadow = if (isUnread) {
                        Shadow(
                            color = text.copy(alpha = 0.85f),
                            offset = Offset.Zero,
                            blurRadius = 14f
                        )
                    } else {
                        null
                    }
                )
            )
        }
    }
}
