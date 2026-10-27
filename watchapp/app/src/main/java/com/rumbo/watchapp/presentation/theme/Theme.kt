package com.rumbo.watchapp.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

// El texto sobre el primario va oscuro: blanco sobre #4F8EFF no llega a contraste 4.5:1.
private val RumboColors = ColorScheme(
    primary = Primary,
    onPrimary = Background,
    tertiary = Accent,
    onTertiary = Background,
    background = Background,
    onBackground = TextPrimary,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

@Composable
fun WatchappTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RumboColors,
        content = content
    )
}
