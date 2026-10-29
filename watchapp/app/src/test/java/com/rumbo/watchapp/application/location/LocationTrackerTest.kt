package com.rumbo.watchapp.application.location

import com.rumbo.watchapp.domain.error.GpsUnavailableError
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.LocationFix
import com.rumbo.watchapp.domain.port.LocationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Buscando señal GPS": la ruta solo se pide con una lectura reciente y precisa. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationTrackerTest {

    private class FakeGps : LocationProvider {
        val fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 8)
        var subscriptions = 0

        override fun locations() = fixes.also { subscriptions++ }
    }

    private val here = Coordinates(1.2146, -77.2783)

    private fun fix(accuracyM: Double = 5.0) = LocationFix(here.lat, here.lng, accuracyM, timeMs = 0)

    @Test
    fun withARecentPreciseFixReturnsItRightAway() = runTest(UnconfinedTestDispatcher()) {
        val gps = FakeGps()
        val tracker = LocationTracker(gps, { 0L }, backgroundScope)
        tracker.start()
        gps.fixes.emit(fix())

        assertEquals(here, tracker.awaitOrigin())
        assertFalse(tracker.searching.value)
    }

    @Test
    fun withoutAFixTurnsOnTheGpsAndWaitsShowingSearching() = runTest(UnconfinedTestDispatcher()) {
        val gps = FakeGps()
        val tracker = LocationTracker(gps, { 0L }, backgroundScope)

        val origin = async { tracker.awaitOrigin() }
        assertTrue(tracker.searching.value)
        assertEquals(1, gps.subscriptions)

        gps.fixes.emit(fix(accuracyM = 80.0))
        assertTrue("una lectura imprecisa no sirve de origen", tracker.searching.value)
        gps.fixes.emit(fix())

        assertEquals(here, origin.await())
        assertFalse(tracker.searching.value)
    }

    @Test
    fun withoutSignalGivesUpAfterTheTimeout() = runTest(UnconfinedTestDispatcher()) {
        val tracker = LocationTracker(FakeGps(), { 0L }, backgroundScope, originTimeoutMs = 40_000)

        val origin = async { runCatching { tracker.awaitOrigin() } }
        advanceTimeBy(40_001)

        assertTrue(origin.await().exceptionOrNull() is GpsUnavailableError)
        assertFalse(tracker.searching.value)
    }
}
