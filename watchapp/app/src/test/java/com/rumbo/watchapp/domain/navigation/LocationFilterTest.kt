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
        // 3 m/s hacia el este; la velocidad sale de las posiciones ya suavizadas.
        listOf(0.0, 6.0, 12.0).forEachIndexed { i, east ->
            filter.accept(fixAt(east, 0.0, 1_000L + i * 2_000, speedMps = null), 1_000L + i * 2_000)
        }
        val fourth = filter.accept(fixAt(18.0, 0.0, 7_000, speedMps = null), 7_000)!!

        assertEquals(3.0, fourth.speedMps!!, 0.05)
        assertEquals(90.0, fourth.bearingDeg!!, 0.5)
    }

    @Test
    fun aSingleJumpDoesNotFakeASpeed() {
        // Caminando al norte a 1,4 m/s, una lectura salta 40 m al este y la siguiente vuelve.
        val readings = listOf(0.0 to 0.0, 0.0 to 1.4, 0.0 to 2.8, 40.0 to 4.2, 0.0 to 5.6, 0.0 to 7.0)
        val speeds = readings.mapIndexed { i, (east, north) ->
            val t = 1_000L * (i + 1)
            filter.accept(fixAt(east, north, t, speedMps = null), t)!!.speedMps
        }

        // Con las lecturas crudas serían ~40 m/s y el aviso de giro saldría cientos de metros antes.
        assertEquals(listOf(1.4, 1.4, 1.4), speeds.takeLast(3).map { it!! }.map { Math.round(it * 10) / 10.0 })
    }

    @Test
    fun impossibleWalkingSpeedIsDiscarded() {
        filter.accept(fixAt(0.0, 0.0, 1_000), 1_000)
        val fix = filter.accept(fixAt(0.0, 1.4, 2_000, speedMps = 40.0), 2_000)!!
        assertNull(fix.speedMps)
    }

    @Test
    fun keepsProviderSpeedAndHasNoneForFirstReading() {
        val first = filter.accept(fixAt(0.0, 0.0, 1_000, speedMps = null), 1_000)!!
        assertNull(first.speedMps)

        val second = filter.accept(fixAt(10.0, 0.0, 3_000, speedMps = 1.2), 3_000)!!
        assertEquals(1.2, second.speedMps!!, 1e-9)
    }
}
