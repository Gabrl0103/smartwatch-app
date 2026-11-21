package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.map.MapZoom
import com.rumbo.watchapp.domain.map.RouteGeometry
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.RouteMapCanvas
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.format.describeDistance
import com.rumbo.watchapp.presentation.format.formatDistance
import com.rumbo.watchapp.presentation.theme.Background
import com.rumbo.watchapp.presentation.theme.Surface
import com.rumbo.watchapp.presentation.theme.TextPrimary
import com.rumbo.watchapp.presentation.theme.TextSecondary

// Arriba la etiqueta del estado y, de cerca, la distancia al giro; abajo la atribución y los
// botones. Son sus altos aproximados: lo que queda entre ellos es para ver el mapa. Si sobra algo,
// RumboScreen centra; nada se desplaza.
private val BadgeArea = 24.dp
private val DistanceArea = 26.dp
private val AttributionArea = 14.dp
private val ButtonsArea = 52.dp
private val ItemGap = 8.dp
private val ButtonsInset = 4.dp

// El margen de arriba y abajo de RumboScreen en pantalla redonda.
private const val VERTICAL_MARGIN = 0.14f

/** Atribución que piden OpenStreetMap y el plan gratis de Geoapify, visible sobre el mapa con calles. */
const val MAP_ATTRIBUTION = "© OpenStreetMap · Geoapify"

/** Lo que dice la etiqueta. Sin señal va más corto que en En ruta: aquí cabe sobre el mapa. */
private fun mapLabel(state: TrackingState) =
    if (state.status == NavigationStatus.NO_SIGNAL) "Sin señal" else lookOf(state).label

// Quien no ve el dibujo recibe lo mismo en palabras.
private fun describeMap(state: TrackingState, label: String, zoom: MapZoom): String {
    val view = if (zoom == MapZoom.NEARBY) "Mapa de cerca" else "Mapa de toda la ruta"
    val arrived = state.status == NavigationStatus.ARRIVED
    return listOfNotNull(
        view,
        label,
        state.nextInstruction?.trimEnd('.')?.takeUnless { arrived },
        "Faltan ${describeDistance(state.remainingM)}".takeUnless { arrived },
    ).joinToString(". ")
}

/**
 * "Giro en 40 m" y lo mismo en palabras para TalkBack; sin giros por delante, lo que falta al
 * destino. Null al llegar.
 */
internal fun turnDistance(nextTurnM: Double?, state: TrackingState): Pair<String, String>? = when {
    state.status == NavigationStatus.ARRIVED -> null
    nextTurnM != null -> "Giro en ${formatDistance(nextTurnM)}" to "Próximo giro a ${describeDistance(nextTurnM)}"
    else -> "Destino en ${formatDistance(state.remainingM)}" to "Destino a ${describeDistance(state.remainingM)}"
}

/**
 * Pantalla "Mapa": de cerca, un mapa tipo GPS con calles, el rumbo arriba y la distancia al próximo
 * giro; o la ruta entera (el norte arriba). Arriba la etiqueta del estado; abajo "Atrás" y el botón
 * que alterna las vistas. Solo muestra [state]: la guía sigue por su cuenta y no espera al mapa.
 */
@Composable
fun RouteMapScreen(state: TrackingState, onBack: () -> Unit, initialZoom: MapZoom = MapZoom.NEARBY) {
    var zoom by remember { mutableStateOf(initialZoom) }
    val nearby = zoom == MapZoom.NEARBY
    val label = mapLabel(state)
    val geometry = remember(state.route) { state.route?.let(::RouteGeometry) }
    val distance = if (nearby) turnDistance(geometry?.nextTurnAhead(state.progressM)?.first, state) else null
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val margin = screenHeight * VERTICAL_MARGIN
    val top = BadgeArea + if (distance != null) DistanceArea else 0.dp
    val bottom = ButtonsArea + if (nearby) AttributionArea else 0.dp
    ScreenOnWhileNavigating(wakeKey = state.status) {
        RumboScreen(
            paneTitle = "Mapa",
            background = {
                RouteMapCanvas(
                    state = state,
                    geometry = geometry,
                    zoom = zoom,
                    description = describeMap(state, label, zoom),
                    topInset = margin + top,
                    bottomInset = margin + bottom,
                )
            },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StateBadge(label, lookOf(state).color, announce = true, filled = true, compact = true)
                distance?.let { (text, spoken) -> DistanceChip(text, spoken) }
            }
            Spacer(Modifier.height(freeHeight(screenHeight, margin, top, bottom)))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (nearby) Attribution()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ButtonsInset),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton("Atrás", onClick = onBack, modifier = Modifier.weight(1f))
                    ZoomButton(zoom, onChange = { zoom = it }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun freeHeight(screen: Dp, margin: Dp, top: Dp, bottom: Dp) =
    (screen - margin * 2 - top - bottom - ItemGap * 2).coerceAtLeast(0.dp)

@Composable
private fun DistanceChip(text: String, spoken: String) {
    Text(
        text = text,
        modifier = Modifier
            .background(Surface, CircleShape)
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        style = MaterialTheme.typography.labelMedium,
        color = TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

// Pequeña pero legible: gris secundario sobre el fondo de la app (más de 4,5:1), no sobre las calles.
@Composable
private fun Attribution() {
    Text(
        text = MAP_ATTRIBUTION,
        modifier = Modifier
            .background(Background.copy(alpha = 0.85f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        color = TextSecondary,
        maxLines = 1,
    )
}

@Composable
private fun ZoomButton(zoom: MapZoom, onChange: (MapZoom) -> Unit, modifier: Modifier = Modifier) {
    val nearby = zoom == MapZoom.NEARBY
    PillButton(
        label = if (nearby) "Ruta" else "Cerca",
        onClick = { onChange(if (nearby) MapZoom.WHOLE_ROUTE else MapZoom.NEARBY) },
        modifier = modifier.semantics { contentDescription = if (nearby) "Ver toda la ruta" else "Ver de cerca" },
    )
}
