package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ruta en el formato JSON de POST /routes. El mismo formato sirve para guardar la ruta en curso
 * en el reloj ([routeToJson]) y leerla al continuar ([routeFromJson]).
 */
internal fun routeFromJson(json: JSONObject): Route {
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
                street = step.optString("street"),
                roundaboutExit = if (step.isNull("roundabout_exit")) null else step.getInt("roundabout_exit"),
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

internal fun routeToJson(route: Route): JSONObject = JSONObject()
    .put("route_id", route.id ?: JSONObject.NULL)
    .put("destination_name", route.destinationName)
    .put("distance_m", route.distanceM)
    .put("duration_s", route.durationS)
    .put(
        "steps",
        JSONArray(
            route.steps.map { step ->
                JSONObject()
                    .put("order", step.order)
                    .put("maneuver", step.maneuver.name.lowercase())
                    .put("instruction", step.instruction)
                    .put("distance_m", step.distanceM)
                    .put("lat", step.lat)
                    .put("lng", step.lng)
                    .put("street", step.street)
                    .put("roundabout_exit", step.roundaboutExit ?: JSONObject.NULL)
            },
        ),
    )
    .put("polyline", JSONArray(route.polyline.map { JSONArray().put(it.lat).put(it.lng) }))

private fun toManeuver(value: String): Maneuver =
    Maneuver.entries.firstOrNull { it.name.lowercase() == value } ?: Maneuver.STRAIGHT
