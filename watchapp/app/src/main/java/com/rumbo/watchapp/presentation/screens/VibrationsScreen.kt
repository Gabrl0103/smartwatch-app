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
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationPatterns
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusOnRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon

private data class GuideEntry(val name: String, val pattern: VibrationPattern, val color: Color)

private val GUIDE = listOf(
    GuideEntry("En ruta", VibrationPatterns.ON_ROUTE, StatusOnRoute),
    GuideEntry("Giro próximo", VibrationPatterns.TURN_APPROACHING, StatusTurnSoon),
    GuideEntry("Izquierda", VibrationPatterns.LEFT, StatusTurnSoon),
    GuideEntry("Derecha", VibrationPatterns.RIGHT, StatusTurnSoon),
    GuideEntry("Fuera de ruta", VibrationPatterns.OFF_ROUTE, StatusOffRoute),
    GuideEntry("Sin señal GPS", VibrationPatterns.NO_SIGNAL, StatusNoSignal),
    GuideEntry("Llegada", VibrationPatterns.ARRIVED, StatusArrived),
)

/** Guía de vibraciones: cada patrón con su nombre; al tocarlo vibra y se ve debajo cómo suena. */
@Composable
fun VibrationsScreen(onPlay: (VibrationPattern) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf<GuideEntry?>(null) }
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
        GUIDE.forEach { entry ->
            PillButton(
                label = entry.name,
                onClick = {
                    selected = entry
                    onPlay(entry.pattern)
                },
                secondaryLabel = entry.pattern.label,
            )
            if (selected == entry) VibrationIndicator(entry.pattern, entry.color, showLabel = false)
        }
        PillButton("Volver", onClick = onBack)
    }
}
