package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.domain.model.Side
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteJsonTest {

    private fun stepJson(extra: String) = JSONObject(
        """{"route_id": null, "destination_name": "Parque", "distance_m": 10, "duration_s": 8, "polyline": [],
            "steps": [{"order": 1, "instruction": "Texto", "distance_m": 10, "lat": 1.2, "lng": -77.2, $extra}]}""",
    )

    @Test
    fun readsEveryManeuverTheBackendSends() {
        val values = mapOf(
            "left" to Maneuver.LEFT,
            "right" to Maneuver.RIGHT,
            "slight_left" to Maneuver.SLIGHT_LEFT,
            "slight_right" to Maneuver.SLIGHT_RIGHT,
            "sharp_left" to Maneuver.SHARP_LEFT,
            "sharp_right" to Maneuver.SHARP_RIGHT,
            "u_turn_left" to Maneuver.U_TURN_LEFT,
            "u_turn_right" to Maneuver.U_TURN_RIGHT,
            "roundabout" to Maneuver.ROUNDABOUT,
            "unknown" to Maneuver.UNKNOWN,
            "straight" to Maneuver.STRAIGHT,
            "arrive" to Maneuver.ARRIVE,
        )

        values.forEach { (value, maneuver) ->
            assertEquals(maneuver, routeFromJson(stepJson(""""maneuver": "$value"""")).steps.single().maneuver)
        }
        assertEquals(Maneuver.entries.size, values.size)
    }

    @Test
    fun readsStreetAndRoundaboutExit() {
        val step = routeFromJson(stepJson(""""maneuver": "roundabout", "street": "Calle 18", "roundabout_exit": 2""")).steps.single()

        assertEquals("Calle 18", step.street)
        assertEquals(2, step.roundaboutExit)
    }

    @Test
    fun readsTheJoinedTurn() {
        val step = routeFromJson(stepJson(""""maneuver": "right", "then_maneuver": "left"""")).steps.single()

        assertEquals(Maneuver.LEFT, step.thenManeuver)
    }

    @Test
    fun readsTheSideOfTheDestination() {
        assertEquals(Side.LEFT, routeFromJson(stepJson(""""maneuver": "arrive", "destination_side": "left"""")).steps.single().destinationSide)
        assertEquals(Side.RIGHT, routeFromJson(stepJson(""""maneuver": "arrive", "destination_side": "right"""")).steps.single().destinationSide)
        assertNull(routeFromJson(stepJson(""""maneuver": "arrive", "destination_side": """"")).steps.single().destinationSide)
    }

    @Test
    fun aRouteFromAnOlderBackendStillLoads() {
        val step = routeFromJson(stepJson(""""maneuver": "left"""")).steps.single()

        assertEquals("", step.street)
        assertNull(step.roundaboutExit)
        assertNull(step.thenManeuver)
        assertNull(step.destinationSide)
    }

    @Test
    fun aManeuverThisWatchDoesNotKnowIsNeverTakenForStraight() {
        assertEquals(Maneuver.UNKNOWN, routeFromJson(stepJson(""""maneuver": "teleport"""")).steps.single().maneuver)
    }

    @Test
    fun theSavedRouteComesBackIdentical() {
        val route = Route(
            destinationName = "Parque",
            distanceM = 100.0,
            durationS = 80.0,
            steps = listOf(
                RouteStep(1, Maneuver.ROUNDABOUT, "Tome la salida.", 50.0, 1.2, -77.2, "Glorieta", roundaboutExit = 3),
                RouteStep(2, Maneuver.U_TURN_RIGHT, "Dé la vuelta.", 50.0, 1.3, -77.3, "Calle 18", thenManeuver = Maneuver.LEFT),
                RouteStep(3, Maneuver.ARRIVE, "Ha llegado.", 0.0, 1.4, -77.4, destinationSide = Side.RIGHT),
            ),
            polyline = listOf(Coordinates(1.2, -77.2), Coordinates(1.4, -77.4)),
            id = 7,
        )

        assertEquals(route, routeFromJson(JSONObject(routeToJson(route).toString())))
    }
}
