package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.components.Dial
import com.rumbo.watchapp.presentation.components.DiscFill
import com.rumbo.watchapp.presentation.components.DoubleTapGate
import com.rumbo.watchapp.presentation.components.GlassDisc
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.PillStyle
import com.rumbo.watchapp.presentation.components.RingStyle
import com.rumbo.watchapp.presentation.components.RoundAction
import com.rumbo.watchapp.presentation.components.RoundActionStyle
import com.rumbo.watchapp.presentation.components.StateIcon
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.StatusPill
import com.rumbo.watchapp.presentation.components.VibrationPill
import com.rumbo.watchapp.presentation.components.doubleTapAnywhere
import com.rumbo.watchapp.presentation.format.describeDistance
import com.rumbo.watchapp.presentation.format.formatDistance
import com.rumbo.watchapp.presentation.format.formatMinutes
import com.rumbo.watchapp.presentation.format.remainingWalkingMinutes
import com.rumbo.watchapp.presentation.theme.ArrivedLight
import com.rumbo.watchapp.presentation.theme.BackgroundGlow
import com.rumbo.watchapp.presentation.theme.BackgroundGlowArrived
import com.rumbo.watchapp.presentation.theme.BackgroundGlowOffRoute
import com.rumbo.watchapp.presentation.theme.OnColor
import com.rumbo.watchapp.presentation.theme.Poppins
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusOnRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.theme.TextSecondary
import com.rumbo.watchapp.presentation.theme.TickMajor
import com.rumbo.watchapp.presentation.theme.TickMajorNoSignal
import com.rumbo.watchapp.presentation.theme.TickMinor
import com.rumbo.watchapp.presentation.theme.TickMinorNoSignal
import com.rumbo.watchapp.presentation.theme.TickMinorOffRoute

// Desde este largo la indicación baja de 20 a 18 sp.
private const val LONG_INSTRUCTION = 16

// Más líneas que esto no caben en el círculo del contenido: la indicación baja a 16 sp.
private const val MAX_INSTRUCTION_LINES = 3

/** Cómo se ve cada estado: nombre, color, ícono y la frase principal. Nunca solo el color. */
internal data class StatusLook(val label: String, val color: Color, val icon: StateIconKind, val headline: String)

