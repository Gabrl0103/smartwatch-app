package com.rumbo.watchapp.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.rumbo.watchapp.presentation.theme.TickMajor
import com.rumbo.watchapp.presentation.theme.TickMinor

/**
 * Bisel ([BezelTicks]) y anillo ([ProgressRing]) juntos, como los dibuja cada pantalla. Las
 * marcas se encienden con [tickProgress] (por defecto, el mismo avance del anillo).
 * [modifier] y [contentDescription] van al anillo, que es lo único que lee TalkBack.
 */
@Composable
fun Dial(
    color: Color,
    style: RingStyle,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    tickProgress: Float = progress,
    majorColor: Color = TickMajor,
    minorColor: Color = TickMinor,
    contentDescription: String? = null,
) {
    BezelTicks(progress = tickProgress, litColor = color, majorColor = majorColor, minorColor = minorColor)
    ProgressRing(
        progress = progress,
        color = color,
        contentDescription = contentDescription,
        modifier = modifier,
        style = style,
    )
}
