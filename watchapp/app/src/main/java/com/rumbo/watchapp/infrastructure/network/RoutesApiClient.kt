package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.port.RoutesRepository
import org.json.JSONObject

private const val NO_ROUTE_MESSAGE = "No encontré una ruta a pie hasta ese lugar"

/** Pide la ruta a pie con POST /routes. */
object RoutesApiClient : RoutesRepository {

    override suspend fun create(origin: Coordinates, destination: Place): Route {
        val request = JSONObject()
            .put("origin", point(origin.lat, origin.lng))
            .put("destination", point(destination.lat, destination.lng))
            .put("destination_name", destination.name)
        val body = backendPostJson("/routes", request).requireOk()
        val route = parseJson(body, ::routeFromJson)
        // Sin puntos no hay nada que seguir: mejor un error claro que fallar al navegar.
        if (route.steps.isEmpty() && route.polyline.isEmpty()) throw AppError(NO_ROUTE_MESSAGE)
        return route
    }

    private fun point(lat: Double, lng: Double) = JSONObject().put("lat", lat).put("lng", lng)
}
