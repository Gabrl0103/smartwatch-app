package com.rumbo.watchapp.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.rumbo.watchapp.presentation.theme.Border

/** Ancho del anillo; el contenido de la pantalla debe quedar por dentro. */
val RingStrokeWidth = 6.dp

// Hueco arriba para que el anillo no pase por debajo de la hora.
private const val TOP_GAP_DEGREES = 56f
private const val START_DEGREES = 270f + TOP_GAP_DEGREES / 2
private const val SWEEP_DEGREES = 360f - TOP_GAP_DEGREES

/**
 * Anillo pegado al borde de la pantalla redonda: se llena en sentido horario a medida que uno se
 * acerca al siguiente giro ([progress] de 0 a 1). TalkBack lo lee después del contenido.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(clamped, label = "progreso")
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                traversalIndex = 1f
            },
    ) {
        val stroke = RingStrokeWidth.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        drawArc(Border, START_DEGREES, SWEEP_DEGREES, false, topLeft, arcSize, style = style)
        if (animated > 0f) {
            drawArc(color, START_DEGREES, SWEEP_DEGREES * animated, false, topLeft, arcSize, style = style)
        }
    }
}
