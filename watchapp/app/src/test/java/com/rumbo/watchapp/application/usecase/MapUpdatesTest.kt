package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.testing.pattern
import com.rumbo.watchapp.domain.vibration.VibrationCue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapUpdatesTest {

    private val source = MutableStateFlow(TrackingState(progressM = 1.0))
    private val drawn = mutableListOf<TrackingState>()

    private fun TestScope.collect() = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        source.mapUpdates().collect { drawn += it }
    }

    private val progress get() = drawn.map { it.progressM }

    @Test
    fun theFirstStateIsDrawnRightAway() = runTest {
        collect()

        assertEquals(listOf(1.0), progress)
    }

    @Test
    fun aMovingPersonIsDrawnAtMostOncePerSecond() = runTest {
        collect()

        // Diez lecturas en un segundo: solo la última llega al siguiente cuadro.
        for (i in 2..11) {
            source.value = source.value.copy(progressM = i.toDouble())
            advanceTimeBy(100)
        }
        runCurrent()

        assertEquals(listOf(1.0, 11.0), progress)
    }

    @Test
    fun theLastChangeIsDrawnEvenIfNothingElseComes() = runTest {
        collect()

        source.value = source.value.copy(status = NavigationStatus.OFF_ROUTE)
        advanceTimeBy(MAP_REDRAW_MS)
        runCurrent()

        assertEquals(NavigationStatus.OFF_ROUTE, drawn.last().status)
    }

    @Test
    fun aVibrationAloneDoesNotRedraw() = runTest {
        collect()

        source.value = source.value.copy(pattern = pattern(VibrationCue.ON_ROUTE))
        advanceTimeBy(5 * MAP_REDRAW_MS)
        runCurrent()

        assertEquals(1, drawn.size)
    }

    @Test
    fun aStillPersonIsNotRedrawnWhileTheClockTicks() = runTest {
        collect()

        advanceTimeBy(60 * MAP_REDRAW_MS)
        runCurrent()

        assertEquals(1, drawn.size)
    }

    @Test
    fun itStopsDrawingWhenTheScreenLeaves() = runTest {
        val screen = collect()
        screen.cancel()

        source.value = source.value.copy(progressM = 2.0)
        advanceTimeBy(5 * MAP_REDRAW_MS)
        runCurrent()

        assertEquals(listOf(1.0), progress)
    }
}
