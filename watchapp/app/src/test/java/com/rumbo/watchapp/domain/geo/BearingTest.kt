package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.testing.at
import org.junit.Assert.assertEquals
import org.junit.Test

class BearingTest {

    private val origin = at(0.0, 0.0)

    @Test
    fun cardinalDirections() {
        assertEquals(0.0, bearingDeg(origin, at(0.0, 100.0)), 0.01)
        assertEquals(90.0, bearingDeg(origin, at(100.0, 0.0)), 0.01)
        assertEquals(180.0, bearingDeg(origin, at(0.0, -100.0)), 0.01)
        assertEquals(270.0, bearingDeg(origin, at(-100.0, 0.0)), 0.01)
    }
}
