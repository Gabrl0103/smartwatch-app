package com.rumbo.watchapp.application.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.transform

/** Cada cuánto se redibuja el mapa, como mucho. */
const val MAP_REDRAW_MS = 1_000L

/**
 * Los estados que el mapa tiene que dibujar: solo si cambió algo que se ve (la vibración no se ve) y
 * como mucho uno cada [periodMs]. Entre dos cuadros se queda con el último, para no mostrar una
 * posición vieja. Es un flujo frío: mientras nadie lo recoge, fuera de la pantalla Mapa, no gasta.
 */
fun Flow<TrackingState>.mapUpdates(periodMs: Long = MAP_REDRAW_MS): Flow<TrackingState> =
    distinctUntilChangedBy { it.copy(pattern = null) }
        .conflate()
        .transform {
            emit(it)
            delay(periodMs)
        }
