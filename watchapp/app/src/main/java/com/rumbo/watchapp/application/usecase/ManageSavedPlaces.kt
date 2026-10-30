package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.geo.haversineM
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.SavedPlace
import com.rumbo.watchapp.domain.navigation.requireWalkable
import com.rumbo.watchapp.domain.port.RoutesRepository
import com.rumbo.watchapp.domain.port.SavedPlacesRepository

const val SAVE_FAILED_MESSAGE = "No pude guardar el lugar"

// La ruta termina en la calle, no en el punto exacto del lugar: hasta aquí cuenta como el mismo.
private const val SAME_PLACE_M = 50.0

/**
 * Lugares guardados: elegir destino sin hablar, guardar el destino confirmado y borrar.
 * [origin] da la ubicación actual: espera al GPS si hace falta y falla si no hay señal.
 */
class ManageSavedPlaces(
    private val savedPlaces: SavedPlacesRepository,
    private val routes: RoutesRepository,
    private val origin: suspend () -> Coordinates,
) {
    suspend fun list(): List<SavedPlace> = savedPlaces.list()

    /** Ruta a pie desde la ubicación actual hasta el lugar guardado. */
    suspend fun planRoute(place: SavedPlace): Route {
        val from = origin()
        requireWalkable(from, Coordinates(place.lat, place.lng))
        return routes.create(from, place.toPlace())
    }

    /** Guarda el destino de [route] con su nombre, en el punto donde termina la ruta. */
    suspend fun saveDestination(route: Route): SavedPlace {
        val end = route.end
        return savedPlaces.add(route.destinationName, end.lat, end.lng)
    }

    suspend fun delete(place: SavedPlace) = savedPlaces.delete(place.id)

    /**
     * Si el destino de [route] ya está guardado, por nombre o por estar en el mismo punto. Sin red
     * cuenta como no guardado: esto solo informa y no debe impedir pedir la ruta.
     */
    suspend fun isSaved(route: Route): Boolean = try {
        savedPlaces.list().any { place ->
            place.name.equals(route.destinationName, ignoreCase = true) ||
                haversineM(Coordinates(place.lat, place.lng), route.end) <= SAME_PLACE_M
        }
    } catch (e: AppError) {
        false
    }
}
