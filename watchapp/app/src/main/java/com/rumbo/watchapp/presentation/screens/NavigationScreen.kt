package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.ProgressRing
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.format.describeDistance
import com.rumbo.watchapp.presentation.format.formatDistance
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusOnRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon

// Desde este largo la indicación baja de 24 a 20 sp: así cabe en dos líneas sin desplazar.
private const val LONG_INSTRUCTION = 16

// Desde este largo ocupa tres líneas: el ícono baja a 60 dp para que se lea entera sin desplazar.
private const val VERY_LONG_INSTRUCTION = 30

/** Cómo se ve cada estado: nombre, color, ícono y la frase principal. Nunca solo el color. */
private data class StatusLook(val label: String, val color: Color, val icon: StateIconKind, val headline: String)

private fun lookOf(state: TrackingState): StatusLook {
    val instruction = state.nextInstruction ?: "Sigue la ruta"
    val maneuver = maneuverIcon(state.nextManeuver)
    return when (state.status) {
        NavigationStatus.ON_ROUTE -> StatusLook("En ruta", StatusOnRoute, maneuver, instruction)
        NavigationStatus.TURN_APPROACHING -> StatusLook("Giro próximo", StatusTurnSoon, maneuver, instruction)
        NavigationStatus.MANEUVER -> StatusLook("Gira ahora", StatusTurnSoon, maneuver, instruction)
        NavigationStatus.OFF_ROUTE ->
            StatusLook("Fuera de ruta", StatusOffRoute, StateIconKind.WARNING, "Te saliste de la ruta")
        NavigationStatus.NO_SIGNAL ->
            StatusLook("Sin señal GPS", StatusNoSignal, StateIconKind.NO_SIGNAL, "Buscando señal GPS")
        NavigationStatus.ARRIVED ->
            StatusLook("Llegaste", StatusArrived, StateIconKind.ARRIVED, "Llegaste a ${state.destinationName}")
    }
}

// La llegada aún no alcanzada se muestra como "recto": el check queda para "Llegaste".
private fun maneuverIcon(maneuver: Maneuver?) = when (maneuver) {
    Maneuver.LEFT -> StateIconKind.LEFT
    Maneuver.RIGHT -> StateIconKind.RIGHT
    Maneuver.STRAIGHT, Maneuver.ARRIVE, null -> StateIconKind.STRAIGHT
}

/**
 * Pantalla "En ruta" sin mapa. Arriba lo que hay que hacer (estado, ícono de la maniobra e
 * indicación); debajo las distancias, la vibración y los botones. El anillo del borde muestra el
 * avance hacia el siguiente giro.
 */
@Composable
fun NavigationScreen(
    state: TrackingState,
    confirmFinish: Boolean,
    onRepeat: () -> Unit,
    onFinish: () -> Unit,
    onKeepNavigating: () -> Unit,
) {
    ScreenOnWhileNavigating(wakeKey = state.status to confirmFinish) {
        if (confirmFinish) {
            FinishRouteScreen(onFinish = onFinish, onKeepNavigating = onKeepNavigating)
        } else {
            RouteScreen(state, onRepeat = onRepeat, onFinish = onFinish)
        }
    }
}

@Composable
private fun RouteScreen(state: TrackingState, onRepeat: () -> Unit, onFinish: () -> Unit) {
    val look = lookOf(state)
    val arrived = state.status == NavigationStatus.ARRIVED
    RumboScreen(
        paneTitle = "En ruta",
        edgeRing = {
            ProgressRing(
                progress = state.stepProgress,
                color = look.color,
                contentDescription = if (arrived) "Ruta completa" else "Avance hacia el siguiente paso",
            )
        },
    ) {
        // TalkBack anuncia el estado y la indicación cuando cambian; las distancias no, para no
        // hablar en cada lectura del GPS.
        StateBadge(look.label, look.color, announce = true)
        StateIcon(look.icon, look.color, size = if (look.headline.length > VERY_LONG_INSTRUCTION) 60.dp else 72.dp)
        Text(
            text = look.headline,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = if (look.headline.length > LONG_INSTRUCTION) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleLarge
            },
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.recalculating) {
            Text(
                text = "Recalculando ruta...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (!arrived) Distances(state, look.color)
        VibrationIndicator(pattern = state.pattern, color = look.color)
        PillButton("Repetir", onClick = onRepeat, style = PillStyle.PRIMARY)
        PillButton("Terminar", onClick = onFinish)
    }
}

/** "En 30 m" (hasta el siguiente paso) y "Faltan 1,2 km" (hasta el destino). */
@Composable
private fun Distances(state: TrackingState, color: Color) {
    val toNext = state.distanceToNextStepM?.takeIf {
        state.status != NavigationStatus.OFF_ROUTE && state.status != NavigationStatus.NO_SIGNAL
    }
    val spoken = listOfNotNull(
        toNext?.let { "En ${describeDistance(it)}" },
        "Faltan ${describeDistance(state.remainingM)}",
    ).joinToString(". ")
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (toNext != null) {
            Text(
                text = "En ${formatDistance(toNext)}",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = "Faltan ${formatDistance(state.remainingM)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Lo que aparece al hacer el gesto de volver en ruta: terminar no debe pasar por accidente. */
@Composable
private fun FinishRouteScreen(onFinish: () -> Unit, onKeepNavigating: () -> Unit) {
    // Pregunta y botones a la vista sin desplazar; "Seguir" va primero porque es lo seguro.
    RumboScreen(paneTitle = "¿Terminar la ruta?") {
        Text(
            text = "¿Terminar la ruta?",
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        PillButton("Seguir navegando", onClick = onKeepNavigating, style = PillStyle.PRIMARY)
        PillButton("Terminar", onClick = onFinish, style = PillStyle.DANGER)
    }
}
