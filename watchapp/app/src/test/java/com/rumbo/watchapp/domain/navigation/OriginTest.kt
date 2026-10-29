package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.error.TooFarError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginTest {

    private fun fix(accuracyM: Double, timeMs: Long) = LocationFix(1.2146, -77.2783, accuracyM, timeMs)

    @Test
    fun withoutReadingsThereIsNoOrigin() {
        assertNull(originFrom(null, nowMs = 0))
    }

    @Test
    fun usesRecentAndPreciseReading() {
        assertEquals(Coordinates(1.2146, -77.2783), originFrom(fix(10.0, 1_000), nowMs = 20_000))
    }

    @Test
    fun ignoresReadingOlderThanThirtySeconds() {
        assertNull(originFrom(fix(10.0, 1_000), nowMs = 31_000))
    }

    @Test
    fun ignoresImpreciseReading() {
        assertNull(originFrom(fix(60.0, 1_000), nowMs = 2_000))
    }

    @Test
    fun destinationInTheSameCityIsWalkable() {
        // Centro de Pasto → Parque Bolívar, unos 1,5 km en línea recta.
        requireWalkable(Coordinates(1.2136, -77.2811), Coordinates(1.2056, -77.2703))
    }

    @Test(expected = TooFarError::class)
    fun destinationInAnotherCityIsTooFar() {
        // Pasto → Bogotá, unos 500 km.
        requireWalkable(Coordinates(1.2136, -77.2811), Coordinates(4.7110, -74.0721))
    }
}
