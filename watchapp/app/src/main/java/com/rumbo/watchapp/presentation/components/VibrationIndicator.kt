package com.rumbo.watchapp.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.presentation.theme.Border

private val BarWidth = 22.dp
private val BarHeight = 8.dp
private val BarGap = 4.dp

// Pausa entre repeticiones de los patrones que suenan una sola vez.
private const val IDLE_MS = 1_000L

/**
 * Muestra el patrón de vibración: una barra en píldora (22×8 dp) por pulso, que se enciende al
 * ritmo real del patrón (más opaca cuanto más fuerte), y su nombre en texto. Sirve en el
 * emulador, que no vibra, y a quien acompaña al usuario.
 */
@Composable
fun VibrationIndicator(pattern: VibrationPattern?, color: Color, modifier: Modifier = Modifier) {
    if (pattern == null) return
    val pulses = remember(pattern) { pattern.pulses }
    val cycleMs = (pattern.repeatEveryMs ?: (pattern.durationMs + IDLE_MS))
        .coerceAtLeast(pattern.durationMs + 1)
        .toInt()
    val transition = rememberInfiniteTransition(label = "vibracion")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = cycleMs.toFloat(),
        animationSpec = infiniteRepeatable(tween(cycleMs, easing = LinearEasing)),
        label = "tiempo",
    )
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Vibración: ${pattern.label}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(BarWidth * pulses.size + BarGap * (pulses.size - 1), BarHeight)) {
            val width = BarWidth.toPx()
            val gap = BarGap.toPx()
            val radius = CornerRadius(size.height / 2)
            // El tiempo se lee al dibujar: solo se redibuja, no se recompone en cada cuadro.
            val now = time.value
            pulses.forEachIndexed { i, pulse ->
                val on = now >= pulse.startMs && now < pulse.startMs + pulse.durationMs
                drawRoundRect(
                    color = if (on) color.copy(alpha = 0.4f + 0.6f * pulse.amplitude / 255f) else Border,
                    topLeft = Offset(i * (width + gap), 0f),
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
        }
        Text(
            text = pattern.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
