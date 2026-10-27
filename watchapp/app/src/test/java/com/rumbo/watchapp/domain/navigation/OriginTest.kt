package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginTest {

    private fun fix(accuracyM: Double, timeMs: Long) = LocationFix(1.2146, -77.2783, accuracyM, timeMs)

    @Test
    fun withoutReadingsUsesPastoCenter() {
        assertEquals(ORIGIN, originFrom(null, nowMs = 0))
    }

    @Test
    fun usesRecentAndPreciseReading() {
        assertEquals(Coordinates(1.2146, -77.2783), originFrom(fix(10.0, 1_000), nowMs = 20_000))
    }

    @Test
    fun ignoresReadingOlderThanThirtySeconds() {
        assertEquals(ORIGIN, originFrom(fix(10.0, 1_000), nowMs = 31_000))
    }

    @Test
    fun ignoresImpreciseReading() {
        assertEquals(ORIGIN, originFrom(fix(60.0, 1_000), nowMs = 2_000))
    }
}
