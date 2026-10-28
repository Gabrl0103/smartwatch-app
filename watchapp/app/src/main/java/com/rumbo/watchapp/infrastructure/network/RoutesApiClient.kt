package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Place
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.port.RoutesRepository
import org.json.JSONObject

/** Pide la ruta a pie con POST /routes. */
object RoutesApiClient : RoutesRepository {

    override suspend fun create(origin: Coordinates, destination: Place): Route {
        val request = JSONObject()
            .put("origin", point(origin.lat, origin.lng))
            .put("destination", point(destination.lat, destination.lng))
            .put("destination_name", destination.name)
        val body = backendPostJson("/routes", request).requireOk()
        return parseJson(body, ::parseRoute)
    }

    private fun point(lat: Double, lng: Double) = JSONObject().put("lat", lat).put("lng", lng)

    private fun parseRoute(json: JSONObject): Route {
        val steps = json.getJSONArray("steps")
        val polyline = json.getJSONArray("polyline")
        return Route(
            destinationName = json.getString("destination_name"),
            distanceM = json.getDouble("distance_m"),
            durationS = json.getDouble("duration_s"),
            steps = List(steps.length()) { i ->
                val step = steps.getJSONObject(i)
                RouteStep(
                    order = step.getInt("order"),
                    maneuver = toManeuver(step.getString("maneuver")),
                    instruction = step.getString("instruction"),
                    distanceM = step.getDouble("distance_m"),
                    lat = step.getDouble("lat"),
                    lng = step.getDouble("lng"),
                )
            },
            polyline = List(polyline.length()) { i ->
                val pair = polyline.getJSONArray(i)
                Coordinates(lat = pair.getDouble(0), lng = pair.getDouble(1))
            },
            // null si el backend no pudo guardar la ruta; se navega igual.
            id = if (json.isNull("route_id")) null else json.getLong("route_id"),
        )
    }

    private fun toManeuver(value: String): Maneuver = when (value) {
        "left" -> Maneuver.LEFT
        "right" -> Maneuver.RIGHT
        "arrive" -> Maneuver.ARRIVE
        else -> Maneuver.STRAIGHT
    }
}
