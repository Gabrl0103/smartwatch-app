package com.rumbo.watchapp.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.rumbo.watchapp.presentation.theme.Background
import com.rumbo.watchapp.presentation.theme.RingTrack

/** Ancho del anillo; el contenido de la pantalla queda por dentro (ver [dialContentRadius]). */
val RingStrokeWidth = 6.dp

/**
 * PROGRESS: se llena con el avance y lleva un punto brillante en la punta. FULL: lleno (escucha,
 * llegada). DASHED: a trazos (desviado). DOTTED: punteado y tenue (sin señal). IDLE: la pista
 * con una línea fina del color, sin avance (inicio, confirmar, asistente).
 */
enum class RingStyle { PROGRESS, FULL, DASHED, DOTTED, IDLE }

// Resplandor: el mismo arco más ancho y transparente por debajo (sin desenfoque ni animación).
private val GlowWidths = listOf(14.dp to 0.10f, 10.dp to 0.18f)
private const val IDLE_LINE_ALPHA = 0.32f
private const val DOTTED_ALPHA = 0.55f

/**
 * Anillo de 6 dp en el radio 203 del diseño (de 227), con hueco arriba para la hora y un
 * resplandor del color del estado. Con [RingStyle.PROGRESS] se llena en sentido horario a medida
 * que uno se acerca al siguiente giro ([progress] de 0 a 1). TalkBack lo lee después del
 * contenido; sin [contentDescription] es decorativo y TalkBack no lo lee.
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    style: RingStyle = RingStyle.PROGRESS,
) {
    val clamped = progress.coerceIn(0f, 1f)
    // Solo se anima el paso de un valor al siguiente; quieto, no se redibuja.
    val animated by animateFloatAsState(clamped, label = "progreso")
    val semantics = if (contentDescription == null) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier.semantics {
            this.contentDescription = contentDescription
            progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
            traversalIndex = 1f
        }
    }
    Canvas(modifier = modifier.fillMaxSize().then(semantics)) {
        val radius = size.minDimension / 2 * RING_RADIUS_FRACTION
        val stroke = RingStrokeWidth.toPx()
        when (style) {
            RingStyle.PROGRESS -> {
                ringArc(RingTrack, radius, stroke, RING_SWEEP_DEGREES)
                val sweep = RING_SWEEP_DEGREES * animated
                if (sweep > 0f) glowingArc(color, radius, stroke, sweep)
                progressDot(color, radius, progressDotAngle(animated))
            }
            RingStyle.FULL -> glowingArc(color, radius, stroke, RING_SWEEP_DEGREES)
            RingStyle.DASHED -> glowingArc(
                color, radius, stroke, RING_SWEEP_DEGREES,
                dashes = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
            )
            RingStyle.DOTTED -> ringArc(
                color.copy(alpha = DOTTED_ALPHA), radius, 4.dp.toPx(), RING_SWEEP_DEGREES,
                dashes = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 7.dp.toPx())),
            )
            RingStyle.IDLE -> {
                ringArc(RingTrack, radius, stroke, RING_SWEEP_DEGREES)
                ringArc(color.copy(alpha = IDLE_LINE_ALPHA), radius, 1.dp.toPx(), RING_SWEEP_DEGREES)
            }
        }
    }
}

// Arco centrado de radio [radius] desde el borde derecho del hueco; los ángulos de Canvas
// empiezan a las 3 y los del dial a las 12, de ahí los -90°.
private fun DrawScope.ringArc(color: Color, radius: Float, width: Float, sweep: Float, dashes: PathEffect? = null) {
    drawArc(
        color = color,
        startAngle = RING_START_DEGREES - 90f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = width, cap = StrokeCap.Round, pathEffect = dashes),
    )
}

private fun DrawScope.glowingArc(color: Color, radius: Float, stroke: Float, sweep: Float, dashes: PathEffect? = null) {
    GlowWidths.forEach { (width, alpha) -> ringArc(color.copy(alpha = alpha), radius, width.toPx(), sweep, dashes) }
    ringArc(color, radius, stroke, sweep, dashes)
}

// Punto en la punta: halo, el punto del color y un centro oscuro, como un cursor.
private fun DrawScope.progressDot(color: Color, radius: Float, angle: Float) {
    val tip = pointOnDial(center.x, center.y, radius, angle)
    val at = Offset(tip.x, tip.y)
    drawCircle(color.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = at)
    drawCircle(color, radius = 5.dp.toPx(), center = at)
    drawCircle(Background, radius = 2.dp.toPx(), center = at)
}
