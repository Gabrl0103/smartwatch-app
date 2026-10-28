package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.navigation.ORIGIN
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SavedPlacesRepository

/**
 * Lugares guardados: elegir destino sin hablar, guardar el destino confirmado y borrar.
 * [origin] da la ubicación actual (GPS reciente o, si no hay, el centro de Pasto).
 */
class ManageSavedPlaces(
    private val savedPlaces: SavedPlacesRepository,
    private val routes: RoutesRepository,
    private val origin: () -> Coordinates = { ORIGIN },
) {
    suspend fun list(): List<SavedPlace> = savedPlaces.list()

    /** Ruta a pie desde la ubicación actual hasta el lugar guardado. */
    suspend fun planRoute(place: SavedPlace): Route = routes.create(origin(), place.toPlace())

    /** Guarda el destino de [route] con su nombre, en el punto donde termina la ruta. */
    suspend fun saveDestination(route: Route): SavedPlace {
        val end = route.end
        return savedPlaces.add(route.destinationName, end.lat, end.lng)
    }

    suspend fun delete(place: SavedPlace) = savedPlaces.delete(place.id)
}
