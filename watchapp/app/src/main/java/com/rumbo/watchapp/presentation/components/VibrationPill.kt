package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.vibration.Pulse
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.presentation.theme.Border
import com.rumbo.watchapp.presentation.theme.Surface
import com.rumbo.watchapp.presentation.theme.TextPrimary
import com.rumbo.watchapp.presentation.theme.TextSecondary
import kotlin.math.roundToInt

private val PillShape = RoundedCornerShape(16.dp)
private val PillMinHeight = 32.dp
private val BarHeight = 14.dp
private val BarGap = 3.dp

// Un pulso de 50 ms mide 4 dp de ancho y uno de 900 ms o más, 18 dp: se ve cuál es largo.
private const val SHORT_MS = 50f
private const val LONG_MS = 900f
private const val MIN_BAR_DP = 4f
private const val MAX_BAR_DP = 18f

// Más pulsos que estos no caben en la píldora: se dibujan los primeros (el texto dice cuántos son).
private const val MAX_BARS = 6

/** Ancho en dp de la barra de un pulso de [durationMs]. */
internal fun pulseBarWidthDp(durationMs: Long): Float {
    val t = ((durationMs - SHORT_MS) / (LONG_MS - SHORT_MS)).coerceIn(0f, 1f)
    return MIN_BAR_DP + (MAX_BAR_DP - MIN_BAR_DP) * t
}

/** "2 pulsos medios · izquierda": cómo se siente y qué aviso es. */
fun vibrationPillText(pattern: VibrationPattern): String =
    "${pattern.description} · ${pattern.name.lowercase()}"

/**
 * Píldora con el patrón de vibración dibujado (una barra por pulso, más ancha cuanto más dura y
 * más opaca cuanto más fuerte) y en texto ("2 pulsos medios · izquierda"). Quieta: el ritmo se
 * siente en la muñeca y se lee aquí. Sirve en el emulador, que no vibra, y a quien acompaña.
 */
@Composable
fun VibrationPill(pattern: VibrationPattern?, color: Color, modifier: Modifier = Modifier) {
    if (pattern == null) return
    val pulses = pattern.pulses.take(MAX_BARS)
    PillRow(modifier.clearAndSetSemantics { contentDescription = "Vibración: ${pattern.label}" }) {
        Canvas(Modifier.size(barsWidth(pulses), BarHeight)) {
            var x = 0f
            pulses.forEach { pulse ->
                val width = pulseBarWidthDp(pulse.durationMs).dp.toPx()
                drawRoundRect(
                    color = color.copy(alpha = 0.45f + 0.55f * pulse.amplitude / 255f),
                    topLeft = Offset(x, size.height * 0.3f),
                    size = Size(width, size.height * 0.4f),
                    cornerRadius = CornerRadius(size.height * 0.2f),
                )
                x += width + BarGap.toPx()
            }
        }
        Text(
            text = vibrationPillText(pattern),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = TextPrimary,
            textAlign = TextAlign.Start,
            maxLines = 3,
        )
    }
}

private fun barsWidth(pulses: List<Pulse>) =
    (pulses.sumOf { pulseBarWidthDp(it.durationMs).roundToInt() } + BarGap.value * (pulses.size - 1).coerceAtLeast(0)).dp

/**
 * Píldora con las barras de voz moviéndose y [text] ("Leyendo destino", "Hablando"): la app está
 * hablando. Las barras son la única animación continua de la app, y solo mientras suena la voz.
 */
@Composable
fun SpeakingPill(text: String, color: Color, modifier: Modifier = Modifier) {
    PillRow(modifier) {
        SpeakingBars(color, maxHeight = 16.dp, barWidth = 3.dp, barGap = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = TextSecondary,
        )
    }
}

@Composable
private fun PillRow(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .heightIn(min = PillMinHeight)
            .background(Surface.copy(alpha = 0.9f), PillShape)
            .border(1.dp, Border, PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
