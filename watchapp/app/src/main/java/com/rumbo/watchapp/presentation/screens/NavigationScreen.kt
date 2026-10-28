package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.format.formatDistance
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusOnRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon

/** Pantalla "En ruta" sin mapa: estado, siguiente paso, distancia restante y vibración actual. */
@Composable
fun NavigationScreen(state: TrackingState, onFinish: () -> Unit) {
    KeepScreenOn()
    val color = statusColor(state.status)
    val arrived = state.status == NavigationStatus.ARRIVED
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = statusLabel(state.status),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            textAlign = TextAlign.Center,
        )
        if (state.recalculating) {
            Text(
                text = "Recalculando ruta...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (arrived) "Llegaste a ${state.destinationName}" else state.nextInstruction.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val toNextStep = state.distanceToNextStepM
        if (!arrived && toNextStep != null) {
            Text(
                text = "En ${formatDistance(toNextStep)}",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        if (!arrived) {
            Text(
                text = "Faltan ${formatDistance(state.remainingM)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        VibrationIndicator(pattern = state.pattern, color = color)
        FilledTonalButton(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Terminar", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

private fun statusLabel(status: NavigationStatus) = when (status) {
    NavigationStatus.ON_ROUTE -> "En ruta"
    NavigationStatus.TURN_APPROACHING -> "Giro próximo"
    NavigationStatus.MANEUVER -> "Gira ahora"
    NavigationStatus.OFF_ROUTE -> "Fuera de ruta"
    NavigationStatus.NO_SIGNAL -> "Sin señal GPS"
    NavigationStatus.ARRIVED -> "Llegaste"
}

private fun statusColor(status: NavigationStatus): Color = when (status) {
    NavigationStatus.ON_ROUTE -> StatusOnRoute
    NavigationStatus.TURN_APPROACHING, NavigationStatus.MANEUVER -> StatusTurnSoon
    NavigationStatus.OFF_ROUTE -> StatusOffRoute
    NavigationStatus.NO_SIGNAL -> StatusNoSignal
    NavigationStatus.ARRIVED -> StatusArrived
}
