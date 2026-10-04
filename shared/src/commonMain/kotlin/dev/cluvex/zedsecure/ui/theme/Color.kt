@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.cluvex.zedsecure.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Z VPN Ultra-Deep OLED Black & Neon Lime Palette
val DeepObsidian = Color(0xFF000000) // Pure OLED Black
val PrimaryBlack = Color(0xFF050505) // Deep Pitch Black Surface
val SurfaceBlack = Color(0xFF0C0E0D) // Dark Container Surface
val ElevatedSurface = Color(0xFF141715) // Deep Surface Container
val HoverSurface = Color(0xFF1C201D) // Hover Container
val SubtleBorder = Color(0xFF222823) // Container Border

val PrimaryLime = Color(0xFFC2F138) // Vibrant Electric Neon Lime
val ElectricLime = Color(0xFFD4FF5A) // Electric Lime Pulse
val DeepLime = Color(0xFF6B930A) // Deep Lime Container
val SoftLime = Color(0xFFA8E600) // Soft Bright Lime
val AccessibleLime = Color(0xFFC2F138) // Neon Lime Accent

val PrimaryWhite = Color(0xFFFFFFFF)
val SecondaryText = Color(0xFFA5AAA0)
val MutedText = Color(0xFF686E65)
val DisabledText = Color(0xFF454A43)

val ZError = Color(0xFFFF4D4D)
val ZWarning = Color(0xFFF5B942)
val ZChampagne = Color(0xFFE6D3A3)

// Legacy alias compatibility
val ZedLime = PrimaryLime
val ZedOnLime = Color(0xFF000000)
val ZedHotPink = Color(0xFFFF5FA2)
val ZedViolet = Color(0xFF7A5CFF)
val ZedDeepViolet = Color(0xFF000000)
val ZedCyan = PrimaryLime

val LightColors: ColorScheme = darkColorScheme(
    primary = PrimaryLime,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1C2605),
    onPrimaryContainer = PrimaryLime,

    secondary = SoftLime,
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0F1403),
    onSecondaryContainer = PrimaryLime,

    background = Color(0xFF000000),
    onBackground = PrimaryWhite,
    surface = Color(0xFF050505),
    onSurface = PrimaryWhite,
    surfaceVariant = Color(0xFF0C0E0D),
    onSurfaceVariant = SecondaryText,

    outline = SubtleBorder,
    outlineVariant = ElevatedSurface,
    scrim = Color(0xFF000000),

    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0C0E0D),
    surfaceContainerHigh = Color(0xFF141715),
    surfaceContainerHighest = Color(0xFF1C201D),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = PrimaryLime,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1C2605),
    onPrimaryContainer = PrimaryLime,
    inversePrimary = DeepLime,

    secondary = SoftLime,
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0F1403),
    onSecondaryContainer = PrimaryLime,

    error = ZError,
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF331010),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF000000),
    onBackground = PrimaryWhite,
    surface = Color(0xFF050505),
    onSurface = PrimaryWhite,
    surfaceVariant = Color(0xFF0C0E0D),
    onSurfaceVariant = SecondaryText,
    surfaceTint = PrimaryLime,

    inverseSurface = PrimaryWhite,
    inverseOnSurface = Color(0xFF000000),

    outline = SubtleBorder,
    outlineVariant = ElevatedSurface,
    scrim = Color(0xFF000000),

    surfaceBright = HoverSurface,
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0C0E0D),
    surfaceContainerHigh = Color(0xFF141715),
    surfaceContainerHighest = Color(0xFF1C201D),
)
