package com.savyor.app.ui.theme

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

enum class CrystalTier {
    CHROME,
    PILL,
    SHEET,
    THUMB_BADGE
}

/**
 * True Liquid Crystal optical modifier:
 * - Multi-stop vertical frosted fill
 * - Top Fresnel dome sheen (top ~45% height)
 * - Prismatic linear-gradient specular rim border
 * - Inner bottom caustic hairline for CHROME
 * - Sculpted ambient elevation halo
 */
fun Modifier.savyorLiquidCrystal(
    tier: CrystalTier,
    shape: Shape = RectangleShape,
    activeRim: Boolean = false
): Modifier = composed {
    val colors = LocalSavyorColors.current

    when (tier) {
        CrystalTier.CHROME -> {
            val elevation = if (colors.isDark) 10.dp else 6.dp
            val ambient = if (colors.isDark) Color.Black else Color(0x1A111113)
            val spot = if (colors.isDark) Color.Black else Color(0x22111113)

            this
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = ambient,
                    spotColor = spot
                )
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(colors.chromeGradTop, colors.chromeGradBottom)
                    ),
                    shape = shape
                )
                .drawWithContent {
                    drawContent()
                    // Top Fresnel dome sheen (top 45% height)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to colors.chromeDomeTop,
                            0.25f to colors.chromeDomeMid,
                            0.45f to Color.Transparent
                        )
                    )
                    // Inner bottom caustic hairline
                    drawLine(
                        color = colors.chromeBottomCaustic,
                        start = Offset(x = size.height * 0.45f, y = size.height - 1.5.dp.toPx()),
                        end = Offset(x = size.width - size.height * 0.45f, y = size.height - 1.5.dp.toPx()),
                        strokeWidth = 0.75.dp.toPx()
                    )
                }
                .border(
                    width = 1.25.dp,
                    brush = Brush.linearGradient(
                        0.00f to colors.chromeRim0,
                        0.28f to colors.chromeRim28,
                        0.55f to colors.chromeRim55,
                        0.82f to colors.chromeRim82,
                        1.00f to colors.chromeRim100
                    ),
                    shape = shape
                )
        }

        CrystalTier.PILL -> {
            val topRim = if (activeRim) colors.chromeRim0 else colors.pillRimTop
            val bottomRim = if (activeRim) colors.chromeRim28 else colors.pillRimBottom

            this
                .clip(shape)
                .background(colors.pillFill, shape)
                .drawWithContent {
                    drawContent()
                    // Subtle top 38% gloss sheen
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to colors.pillSheenTop,
                            0.38f to Color.Transparent
                        )
                    )
                }
                .border(
                    width = if (activeRim) 1.25.dp else 1.dp,
                    brush = Brush.verticalGradient(listOf(topRim, bottomRim)),
                    shape = shape
                )
        }

        CrystalTier.THUMB_BADGE -> {
            this
                .clip(shape)
                .background(colors.thumbBadgeFill, shape)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(colors.thumbBadgeRimTop, colors.thumbBadgeRimBottom)
                    ),
                    shape = shape
                )
        }

        CrystalTier.SHEET -> {
            this
                .clip(shape)
                .background(colors.sheetFill, shape)
                .drawWithContent {
                    drawContent()
                    // Curved top-center caustic glow
                    if (size.width > 0f) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(colors.sheetCausticGlow, Color.Transparent),
                                center = Offset(size.width * 0.5f, 0f),
                                radius = size.width * 0.65f
                            )
                        )
                    }
                }
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(colors.sheetRimTop, colors.sheetRimBottom)
                    ),
                    shape = shape
                )
        }
    }
}

/**
 * 1dp sculpted vertical gradient border for solid surfaceCard items (120Hz safe).
 */
fun Modifier.savyorCardBorder(shape: Shape): Modifier = composed {
    val colors = LocalSavyorColors.current
    this.border(
        width = 1.dp,
        brush = Brush.verticalGradient(listOf(colors.cardRimTop, colors.cardRimBottom)),
        shape = shape
    )
}

/**
 * Enables real Android 12+ (API 31+) native window backdrop blur on ModalBottomSheet / Dialog windows,
 * guarded by WindowManager.isCrossWindowBlurEnabled.
 */
@Composable
fun ApplySheetWindowBlur(
    backgroundBlurRadiusPx: Int = 72,
    blurBehindRadiusPx: Int = 42
) {
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val window = (view.parent as? DialogWindowProvider)?.window
                val wm = context.getSystemService(WindowManager::class.java)
                if (window != null && wm != null && wm.isCrossWindowBlurEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = blurBehindRadiusPx
                    }
                    window.setBackgroundBlurRadius(backgroundBlurRadiusPx)
                }
            } catch (_: Exception) {
                // Ignore safely on OEMs that restrict cross-window blur
            }
        }
    }
}
