package com.rumbo.watchapp.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rumbo.watchapp.domain.voice.LEVEL_SAMPLE_MS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

const val VOICE_BAR_COUNT = 9

private val BarWidth = 8.dp
private val BarGap = 6.dp

// Cuánto pesa cada barra según su distancia al centro (0 en el centro, 1 en los bordes): el
// centro es la más alta y las de los bordes llegan al 30 %.
private const val EDGE_DROP = 0.7f

// La onda sube y baja la altura ±8 % y tarda medio ciclo en ir del centro a los bordes. Con ese
// margen el centro sigue siendo siempre la barra más alta.
private const val WAVE_DEPTH = 0.08f

// Una vuelta de la onda.
private const val WAVE_PERIOD_MS = 900

/**
 * Alto de cada barra para un nivel de voz de 0 a 1: entre [minDp] (en silencio) y [maxDp] (la
 * del centro con la voz más fuerte). Son simétricas y más altas al centro; [phase] (radianes)
 * hace que suban y bajen con un pequeño desfase del centro hacia afuera, como una onda.
 */
fun voiceBarHeights(
    level: Float,
    phase: Float,
    count: Int = VOICE_BAR_COUNT,
    minDp: Float = 16f,
    maxDp: Float = 76f,
): List<Float> {
    val voice = level.coerceIn(0f, 1f)
    val center = (count - 1) / 2f
    return List(count) { i ->
        val distance = if (center == 0f) 0f else abs(i - center) / center
        val envelope = 1f - EDGE_DROP * distance
        val wave = 1f - WAVE_DEPTH + WAVE_DEPTH * sin(phase - distance * PI.toFloat())
        minDp + (maxDp - minDp) * voice * envelope * wave
    }
}

/**
 * Nueve barras redondeadas de 8 dp que se mueven con [level] (0 a 1), entre [minHeight] y
 * [maxHeight]. Son decorativas: TalkBack no las lee (la pantalla ya dice qué pasa).
 */
@Composable
fun VoiceBars(
    level: Float,
    color: Color,
    modifier: Modifier = Modifier,
    minHeight: Dp = 16.dp,
    maxHeight: Dp = 76.dp,
) {
    // Entre lectura y lectura del micrófono el nivel se mueve de a poco, no a saltos.
    val smoothLevel by animateFloatAsState(level, tween(LEVEL_SAMPLE_MS.toInt(), easing = LinearEasing), label = "nivel")
    val phase = rememberInfiniteTransition(label = "onda").animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(WAVE_PERIOD_MS, easing = LinearEasing)),
        label = "fase",
    )
    Canvas(
        modifier
            .size(BarWidth * VOICE_BAR_COUNT + BarGap * (VOICE_BAR_COUNT - 1), maxHeight)
            .clearAndSetSemantics {},
    ) {
        val width = BarWidth.toPx()
        val gap = BarGap.toPx()
        // La fase se lee al dibujar: la onda solo redibuja, no recompone.
        val heights = voiceBarHeights(smoothLevel, phase.value, minDp = minHeight.value, maxDp = maxHeight.value)
        heights.forEachIndexed { i, heightDp ->
            val height = heightDp.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (width + gap), (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

// Movimiento de las barras mientras la app habla: sube y baja sin depender de un nivel real.
private const val SPEAKING_PERIOD_MS = 700

/**
 * Las mismas barras con un movimiento suave que no necesita el nivel real: muestran que la app
 * está hablando (voz del reloj o Gemini). Decorativas, como [VoiceBars].
 */
@Composable
fun SpeakingBars(color: Color, modifier: Modifier = Modifier, maxHeight: Dp = 24.dp) {
    val breath = rememberInfiniteTransition(label = "habla").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(SPEAKING_PERIOD_MS), RepeatMode.Reverse),
        label = "nivel",
    )
    VoiceBars(level = breath.value, color = color, modifier = modifier, minHeight = 6.dp, maxHeight = maxHeight)
}
