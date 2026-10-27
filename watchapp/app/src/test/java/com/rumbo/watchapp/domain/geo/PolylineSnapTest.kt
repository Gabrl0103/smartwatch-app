package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.testing.at
import com.rumbo.watchapp.testing.lRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineSnapTest {

    private val polyline = lRoute().polyline

    @Test
    fun snapsToNearestSegmentWithDistanceAndAlong() {
        val snap = snapToPolyline(at(3.0, 125.0), polyline)
        assertEquals(12, snap.segmentIndex)
        assertEquals(3.0, snap.distanceToRouteM, 0.05)
        assertEquals(125.0, snap.alongM, 0.1)
        assertEquals(0.0, haversineM(snap.point, at(0.0, 125.0)), 0.05)
    }

    @Test
    fun snapsOnSecondLegOfTheRoute() {
        val snap = snapToPolyline(at(100.0, 197.0), polyline)
        assertEquals(3.0, snap.distanceToRouteM, 0.05)
        assertEquals(300.0, snap.alongM, 0.1)
    }

    @Test
    fun windowLimitsTheSegmentsConsidered() {
        val snap = snapToPolyline(at(0.0, 150.0), polyline, toAlongM = 100.0)
        assertEquals(100.0, snap.alongM, 0.1)
        assertEquals(50.0, snap.distanceToRouteM, 0.1)
    }

    @Test
    fun singlePointPolyline() {
        val snap = snapToPolyline(at(3.0, 4.0), listOf(at(0.0, 0.0)))
        assertEquals(5.0, snap.distanceToRouteM, 0.05)
        assertEquals(0.0, snap.alongM, 1e-9)
    }
}
