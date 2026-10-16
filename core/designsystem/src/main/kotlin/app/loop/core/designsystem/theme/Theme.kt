package app.loop.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import app.loop.core.designsystem.theme.LoopPalette as P

private val LightScheme = lightColorScheme(
    primary = P.AccentDeep,
    onPrimary = P.White,
    primaryContainer = P.AccentSoft,
    onPrimaryContainer = P.Ink,
    secondary = P.Slate,
    onSecondary = P.White,
    secondaryContainer = P.PaperDim,
    onSecondaryContainer = P.Ink,
    background = P.Paper,
    onBackground = P.Ink,
    surface = P.Paper,
    onSurface = P.Ink,
    surfaceVariant = P.PaperDim,
    onSurfaceVariant = P.Slate,
    surfaceContainerLowest = P.White,
    surfaceContainerLow = P.White,
    surfaceContainer = P.White,
    surfaceContainerHigh = P.PaperDim,
    surfaceContainerHighest = P.PaperDim,
    outline = P.Hairline,
    outlineVariant = P.Hairline,
    error = P.Error,
    onError = P.White,
)

private val DarkScheme = darkColorScheme(
    primary = P.AccentNight,
    onPrimary = P.Ink,
    primaryContainer = P.AccentDeep,
    onPrimaryContainer = P.White,
    secondary = P.SlateLight,
    onSecondary = P.Ink,
    secondaryContainer = P.InkHigh,
    onSecondaryContainer = P.Paper,
    background = P.Ink,
    onBackground = P.Paper,
    surface = P.Ink,
    onSurface = P.Paper,
    surfaceVariant = P.InkHigh,
    onSurfaceVariant = P.SlateLight,
    surfaceContainerLowest = P.InkRaised,
    surfaceContainerLow = P.InkRaised,
    surfaceContainer = P.InkRaised,
    surfaceContainerHigh = P.InkHigh,
    surfaceContainerHighest = P.InkHigh,
    outline = P.HairlineDark,
    outlineVariant = P.HairlineDark,
    error = P.ErrorNight,
    onError = P.Ink,
)

/**
 * Loop's theme. Deliberately no dynamic (wallpaper) colour: the four area colours are
 * part of how people read the app, so they stay fixed.
 */
@Composable
fun LoopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalAreaColors provides if (darkTheme) DarkAreaColors else LightAreaColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LoopTypography,
            shapes = LoopShapes,
            content = content,
        )
    }
}

/** Shorthand: `LoopTheme.areas.move`, `LoopTheme.spacing.md`. */
object LoopTheme {
    val areas: AreaColors
        @Composable @ReadOnlyComposable get() = LocalAreaColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
}
