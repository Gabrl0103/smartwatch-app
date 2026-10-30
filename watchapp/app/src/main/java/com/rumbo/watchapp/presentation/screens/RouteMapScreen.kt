package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.map.MapZoom
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.RouteMapCanvas
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.format.describeDistance

// Alto de la etiqueta y de los botones, con su separación: el mapa queda libre entre ellos.
private val BadgeArea = 38.dp
private val ButtonsArea = 60.dp

// El margen de arriba y abajo de RumboScreen en pantalla redonda.
private const val VERTICAL_MARGIN = 0.14f

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
 * Pantalla "Mapa": la ruta dibujada por [RouteMapCanvas], con la etiqueta del estado arriba y
 * abajo "Atrás" y el botón que alterna la vista de cerca (la persona al centro, su rumbo arriba)
 * y la de toda la ruta (el norte arriba). Solo muestra [state]: la guía sigue por su cuenta.
 */
@Composable
fun RouteMapScreen(state: TrackingState, onBack: () -> Unit, initialZoom: MapZoom = MapZoom.NEARBY) {
    var zoom by remember { mutableStateOf(initialZoom) }
    val label = mapLabel(state)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val margin = screenHeight * VERTICAL_MARGIN
    ScreenOnWhileNavigating(wakeKey = state.status) {
        RumboScreen(
            paneTitle = "Mapa",
            background = {
                RouteMapCanvas(
                    state = state,
                    zoom = zoom,
                    description = describeMap(state, label, zoom),
                    topInset = margin + BadgeArea,
                    bottomInset = margin + ButtonsArea,
                )
            },
        ) {
            StateBadge(label, lookOf(state).color, announce = true, filled = true)
            Spacer(Modifier.height(freeHeight(screenHeight, margin)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Atrás", onClick = onBack, modifier = Modifier.weight(1f))
                ZoomButton(zoom, onChange = { zoom = it }, modifier = Modifier.weight(1f))
            }
        }
    }
}

// Lo que sobra entre la etiqueta y los botones, sin obligar a desplazar la pantalla.
private fun freeHeight(screenHeight: Dp, margin: Dp) =
    (screenHeight - margin * 2 - BadgeArea - ButtonsArea).coerceAtLeast(0.dp)

@Composable
private fun ZoomButton(zoom: MapZoom, onChange: (MapZoom) -> Unit, modifier: Modifier = Modifier) {
    val nearby = zoom == MapZoom.NEARBY
    PillButton(
        label = if (nearby) "Ruta" else "Cerca",
        onClick = { onChange(if (nearby) MapZoom.WHOLE_ROUTE else MapZoom.NEARBY) },
        modifier = modifier.semantics { contentDescription = if (nearby) "Ver toda la ruta" else "Ver de cerca" },
    )
}
