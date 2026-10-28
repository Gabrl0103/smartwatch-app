package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.format.describeDistance
import com.rumbo.watchapp.presentation.format.formatDistance
import com.rumbo.watchapp.presentation.format.formatWalkingMinutes
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.viewmodel.SAVE_FAILED_MESSAGE
import com.rumbo.watchapp.presentation.viewmodel.SaveStatus

@Composable
fun ConfirmScreen(
    route: Route,
    save: SaveStatus,
    gpsAvailable: Boolean,
    onGo: () -> Unit,
    onChange: () -> Unit,
    onSave: () -> Unit,
) {
    RumboScreen(paneTitle = "Confirmar destino") {
        StateBadge("Confirmar destino", Primary)
        if (!gpsAvailable) StateBadge("Sin GPS", StatusNoSignal)
        Text(
            text = route.destinationName,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val distance = formatDistance(route.distanceM)
        val minutes = formatWalkingMinutes(route.durationS)
        Column(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "A ${describeDistance(route.distanceM)}, $minutes"
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(distance, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Text(
                text = minutes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        PillButton("Ir", onClick = onGo, style = PillStyle.PRIMARY)
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

private fun saveLabel(save: SaveStatus) = when (save) {
    SaveStatus.IDLE, SaveStatus.FAILED -> "Guardar"
    SaveStatus.SAVING -> "Guardando..."
    SaveStatus.SAVED -> "Guardado"
}
