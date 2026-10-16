package app.loop.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw palette from the Loop logo system. Screens use [MaterialTheme] roles or [AreaColors], never these directly. */
internal object LoopPalette {
    val Ink = Color(0xFF111418)
    val InkRaised = Color(0xFF1C2127)
    val InkHigh = Color(0xFF252B33)
    val Slate = Color(0xFF4A5560)
    val SlateLight = Color(0xFFA9B3BD)
    val Paper = Color(0xFFF6F7F5)
    val PaperDim = Color(0xFFECEEEA)
    val Hairline = Color(0xFFD5D9D4)
    val HairlineDark = Color(0xFF3A424C)
    val White = Color(0xFFFFFFFF)

    // Brand accent (= Move). Text-on-accent uses the deeper shade to keep 4.5:1 contrast.
    val Accent = Color(0xFF2F9E6A)
    val AccentDeep = Color(0xFF1E7A4F)
    val AccentSoft = Color(0xFFD6F0E2)
    val AccentNight = Color(0xFF5CC492)

    val Move = Color(0xFF2F9E6A)
    val Sleep = Color(0xFF5B5BD6)
    val Screen = Color(0xFFE8743B)
    val Fuel = Color(0xFFD9A21B)

    // Lifted versions for dark backgrounds.
    val MoveNight = Color(0xFF4CC08A)
    val SleepNight = Color(0xFF8C8CF2)
    val ScreenNight = Color(0xFFF2925F)
    val FuelNight = Color(0xFFEBBC45)

    // Period tracker accent (not one of the four areas).
    val Cycle = Color(0xFFC94F72)
    val CycleNight = Color(0xFFF08AA5)

    val Error = Color(0xFFB3261E)
    val ErrorNight = Color(0xFFF2B8B5)
}

/** One fixed colour per area, used everywhere that area appears. */
@Immutable
data class AreaColors(
    val move: Color,
    val sleep: Color,
    val screen: Color,
    val fuel: Color,
    /** The period tracker's accent. */
    val cycle: Color,
    /** Unfilled part of a ring. */
    val trackAlpha: Float,
)

internal val LightAreaColors = AreaColors(
    move = LoopPalette.Move,
    sleep = LoopPalette.Sleep,
    screen = LoopPalette.Screen,
    fuel = LoopPalette.Fuel,
    cycle = LoopPalette.Cycle,
    trackAlpha = 0.16f,
)

internal val DarkAreaColors = AreaColors(
    move = LoopPalette.MoveNight,
    sleep = LoopPalette.SleepNight,
    screen = LoopPalette.ScreenNight,
    fuel = LoopPalette.FuelNight,
    cycle = LoopPalette.CycleNight,
    trackAlpha = 0.22f,
)

val LocalAreaColors = staticCompositionLocalOf { LightAreaColors }
