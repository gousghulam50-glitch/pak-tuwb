package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val PakTubeLightColorScheme = lightColorScheme(
    primary = PakGreen,
    onPrimary = PakWhite,
    primaryContainer = PakGreenLight,
    onPrimaryContainer = PakGreenDark,
    secondary = PakLimeAccent,
    onSecondary = PakCharcoal,
    secondaryContainer = PakSurfaceVariant,
    onSecondaryContainer = PakCharcoal,
    background = PakWhite,
    onBackground = PakCharcoal,
    surface = PakWhite,
    onSurface = PakCharcoal,
    surfaceVariant = PakSurfaceVariant,
    onSurfaceVariant = PakSubtleText,
    outline = PakOutline,
    error = PakError,
    onError = PakWhite
)

private val PakTubeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PakTubeTheme(
    rtl: Boolean = true,
    content: @Composable () -> Unit
) {
    val direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = PakTubeLightColorScheme,
            typography = PakTubeTypography,
            shapes = PakTubeShapes,
            content = content
        )
    }
}
