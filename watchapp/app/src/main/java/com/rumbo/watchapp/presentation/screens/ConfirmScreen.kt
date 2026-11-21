package com.rumbo.watchapp.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.application.usecase.SAVE_FAILED_MESSAGE
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.presentation.components.Dial
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.RingStyle
import com.rumbo.watchapp.presentation.components.SpeakingPill
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.StatusPill
import com.rumbo.watchapp.presentation.format.describeDistance
import com.rumbo.watchapp.presentation.format.formatDistance
import com.rumbo.watchapp.presentation.format.formatMinutes
import com.rumbo.watchapp.presentation.format.formatWalkingMinutes
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.TextSecondary
import com.rumbo.watchapp.presentation.theme.TickMajor
import com.rumbo.watchapp.presentation.viewmodel.SaveStatus
import kotlin.math.ceil

/**
 * Confirmar destino: insignia "Destino", el nombre, la distancia y los minutos a pie con sus
 * íconos y, mientras la app lo dice en voz alta, la píldora "Leyendo destino" con las barras (su
 * lugar está reservado: nada se mueve justo cuando la persona va a tocar "Ir"). Debajo "Ir",
 * "Hablar" (micrófono), "Cambiar" y "Guardar".
 */
@Composable
fun ConfirmScreen(
    route: Route,
    save: SaveStatus,
    gpsAvailable: Boolean,
    onGo: () -> Unit,
    onChange: () -> Unit,
    onSave: () -> Unit,
    speaking: Boolean = false,
    onSpeak: () -> Unit = {},
) {
    RumboScreen(paneTitle = "Confirmar destino", dial = { Dial(Primary, RingStyle.IDLE) }) {
        StatusPill("Destino", Primary, icon = StateIconKind.PIN)
        if (!gpsAvailable) StatusPill("Sin GPS", StatusNoSignal, icon = StateIconKind.NO_SIGNAL)
        Text(
            text = route.destinationName,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp, lineHeight = 29.sp),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val minutes = ceil(route.durationS / 60).toInt().coerceAtLeast(1)
        Row(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "A ${describeDistance(route.distanceM)}, ${formatWalkingMinutes(route.durationS)}"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StateIcon(StateIconKind.WALK, TextSecondary, size = 16.dp, contentDescription = null)
            MetaText(formatDistance(route.distanceM))
            Box(Modifier.size(4.dp).background(TickMajor, CircleShape))
            StateIcon(StateIconKind.CLOCK, TextSecondary, size = 16.dp, contentDescription = null)
            MetaText(formatMinutes(minutes))
        }
        // Se funde al empezar y al terminar de hablar; TalkBack no lo lee (ya dice el destino).
        val talking by animateFloatAsState(if (speaking) 1f else 0f, label = "habla")
        SpeakingPill(
            text = "Leyendo destino",
            color = Primary,
            modifier = Modifier.graphicsLayer { alpha = talking }.clearAndSetSemantics {},
        )
        PillButton("Ir", onClick = onGo, style = PillStyle.PRIMARY)
        // El micrófono: guardar, repetir, cancelar o pedir otro destino sin tocar nada más.
        PillButton("Hablar", onClick = onSpeak, style = PillStyle.ASSISTANT, secondaryLabel = "Micrófono")
        PillButton("Cambiar", onClick = onChange)
        // Secundario: no compite con "Ir". Siempre activo para que el texto mantenga contraste.
        PillButton(saveLabel(save), onClick = onSave)
        if (save == SaveStatus.FAILED) {
            Text(
                text = SAVE_FAILED_MESSAGE,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelSmall,
                color = StatusOffRoute,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
}

private fun saveLabel(save: SaveStatus) = when (save) {
    SaveStatus.IDLE, SaveStatus.FAILED -> "Guardar"
    SaveStatus.SAVING -> "Guardando..."
    SaveStatus.SAVED -> "Guardado ✓"
}
