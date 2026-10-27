package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.LocationFix
import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    /** Lecturas del GPS mientras se recolecta el flujo; termina vacío si no hay permiso. */
    fun locations(): Flow<LocationFix>
}
