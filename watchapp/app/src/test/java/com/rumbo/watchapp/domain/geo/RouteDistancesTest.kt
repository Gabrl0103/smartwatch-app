package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.testing.at
import com.rumbo.watchapp.testing.lRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDistancesTest {

    @Test
    fun cumulativeStartsAtZeroAndGrows() {
        val cumulative = cumulativeDistances(lRoute().polyline)
        assertEquals(0.0, cumulative.first(), 1e-9)
        assertEquals(400.0, cumulative.last(), 0.2)
        assertTrue(cumulative.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun stepAlongDistancesOfLRoute() {
        val route = lRoute()
        val along = stepAlongDistances(route.steps, route.polyline)
        assertEquals(0.0, along[0], 0.1)
        assertEquals(200.0, along[1], 0.1)
        assertEquals(400.0, along[2], 0.2)
    }

    @Test
    fun stepThatRepeatsAPointIsSearchedForward() {
        // Ida y vuelta: el destino coincide con el inicio y no debe quedar en 0 m.
        val polyline = listOf(at(0.0, 0.0), at(0.0, 50.0), at(0.0, 100.0), at(0.0, 50.0), at(0.0, 0.0))
        fun step(order: Int, maneuver: Maneuver, north: Double) =
            at(0.0, north).let { RouteStep(order, maneuver, "", 0.0, it.lat, it.lng) }
        val steps = listOf(step(1, Maneuver.STRAIGHT, 0.0), step(2, Maneuver.LEFT, 100.0), step(3, Maneuver.ARRIVE, 0.0))

        val along = stepAlongDistances(steps, polyline)

        assertEquals(0.0, along[0], 0.1)
        assertEquals(100.0, along[1], 0.1)
        assertEquals(200.0, along[2], 0.1)
    }
}
