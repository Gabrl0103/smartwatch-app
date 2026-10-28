package com.rumbo.watchapp.presentation.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.theme.Accent

@Composable
fun ListeningScreen() {
    val pulse = rememberInfiniteTransition(label = "escuchando").animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulso",
    )
    RumboScreen(paneTitle = "Escuchando") {
        StateBadge("Escuchando", Accent, announce = true)
        StateIcon(
            kind = StateIconKind.MIC,
            color = Accent,
            modifier = Modifier.graphicsLayer { alpha = pulse.value },
            contentDescription = "Micrófono encendido",
        )
        Text(
            text = "Di el nombre del lugar",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
