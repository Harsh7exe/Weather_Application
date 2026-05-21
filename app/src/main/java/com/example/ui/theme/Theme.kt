package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ImmersiveDarkColorScheme = darkColorScheme(
    primary = PrimaryWhite,
    secondary = PaleSkyBlue,
    tertiary = AccentIndigo,
    background = CosmicBlack,
    surface = DeepSlateBlue,
    onPrimary = CosmicBlack,
    onSecondary = SolidBlack,
    error = AlertBadgeOrange
)

private val ImmersiveLightColorScheme = lightColorScheme(
    primary = DeepSlateBlue,
    secondary = AccentIndigo,
    tertiary = PaleSkyBlue,
    background = SecondaryWhite,
    surface = PrimaryWhite,
    onPrimary = PrimaryWhite,
    onSecondary = PrimaryWhite,
    error = AlertBadgeOrange
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default to deliver the premium Immersive UI vibe
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve our tailored gradient design perfectly
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) ImmersiveDarkColorScheme else ImmersiveLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
