package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.rumbo.watchapp.presentation.theme.TickMajor
import com.rumbo.watchapp.presentation.theme.TickMinor

// Transparencia de las marcas cortas encendidas: las largas marcan el ritmo, las cortas acompañan.
private const val LIT_MINOR_ALPHA = 0.5f

/**
 * Bisel de 60 marcas en el borde (radio 214–222 del diseño de 454 px; cada quinta, más larga,
 * desde 208), con hueco arriba para la hora. Las que quedan detrás de la punta del anillo
 * ([progress], de 0 a 1) se encienden en [litColor]; el resto va en [majorColor] y
 * [minorColor]. Es decorativo: el avance lo lee TalkBack en [ProgressRing].
 */
@Composable
fun BezelTicks(
    progress: Float,
    litColor: Color,
    modifier: Modifier = Modifier,
    majorColor: Color = TickMajor,
    minorColor: Color = TickMinor,
) {
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {}) {
        val radius = size.minDimension / 2
        val majorWidth = 1.5.dp.toPx()
        val minorWidth = 1.dp.toPx()
        tickMarks(center.x, center.y, radius).forEach { tick ->
            val lit = isTickLit(tick.index, progress)
            val color = when {
                lit && tick.major -> litColor
                lit -> litColor.copy(alpha = LIT_MINOR_ALPHA)
                tick.major -> majorColor
                else -> minorColor
            }
            drawLine(
                color = color,
                start = Offset(tick.inner.x, tick.inner.y),
                end = Offset(tick.outer.x, tick.outer.y),
                strokeWidth = if (tick.major) majorWidth else minorWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
