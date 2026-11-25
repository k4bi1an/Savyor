package com.savyor.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 32dp mini Liquid Obsidian emblem with porcelain Bookmark-Play monogram.
 */
@Composable
fun SavyorMiniEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF24242E), Color(0xFF09090B)),
                    center = Offset(0.5f, 0.25f)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0x4DFFFFFF), Color(0x14FFFFFF))
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val sx = this.size.width / 24f
            val sy = this.size.height / 24f
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                // Bookmark outer silhouette
                moveTo(7.5f * sx, 2f * sy)
                lineTo(16.5f * sx, 2f * sy)
                quadraticBezierTo(19f * sx, 2f * sy, 19f * sx, 4.5f * sy)
                lineTo(19f * sx, 22f * sy)
                lineTo(12.0f * sx, 17.5f * sy)
                lineTo(5f * sx, 22f * sy)
                lineTo(5f * sx, 4.5f * sy)
                quadraticBezierTo(5f * sx, 2f * sy, 7.5f * sx, 2f * sy)
                close()
                // Inner play cutout
                moveTo(10.4f * sx, 7.25f * sy)
                lineTo(10.4f * sx, 12.25f * sy)
                lineTo(15.2f * sx, 9.75f * sy)
                close()
            }
            drawPath(path = path, color = Color(0xFFF4F4F5))
        }
    }
}
