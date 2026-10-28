package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.R
import com.rumbo.watchapp.domain.port.SpeechEngine
import com.rumbo.watchapp.domain.port.SpeechStatus

@Composable
fun StartScreen(
    onSpeak: () -> Unit,
    onSavedPlaces: () -> Unit,
    voiceEnabled: Boolean,
    onVoiceChange: (Boolean) -> Unit,
    speechStatus: SpeechStatus,
    onInstallVoice: () -> Unit,
    gpsAvailable: Boolean,
) {
    CenteredScreen {
        Text(
            text = "¿A dónde vamos?",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        FilledIconButton(onClick = onSpeak, modifier = Modifier.size(72.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Hablar destino",
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = "Toca y habla",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(
            onClick = onSavedPlaces,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Lugares guardados", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        // El estado va en el texto ("sí"/"no"), no solo en el color del interruptor.
        SwitchButton(
            checked = voiceEnabled,
            onCheckedChange = onVoiceChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(if (voiceEnabled) "Voz: sí" else "Voz: no")
        }
        Text(
            text = engineLabel(speechStatus.engine),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (speechStatus.deviceVoiceMissing) {
            FilledTonalButton(
                onClick = onInstallVoice,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    "Falta la voz en español del reloj",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (!gpsAvailable) NoGpsLabel()
    }
}

private fun engineLabel(engine: SpeechEngine) = when (engine) {
    SpeechEngine.DEVICE -> "Voz: del reloj"
    SpeechEngine.GEMINI -> "Voz: Gemini"
    SpeechEngine.NONE -> "Voz: no disponible"
}
