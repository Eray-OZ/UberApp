package com.erayoz.uberapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = UberGreen,
    onPrimary = UberSurface,
    background = UberGray,
    onBackground = UberOnSurface,
    surface = UberSurface,
    onSurface = UberOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = UberGreen,
    onPrimary = UberBlack,
    background = UberBlack,
    onBackground = UberSurface,
    surface = ColorTokens.DarkSurface,
    onSurface = UberSurface
)

private object ColorTokens {
    val DarkSurface = UberOnSurface
}

@Composable
fun UberAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = UberTypography,
        content = content
    )
}
