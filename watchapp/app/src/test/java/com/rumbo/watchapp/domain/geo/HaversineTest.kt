package com.rumbo.watchapp.domain.geo

import com.rumbo.watchapp.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineTest {

    private val pasto = Coordinates(1.2136, -77.2811)

    @Test
    fun samePointIsZero() {
        assertEquals(0.0, haversineM(pasto, pasto), 1e-9)
    }

    @Test
    fun hundredthOfDegreeOfLatitudeIsAboutOneKilometer() {
        val north = Coordinates(pasto.lat + 0.01, pasto.lng)
        assertEquals(1111.95, haversineM(pasto, north), 0.5)
    }

    @Test
    fun isSymmetric() {
        val other = Coordinates(1.2056, -77.2703)
        assertEquals(haversineM(pasto, other), haversineM(other, pasto), 1e-9)
    }
}