internal fun lookOf(state: TrackingState): StatusLook {
    val instruction = state.nextInstruction ?: "Sigue la ruta"
    val maneuver = maneuverIcon(state.nextManeuver)
    // Un paso desconocido no es un giro: no se anuncia como tal, también en lo que lee TalkBack.
    val notATurn = state.nextManeuver == Maneuver.UNKNOWN
    return when (state.status) {
        NavigationStatus.ON_ROUTE -> StatusLook("En ruta", StatusOnRoute, maneuver, instruction)
        NavigationStatus.TURN_APPROACHING ->
            StatusLook(if (notATurn) "Atención" else "Giro próximo", StatusTurnSoon, maneuver, instruction)
        NavigationStatus.MANEUVER ->
            StatusLook(if (notATurn) "Atención" else "Gira ahora", StatusTurnSoon, maneuver, instruction)
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
    Maneuver.UNKNOWN -> StateIconKind.WARNING
    Maneuver.STRAIGHT, Maneuver.ARRIVE, null -> StateIconKind.STRAIGHT
}

/**
 * El dial y el fondo de cada estado: el anillo se llena hacia el giro (en ruta y en el giro), va
 * a trazos fuera de ruta, punteado sin señal y lleno al llegar. Desviado y Llegada tiñen la luz
 * del fondo. El ícono de la insignia repite el estado, que siempre va escrito.
 */
private data class DialLook(
    val ring: RingStyle,
    val glow: Color,
    val tickProgress: Float,
    val majorTick: Color,
    val minorTick: Color,
    val pillIcon: StateIconKind,
)

private fun dialLookOf(state: TrackingState, look: StatusLook): DialLook = when (state.status) {
    NavigationStatus.ON_ROUTE ->
        DialLook(RingStyle.PROGRESS, BackgroundGlow, state.stepProgress, TickMajor, TickMinor, StateIconKind.STRAIGHT)
    NavigationStatus.TURN_APPROACHING, NavigationStatus.MANEUVER -> DialLook(
        RingStyle.PROGRESS, BackgroundGlow, state.stepProgress, TickMajor, TickMinor,
        if (look.icon == StateIconKind.WARNING) StateIconKind.WARNING else StateIconKind.TURN_SOON,
    )
    NavigationStatus.OFF_ROUTE ->
        DialLook(RingStyle.DASHED, BackgroundGlowOffRoute, 0f, look.color.copy(alpha = 0.9f), TickMinorOffRoute, StateIconKind.WARNING)
    NavigationStatus.NO_SIGNAL ->
        DialLook(RingStyle.DOTTED, BackgroundGlow, 0f, TickMajorNoSignal, TickMinorNoSignal, StateIconKind.NO_SIGNAL)
    NavigationStatus.ARRIVED ->
        DialLook(RingStyle.FULL, BackgroundGlowArrived, 1f, TickMajor, TickMinor, StateIconKind.ARRIVED)
}

/**
 * Pantalla "En ruta"; el mapa va aparte, tras "Ver mapa". Arriba lo que hay que hacer (estado,
 * disco con la maniobra, distancia e indicación); debajo lo que falta, la vibración y los botones.
 * El anillo y el bisel muestran el avance hacia el siguiente giro. Un doble toque en cualquier
 * parte repite la indicación.
 */
@Composable
fun NavigationScreen(
    state: TrackingState,
    confirmFinish: Boolean,
    onRepeat: () -> Unit,
    onFinish: () -> Unit,
    onKeepNavigating: () -> Unit,
    onAsk: () -> Unit = {},
    onMap: () -> Unit = {},
) {
    ScreenOnWhileNavigating(wakeKey = state.status to confirmFinish) {
        if (confirmFinish) {
            FinishRouteScreen(onFinish = onFinish, onKeepNavigating = onKeepNavigating)
        } else {
            RouteScreen(state, onRepeat = onRepeat, onAsk = onAsk, onMap = onMap, onFinish = onFinish)
        }
    }
}

@Composable
private fun RouteScreen(
    state: TrackingState,
    onRepeat: () -> Unit,
    onAsk: () -> Unit,
    onMap: () -> Unit,
    onFinish: () -> Unit,
) {
    val look = lookOf(state)
    val dial = dialLookOf(state, look)
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
        glow = dial.glow,
        resetScrollKey = state.status,
        dial = {
            Dial(
                color = look.color,
                style = dial.ring,
                progress = state.stepProgress,
                tickProgress = dial.tickProgress,
                majorColor = dial.majorTick,
                minorColor = dial.minorTick,
                contentDescription = if (arrived) "Ruta completa" else "Avance hacia el siguiente paso",
                modifier = repeatsOnActivate,
            )
        },
    ) {
        // TalkBack anuncia el estado y la indicación cuando cambian; las distancias no, para no
        // hablar en cada lectura del GPS.
        StatusPill(look.label, look.color, modifier = repeatsOnActivate, icon = dial.pillIcon, announce = true)
        if (arrived) {
            ArrivedContent(state, modifier = repeatsOnActivate)
            PillButton("Terminar", onClick = { gate.click(onFinish) }, style = PillStyle.ARRIVED)
        } else {
            ManeuverRow(state, look, modifier = repeatsOnActivate)
            Instruction(look.headline, modifier = repeatsOnActivate)
            if (state.recalculating) SecondaryLine("Recalculando ruta...")
            Remaining(state, look.color, onRepeat)
        }
        if (state.destinationSaved) {
            Text(
                text = "Guardado ✓",
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "Destino guardado"
                    repeatOnActivate(onRepeat)
                },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        VibrationPill(pattern = state.pattern, color = look.color)
        RouteActions(arrived, onRepeat = { gate.click(onRepeat) }, onAsk = { gate.click(onAsk) },
            onMap = { gate.click(onMap) }, onFinish = { gate.click(onFinish) })
    }
}

/** Disco con la maniobra y, en el giro, la distancia grande al lado ("50 m"). */
@Composable
private fun ManeuverRow(state: TrackingState, look: StatusLook, modifier: Modifier = Modifier) {
    val turning = state.status == NavigationStatus.TURN_APPROACHING || state.status == NavigationStatus.MANEUVER
    val toNext = state.distanceToNextStepM?.takeIf { turning }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassDisc(
            size = if (toNext != null) 56.dp else 60.dp,
            halo = look.color,
            rings = state.status == NavigationStatus.NO_SIGNAL,
        ) {
            StateIcon(look.icon, look.color, modifier = modifier, size = 34.dp)
        }
        if (toNext != null) HeroDistance(toNext, modifier = modifier)
    }
}

