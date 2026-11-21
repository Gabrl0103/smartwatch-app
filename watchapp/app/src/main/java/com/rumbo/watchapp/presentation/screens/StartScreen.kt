package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.R
import com.rumbo.watchapp.domain.port.SpeechEngine
import com.rumbo.watchapp.domain.port.SpeechStatus
import com.rumbo.watchapp.presentation.components.Dial
import com.rumbo.watchapp.presentation.components.DiscFill
import com.rumbo.watchapp.presentation.components.GlassDisc
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.PillSwitch
import com.rumbo.watchapp.presentation.components.RingStyle
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.StatusPill
import com.rumbo.watchapp.presentation.theme.OnColor
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.PrimaryLight
import com.rumbo.watchapp.presentation.theme.StatusNoSignal

/**
 * Inicio: "¿A dónde vamos?", el micrófono en un disco azul que brilla y "Toca y di tu destino";
 * debajo, "Lugares guardados", "Vibraciones" y la voz. Si la app se cerró a mitad de una ruta,
 * "Continuar ruta anterior" va primero. Sin permiso de ubicación, la insignia "Sin GPS".
 */
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
    previousDestination: String? = null,
    onContinue: () -> Unit = {},
) {
    RumboScreen(paneTitle = "Inicio", dial = { Dial(Primary, RingStyle.IDLE) }) {
        // Sin permiso de ubicación: se pide al tocar el micrófono o un lugar guardado.
        if (!gpsAvailable) StatusPill("Sin GPS", StatusNoSignal, icon = StateIconKind.NO_SIGNAL)
        // Lo primero si la app se cerró a mitad de una ruta: es lo más probable que quiera hacer.
        if (previousDestination != null) {
            PillButton(
                "Continuar ruta anterior",
                onClick = onContinue,
                style = PillStyle.PRIMARY,
                secondaryLabel = previousDestination,
            )
        }
        Text(
            text = "¿A dónde vamos?",
            modifier = Modifier.widthIn(max = 150.dp).semantics { heading() },
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 22.sp, lineHeight = 27.sp),
            textAlign = TextAlign.Center,
        )
        GlassDisc(
            size = 76.dp,
            halo = Primary,
            fill = DiscFill(PrimaryLight, Primary),
            modifier = Modifier
                .clickable(onClick = onSpeak, role = Role.Button)
                .semantics { contentDescription = "Hablar destino" },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = OnColor,
            )
        }
        Text(
            text = "Toca y di tu destino",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PillButton("Lugares guardados", onClick = onSavedPlaces)
        PillButton("Vibraciones", onClick = onVibrations, secondaryLabel = "Prueba los patrones")
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
