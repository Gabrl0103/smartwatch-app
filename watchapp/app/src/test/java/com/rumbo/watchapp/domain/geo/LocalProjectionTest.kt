package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalProjectionTest {

    private val origin = Coordinates(1.2136, -77.2811)
    private val projection = LocalProjection(origin)

    @Test
    fun originIsZero() {
        val p = projection.toXY(origin)
        assertEquals(0.0, p.x, 1e-9)
        assertEquals(0.0, p.y, 1e-9)
    }

    @Test
    fun agreesWithHaversineAtWalkingScale() {
        val point = Coordinates(origin.lat + 0.003, origin.lng + 0.004)
        val p = projection.toXY(point)
        assertEquals(haversineM(origin, point), kotlin.math.hypot(p.x, p.y), 0.5)
    }

    @Test
    fun roundTripReturnsSamePoint() {
        val back = projection.toCoordinates(Point2(120.0, -80.0))
        val p = projection.toXY(back)
        assertEquals(120.0, p.x, 1e-6)
        assertEquals(-80.0, p.y, 1e-6)
    }
}