/** "50 m" en grande: el número en Poppins 700 y la unidad más chica y gris. */
@Composable
private fun HeroDistance(meters: Double, modifier: Modifier = Modifier) {
    val text = formatDistance(meters)
    val number = text.substringBeforeLast(' ')
    val unit = text.substringAfterLast(' ')
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "En ${describeDistance(meters)}"
        },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp, lineHeight = 40.sp),
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Poppins, fontSize = 18.sp),
            color = TextSecondary,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/** La indicación: 20 sp, 18 si es larga y 16 si no cabe en tres líneas (medido, no contado). */
@Composable
private fun Instruction(headline: String, modifier: Modifier = Modifier) {
    var compact by remember(headline) { mutableStateOf(false) }
    Text(
        text = headline,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = when {
            compact -> MaterialTheme.typography.titleSmall
            headline.length > LONG_INSTRUCTION -> MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp)
            else -> MaterialTheme.typography.titleMedium
        },
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout -> if (!compact && layout.lineCount > MAX_INSTRUCTION_LINES) compact = true },
    )
}

@Composable
private fun SecondaryLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Llegada: disco verde con el check, "¡Llegaste!", el destino y lo que midió la ruta. */
@Composable
private fun ArrivedContent(state: TrackingState, modifier: Modifier = Modifier) {
    GlassDisc(size = 60.dp, halo = StatusArrived, fill = DiscFill(ArrivedLight, StatusArrived)) {
        StateIcon(StateIconKind.ARRIVED, OnColor, size = 34.dp, contentDescription = null)
    }
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Llegaste a ${state.destinationName}"
            liveRegion = LiveRegionMode.Polite
            heading()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("¡Llegaste!", style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp), textAlign = TextAlign.Center)
        Text(
            text = state.destinationName,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 22.sp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        state.route?.let { route ->
            Text(
                text = "Ruta de ${formatDistance(route.distanceM)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** "En 320 m" (en ruta, hasta el siguiente paso) y "Faltan 1,1 km · 18 min" (hasta el destino). */
@Composable
private fun Remaining(state: TrackingState, color: Color, onRepeat: () -> Unit) {
    val toNext = state.distanceToNextStepM?.takeIf { state.status == NavigationStatus.ON_ROUTE }
    val minutes = state.route?.let { remainingWalkingMinutes(state.remainingM, it.distanceM, it.durationS) }
    val remaining = listOfNotNull("Faltan ${formatDistance(state.remainingM)}", minutes?.let(::formatMinutes))
    val spoken = listOfNotNull(
        toNext?.let { "En ${describeDistance(it)}" },
        "Faltan ${describeDistance(state.remainingM)}" + (minutes?.let { ", $it minutos a pie" } ?: ""),
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
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
                color = color,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = remaining.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Botones redondos con su nombre debajo, de a dos por fila. Al llegar, "Terminar" va arriba. */
@Composable
private fun RouteActions(arrived: Boolean, onRepeat: () -> Unit, onAsk: () -> Unit, onMap: () -> Unit, onFinish: () -> Unit) {
    val spacing = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    Row(horizontalArrangement = spacing) {
        RoundAction(StateIconKind.REPEAT, "Repetir", onRepeat, style = RoundActionStyle.PRIMARY)
        // Preguntar algo sin dejar la ruta: las vibraciones y la voz de la ruta siguen.
        RoundAction(StateIconKind.MIC, "Preguntar", onAsk, style = RoundActionStyle.ASSISTANT)
    }
    Row(horizontalArrangement = spacing) {
        RoundAction(StateIconKind.MAP, "Ver mapa", onMap)
        if (!arrived) RoundAction(StateIconKind.CLOSE, "Terminar", onFinish, style = RoundActionStyle.DANGER)
    }
}

private fun SemanticsPropertyReceiver.repeatOnActivate(onRepeat: () -> Unit) {
    onClick(label = "repetir la indicación") {
        onRepeat()
        true
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
