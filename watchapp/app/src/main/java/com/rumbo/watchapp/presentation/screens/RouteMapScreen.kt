package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.map.MapZoom
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.RouteMapCanvas
import com.rumbo.watchapp.presentation.components.StateBadge
import com.rumbo.watchapp.presentation.format.describeDistance

// La etiqueta y los botones van juntos abajo, donde tapan lo ya recorrido y no lo que viene. Este es
// su alto con la separación que pone RumboScreen; el resto queda para el mapa, sin desplazar.
private val ControlsArea = 87.dp

// Bajan un poco más sin cambiar lo medido, para no chocar con el punto de la persona, que va al
// centro y puede ser del mismo color que la etiqueta. Así tampoco hay nada que desplazar.
private val ControlsDrop = 6.dp
private val ButtonsInset = 4.dp

// Lo que ocupa la hora: la ruta entera se dibuja debajo de ella.
private val TimeArea = 24.dp

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
 * Pantalla "Mapa": la ruta dibujada por [RouteMapCanvas] y, abajo, la etiqueta del estado, "Atrás"
 * y el botón que alterna la vista de cerca (la persona al centro, su rumbo arriba) y la de toda la
 * ruta (el norte arriba). Solo muestra [state]: la guía sigue por su cuenta.
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
                    topInset = TimeArea,
                    bottomInset = margin + ControlsArea,
                )
            },
        ) {
            Spacer(Modifier.height((screenHeight - margin * 2 - ControlsArea).coerceAtLeast(0.dp)))
            Column(
                modifier = Modifier.offset(y = ControlsDrop),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StateBadge(label, lookOf(state).color, announce = true, filled = true, compact = true)
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

@Composable
private fun ZoomButton(zoom: MapZoom, onChange: (MapZoom) -> Unit, modifier: Modifier = Modifier) {
    val nearby = zoom == MapZoom.NEARBY
    PillButton(
        label = if (nearby) "Ruta" else "Cerca",
        onClick = { onChange(if (nearby) MapZoom.WHOLE_ROUTE else MapZoom.NEARBY) },
        modifier = modifier.semantics { contentDescription = if (nearby) "Ver toda la ruta" else "Ver de cerca" },
    )
}
