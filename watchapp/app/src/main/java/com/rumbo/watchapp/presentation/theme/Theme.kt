package com.rumbo.watchapp.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

// Todo el esquema sale de los tokens de Rumbo. El texto sobre colores claros va en [OnColor].
private val RumboColors = ColorScheme(
    primary = Primary,
    primaryDim = Primary,
    primaryContainer = Surface,
    onPrimary = OnColor,
    onPrimaryContainer = TextPrimary,
    // Secundario: el asistente; lo que lleva texto usa el tono del chip para el contraste.
    secondary = AssistantChip,
    secondaryDim = Assistant,
    onSecondary = OnColor,
    tertiary = Accent,
    tertiaryDim = Accent,
    onTertiary = OnColor,
    background = Background,
    onBackground = TextPrimary,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = Border,
    error = StatusOffRoute,
    errorDim = StatusOffRoute,
    onError = OnColor,
)

@Composable
fun WatchappTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RumboColors,
        typography = RumboTypography,
        content = content
    )
}
