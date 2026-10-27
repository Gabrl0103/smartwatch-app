package com.rumbo.watchapp.domain.port

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Place

interface PlacesRepository {
    /** Busca un lugar por nombre cerca de [near]; null si no existe. */
    suspend fun search(query: String, near: Coordinates): Place?
}
