package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.vibration.VibrationCue
import com.rumbo.watchapp.domain.vibration.PulseDuration
import com.rumbo.watchapp.domain.vibration.VibrationPattern
import com.rumbo.watchapp.domain.vibration.VibrationSettings
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusOnRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.viewmodel.DemoStep

/** Color del estado en que suena cada aviso, el mismo de la pantalla "En ruta". */
fun VibrationCue.color(): Color = when (this) {
    VibrationCue.ON_ROUTE -> StatusOnRoute
    VibrationCue.TURN_APPROACHING, VibrationCue.LEFT, VibrationCue.RIGHT, VibrationCue.ATTENTION -> StatusTurnSoon
    VibrationCue.OFF_ROUTE -> StatusOffRoute
    VibrationCue.NO_SIGNAL -> StatusNoSignal
    VibrationCue.ARRIVED -> StatusArrived
}

/**
 * Probar vibraciones. Arriba dice si el reloj controla la intensidad y están los ajustes
 * "Intensidad" y "Duración" ([settings]; cada toque pasa al siguiente valor); debajo, cada patrón
 * de [patterns] (la versión que entiende este reloj, con los ajustes aplicados) con su nombre y
 * cómo se siente: al tocarlo vibra y se ven las barras animadas con su nombre. Con [demo]
 * muestra en grande el patrón que está sonando en la secuencia de demostración.
 */
@Composable
fun VibrationsScreen(
    patterns: Map<VibrationCue, VibrationPattern>,
    supportsAmplitude: Boolean,
    settings: VibrationSettings,
    demo: DemoStep?,
    onPlay: (VibrationCue) -> Unit,
    onChangeIntensity: () -> Unit,
    onChangeDuration: () -> Unit,
    onStartDemo: () -> Unit,
    onStopDemo: () -> Unit,
    onBack: () -> Unit,
) {
    if (demo != null) {
        DemoScreen(demo, patterns.getValue(demo.cue), onStopDemo)
    } else {
        Guide(patterns, supportsAmplitude, onPlay, onStartDemo, onBack) {
            Settings(settings, supportsAmplitude, onChangeIntensity, onChangeDuration)
        }
    }
}

/** Los ajustes dicen su valor en el texto ("Intensidad: suave"); tocar pasa al siguiente y vibra. */
@Composable
private fun Settings(
    settings: VibrationSettings,
    supportsAmplitude: Boolean,
    onChangeIntensity: () -> Unit,
    onChangeDuration: () -> Unit,
) {
    PillButton(
        "Intensidad: ${settings.intensity.label}",
        onClick = onChangeIntensity,
        secondaryLabel = if (supportsAmplitude) "Toca para cambiar" else "Este reloj no la controla",
    )
    PillButton(
        "Duración: ${settings.duration.label}",
        onClick = onChangeDuration,
        secondaryLabel = when (settings.duration) {
            PulseDuration.LONG -> "Pulsos 30 % más largos"
            PulseDuration.NORMAL -> "Toca para alargar"
        },
    )
}

@Composable
private fun Guide(
    patterns: Map<VibrationCue, VibrationPattern>,
    supportsAmplitude: Boolean,
    onPlay: (VibrationCue) -> Unit,
    onStartDemo: () -> Unit,
    onBack: () -> Unit,
    settings: @Composable () -> Unit,
) {
    var selected by remember { mutableStateOf<VibrationCue?>(null) }
    // Cada toque reinicia la animación para que vaya a la par con la vibración.
    var taps by remember { mutableIntStateOf(0) }
    RumboScreen(paneTitle = "Vibraciones") {
        Text(
            text = "Vibraciones",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        // El soporte va en el texto ("sí"/"no"), no solo en el color.
        StateBadge(
            text = if (supportsAmplitude) "Intensidad: sí" else "Intensidad: no",
            color = if (supportsAmplitude) Primary else StatusNoSignal,
        )
        Text(
            text = if (supportsAmplitude) "El reloj varía la fuerza" else "Se distinguen por ritmo y duración",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        settings()
        PillButton(
            "Secuencia de demostración",
            onClick = onStartDemo,
            style = PillStyle.PRIMARY,
            secondaryLabel = "Todos, uno tras otro",
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
                    taps++
                    onPlay(cue)
                },
                secondaryLabel = pattern.description,
            )
            if (selected == cue) {
                key(taps) { VibrationIndicator(pattern, cue.color(), showLabel = false, showName = true) }
            }
        }
        PillButton("Volver", onClick = onBack)
    }
}

/** Un paso de la demostración: el nombre en grande (TalkBack lo anuncia), cuál va y sus barras. */
@Composable
private fun DemoScreen(demo: DemoStep, pattern: VibrationPattern, onStop: () -> Unit) {
    RumboScreen(paneTitle = "Demostración de vibraciones") {
        Text(
            text = "Demostración",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${demo.number} de ${demo.total}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = pattern.name,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.displaySmall,
            color = demo.cue.color(),
            textAlign = TextAlign.Center,
        )
        // Las barras empiezan de cero con cada patrón, a la par con la vibración.
        key(demo.number) { VibrationIndicator(pattern, demo.cue.color()) }
        PillButton("Detener", onClick = onStop, style = PillStyle.PRIMARY)
    }
}
