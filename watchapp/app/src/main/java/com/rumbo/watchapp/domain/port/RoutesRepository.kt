package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route

interface RoutesRepository {
    /** Calcula la ruta a pie desde [origin] hasta [destination]. */
    suspend fun create(origin: Coordinates, destination: Place): Route
}
