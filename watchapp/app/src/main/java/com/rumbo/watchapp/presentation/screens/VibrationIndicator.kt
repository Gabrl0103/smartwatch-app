package com.rumbo.watchapp.presentation.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.vibration.VibrationPattern

/**
 * Muestra el último patrón de vibración: una barra por pulso (ancho = duración, alto = fuerza)
 * y su descripción en texto. Sirve en el emulador, que no vibra.
 */
@Composable
fun VibrationIndicator(pattern: VibrationPattern?, color: Color) {
    if (pattern == null) return
    val transition = rememberInfiniteTransition(label = "vibracion")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pattern.pulseDurationsMs.zip(pattern.pulseAmplitudes).forEach { (ms, amplitude) ->
                Box(
                    modifier = Modifier
                        .width((ms / 25).toInt().coerceIn(4, 28).dp)
                        .height((3 + amplitude / 32).dp)
                        .background(color.copy(alpha = alpha), RoundedCornerShape(2.dp)),
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
