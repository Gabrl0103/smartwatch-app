package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.components.DoubleTapGate
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.ProgressRing
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.VibrationIndicator
import com.rumbo.watchapp.presentation.components.doubleTapAnywhere
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

// Más líneas que esto a 20 sp no caben en el círculo debajo del ícono: la última queda cortada
// por el borde. Entonces la indicación baja a 16 sp y el ícono a 48 dp.
private const val MAX_INSTRUCTION_LINES = 3

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
    Maneuver.LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT -> StateIconKind.LEFT
    Maneuver.RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT -> StateIconKind.RIGHT
    Maneuver.U_TURN_LEFT, Maneuver.U_TURN_RIGHT -> StateIconKind.U_TURN
    Maneuver.ROUNDABOUT -> StateIconKind.ROUNDABOUT
    Maneuver.STRAIGHT, Maneuver.ARRIVE, null -> StateIconKind.STRAIGHT
}

/**
 * Pantalla "En ruta" sin mapa. Arriba lo que hay que hacer (estado, ícono de la maniobra e
 * indicación); debajo las distancias, la vibración y los botones. El anillo del borde muestra el
 * avance hacia el siguiente giro. Un doble toque en cualquier parte repite la indicación.
 */
@Composable
fun NavigationScreen(
    state: TrackingState,
    confirmFinish: Boolean,
    onRepeat: () -> Unit,
    onFinish: () -> Unit,
    onKeepNavigating: () -> Unit,
    onAsk: () -> Unit = {},
) {
    ScreenOnWhileNavigating(wakeKey = state.status to confirmFinish) {
        if (confirmFinish) {
            FinishRouteScreen(onFinish = onFinish, onKeepNavigating = onKeepNavigating)
        } else {
            RouteScreen(state, onRepeat = onRepeat, onAsk = onAsk, onFinish = onFinish)
        }
    }
}

@Composable
private fun RouteScreen(state: TrackingState, onRepeat: () -> Unit, onAsk: () -> Unit, onFinish: () -> Unit) {
    val look = lookOf(state)
    val arrived = state.status == NavigationStatus.ARRIVED
    val scope = rememberCoroutineScope()
    val doubleTapMs = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val repeat by rememberUpdatedState(onRepeat)
    val gate = remember(scope, doubleTapMs) { DoubleTapGate(scope, doubleTapMs) { repeat() } }
    // Con TalkBack el doble toque activa lo que esté enfocado: la información también repite.
    val repeatsOnActivate = Modifier.semantics { repeatOnActivate(onRepeat) }
    RumboScreen(
        paneTitle = "En ruta",
        modifier = Modifier.doubleTapAnywhere(gate),
        edgeRing = {
            ProgressRing(
                progress = state.stepProgress,
                color = look.color,
                contentDescription = if (arrived) "Ruta completa" else "Avance hacia el siguiente paso",
                modifier = repeatsOnActivate,
            )
        },
    ) {
        // TalkBack anuncia el estado y la indicación cuando cambian; las distancias no, para no
        // hablar en cada lectura del GPS.
        StateBadge(look.label, look.color, modifier = repeatsOnActivate, announce = true)
        // Se decide midiendo el texto, no contando letras: el corte de línea depende de las palabras
        // ("Gire a la derecha hacia Avenida Bolívar." ya ocupa cuatro).
        var compact by remember(look.headline) { mutableStateOf(false) }
        val iconSize = when {
            compact -> 48.dp
            look.headline.length > VERY_LONG_INSTRUCTION -> 60.dp
            else -> 72.dp
        }
        StateIcon(look.icon, look.color, modifier = repeatsOnActivate, size = iconSize)
        Text(
            text = look.headline,
            modifier = repeatsOnActivate.semantics { liveRegion = LiveRegionMode.Polite },
            style = when {
                compact -> MaterialTheme.typography.titleSmall
                look.headline.length > LONG_INSTRUCTION -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleLarge
            },
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout -> if (!compact && layout.lineCount > MAX_INSTRUCTION_LINES) compact = true },
        )
        if (state.recalculating) {
            Text(
                text = "Recalculando ruta...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (!arrived) Distances(state, look.color, onRepeat)
        VibrationIndicator(pattern = state.pattern, color = look.color)
        PillButton("Repetir", onClick = { gate.click(onRepeat) }, style = PillStyle.PRIMARY)
        // Preguntar algo sin dejar la ruta: las vibraciones y la voz de la ruta siguen.
        PillButton(
            "Preguntar",
            onClick = { gate.click(onAsk) },
            style = PillStyle.ASSISTANT,
            secondaryLabel = "Asistente",
        )
        PillButton("Terminar", onClick = { gate.click(onFinish) })
    }
}

private fun SemanticsPropertyReceiver.repeatOnActivate(onRepeat: () -> Unit) {
    onClick(label = "repetir la indicación") {
        onRepeat()
        true
    }
}

/** "En 30 m" (hasta el siguiente paso) y "Faltan 1,2 km" (hasta el destino). */
@Composable
private fun Distances(state: TrackingState, color: Color, onRepeat: () -> Unit) {
    val toNext = state.distanceToNextStepM?.takeIf {
        state.status != NavigationStatus.OFF_ROUTE && state.status != NavigationStatus.NO_SIGNAL
    }
    val spoken = listOfNotNull(
        toNext?.let { "En ${describeDistance(it)}" },
        "Faltan ${describeDistance(state.remainingM)}",
    ).joinToString(". ")
    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = spoken
            repeatOnActivate(onRepeat)
        },
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
