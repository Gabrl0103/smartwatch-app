package com.rumbo.watchapp.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentDistanceTest {

    private val a = Point2(0.0, 0.0)
    private val b = Point2(10.0, 0.0)

    @Test
    fun perpendicularDistanceInsideSegment() {
        val hit = projectOntoSegment(Point2(5.0, 5.0), a, b)
        assertEquals(5.0, hit.distanceM, 1e-9)
        assertEquals(0.5, hit.fraction, 1e-9)
        assertEquals(5.0, hit.point.x, 1e-9)
    }

    @Test
    fun clampsToEndpoints() {
        val beyond = projectOntoSegment(Point2(15.0, 0.0), a, b)
        assertEquals(1.0, beyond.fraction, 1e-9)
        assertEquals(5.0, beyond.distanceM, 1e-9)

        val before = projectOntoSegment(Point2(-3.0, 4.0), a, b)
        assertEquals(0.0, before.fraction, 1e-9)
        assertEquals(5.0, before.distanceM, 1e-9)
    }

    @Test
    fun degenerateSegmentIsAPoint() {
        val hit = projectOntoSegment(Point2(3.0, 4.0), a, a)
        assertEquals(5.0, hit.distanceM, 1e-9)
    }
}
