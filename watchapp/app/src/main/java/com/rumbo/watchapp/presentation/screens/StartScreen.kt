package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.port.SpeechEngine
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.presentation.components.MicButton
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillSwitch
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.StatusNoSignal

@Composable
fun StartScreen(
    onSpeak: () -> Unit,
    onSavedPlaces: () -> Unit,
    onVibrations: () -> Unit,
    voiceEnabled: Boolean,
    onVoiceChange: (Boolean) -> Unit,
    speechStatus: SpeechStatus,
    onInstallVoice: () -> Unit,
    gpsAvailable: Boolean,
) {
    RumboScreen(paneTitle = "Inicio") {
        StateBadge("Listo para navegar", Primary)
        // Sin permiso de ubicación la ruta sale del centro de Pasto.
        if (!gpsAvailable) StateBadge("Sin GPS", StatusNoSignal)
        Text(
            text = "¿A dónde vamos?",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        MicButton(onClick = onSpeak, contentDescription = "Hablar destino")
        Text(
            text = "Toca y habla",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PillButton("Lugares guardados", onClick = onSavedPlaces)
        PillButton("Vibraciones", onClick = onVibrations, secondaryLabel = "Aprende los patrones")
        // El estado va en el texto ("sí"/"no"), no solo en el color del interruptor.
        PillSwitch(
            checked = voiceEnabled,
            onCheckedChange = onVoiceChange,
            label = if (voiceEnabled) "Voz: sí" else "Voz: no",
            secondaryLabel = engineLabel(speechStatus.engine),
        )
        if (speechStatus.deviceVoiceMissing) {
            PillButton("Falta la voz en español del reloj", onClick = onInstallVoice)
        }
    }
}

private fun engineLabel(engine: SpeechEngine) = when (engine) {
    SpeechEngine.DEVICE -> "Del reloj"
    SpeechEngine.GEMINI -> "Gemini"
    SpeechEngine.NONE -> "No disponible"
}
