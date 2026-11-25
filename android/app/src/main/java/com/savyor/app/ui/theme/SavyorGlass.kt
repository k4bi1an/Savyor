package com.savyor.app.ui.theme

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable zero-dependency Liquid Glass modifier:
 * 1. Translucent glassSurface / glassSurfaceElevated background clipped to [shape].
 * 2. Subtle top inner sheen gradient.
 * 3. 1dp specular rim border (bright top highlight -> subtle bottom rim).
 */
fun Modifier.savyorGlass(
    shape: Shape = RectangleShape,
    elevated: Boolean = false,
    borderWidth: Dp = 1.dp
): Modifier = composed {
    val colors = LocalSavyorColors.current
    val baseFill = if (elevated) colors.glassSurfaceElevated else colors.glassSurface
    val sheenTop = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.35f)

    this
        .clip(shape)
        .background(baseFill, shape)
        .background(
            brush = Brush.verticalGradient(listOf(sheenTop, Color.Transparent)),
            shape = shape
        )
        .border(
            width = borderWidth,
            brush = Brush.verticalGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom)),
            shape = shape
        )
}

/**
 * Optional native Android 12+ (API 31+) window backdrop blur for dialogs/bottom sheets,
 * safely guarded by WindowManager.isCrossWindowBlurEnabled.
 */
fun Window.applyOptionalCrossWindowBlur(radiusPx: Int = 36) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val wm = context.getSystemService(WindowManager::class.java)
            if (wm != null && wm.isCrossWindowBlurEnabled) {
                addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                setBackgroundBlurRadius(radiusPx)
            }
        } catch (_: Exception) {
            // Safely ignore on OEMs that disable cross-window blur
        }
    }
}
