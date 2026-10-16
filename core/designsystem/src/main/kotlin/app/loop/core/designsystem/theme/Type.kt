package app.loop.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.loop.core.designsystem.R

private fun sora(weight: Int) = Font(
    resId = R.font.sora,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Sora, bundled (OFL licence), so no font download is ever needed. */
val Sora = FontFamily(sora(400), sora(500), sora(600), sora(700))

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Sora,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

internal val LoopTypography = Typography(
    displayLarge = style(56, 60, FontWeight.Bold, -0.04),
    displayMedium = style(44, 50, FontWeight.Bold, -0.03),
    displaySmall = style(36, 42, FontWeight.Bold, -0.02),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.02),
    headlineMedium = style(26, 32, FontWeight.SemiBold, -0.01),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.01),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(17, 24, FontWeight.SemiBold),
    titleSmall = style(15, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(13, 18, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.SemiBold),
    labelMedium = style(13, 16, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium, 0.02),
)
