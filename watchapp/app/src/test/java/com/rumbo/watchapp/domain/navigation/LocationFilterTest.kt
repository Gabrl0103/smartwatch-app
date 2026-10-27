package com.rumbo.watchapp.domain.navigation

import com.rumbo.watchapp.domain.geo.haversineM
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.testing.at
import com.rumbo.watchapp.testing.fixAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocationFilterTest {

    private val filter = LocationFilter()

    @Test
    fun discardsInaccurateReadings() {
        assertNull(filter.accept(fixAt(0.0, 0.0, 1_000, accuracyM = 30.0), nowMs = 1_000))
    }

    @Test
    fun discardsStaleReadings() {
        assertNull(filter.accept(fixAt(0.0, 0.0, 1_000), nowMs = 7_000))
    }

    @Test
    fun discardsReadingsOlderThanThePreviousOne() {
        filter.accept(fixAt(0.0, 0.0, 2_000), nowMs = 2_000)
        assertNull(filter.accept(fixAt(0.0, 5.0, 1_000), nowMs = 2_000))
    }

    @Test
    fun medianOfLastThreeRejectsASingleJump() {
        filter.accept(fixAt(0.0, 0.0, 1_000), 1_000)
        filter.accept(fixAt(0.0, 50.0, 2_000), 2_000)
        val smoothed = filter.accept(fixAt(0.0, 2.0, 3_000), 3_000)

        assertNotNull(smoothed)
        val point = Coordinates(smoothed!!.lat, smoothed.lng)
        assertEquals(0.0, haversineM(point, at(0.0, 2.0)), 0.05)
    }

    @Test
    fun computesSpeedAndBearingWhenProviderDoesNot() {
        filter.accept(fixAt(0.0, 0.0, 1_000, speedMps = null), 1_000)
        val second = filter.accept(fixAt(10.0, 0.0, 3_000, speedMps = null), 3_000)!!

        assertEquals(5.0, second.speedMps!!, 0.05)
        assertEquals(90.0, second.bearingDeg!!, 0.5)
    }

    @Test
    fun keepsProviderSpeedAndHasNoneForFirstReading() {
        val first = filter.accept(fixAt(0.0, 0.0, 1_000, speedMps = null), 1_000)!!
        assertNull(first.speedMps)

        val second = filter.accept(fixAt(10.0, 0.0, 3_000, speedMps = 1.2), 3_000)!!
        assertEquals(1.2, second.speedMps!!, 1e-9)
    }
}
