package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusOnRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon

/** Color del estado en que suena cada aviso, el mismo de la pantalla "En ruta". */
fun VibrationCue.color(): Color = when (this) {
    VibrationCue.ON_ROUTE -> StatusOnRoute
    VibrationCue.TURN_APPROACHING, VibrationCue.LEFT, VibrationCue.RIGHT -> StatusTurnSoon
    VibrationCue.OFF_ROUTE -> StatusOffRoute
    VibrationCue.NO_SIGNAL -> StatusNoSignal
    VibrationCue.ARRIVED -> StatusArrived
}

/**
 * Guía de vibraciones: cada patrón de [patterns] (la versión que entiende este reloj) con su
 * nombre y cómo se siente; al tocarlo vibra y se ve debajo cómo suena.
 */
@Composable
fun VibrationsScreen(
    patterns: Map<VibrationCue, VibrationPattern>,
    onPlay: (VibrationCue) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf<VibrationCue?>(null) }
    RumboScreen(paneTitle = "Vibraciones") {
        Text(
            text = "Vibraciones",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Toca uno para sentirlo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        VibrationCue.entries.forEach { cue ->
            val pattern = patterns.getValue(cue)
            PillButton(
                label = pattern.name,
                onClick = {
                    selected = cue
                    onPlay(cue)
                },
                secondaryLabel = pattern.description,
            )
            if (selected == cue) VibrationIndicator(pattern, cue.color(), showLabel = false)
        }
        PillButton("Volver", onClick = onBack)
    }
}
