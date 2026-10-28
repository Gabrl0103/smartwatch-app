package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.domain.navigation.NavigationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingStateTest {

    @Test
    fun stepProgressIsRemainingDistanceNormalizedByStepLength() {
        val state = TrackingState(distanceToNextStepM = 50.0, stepLengthM = 200.0)

        assertEquals(0.75f, state.stepProgress, 0.001f)
    }

    @Test
    fun stepProgressStaysBetweenZeroAndOne() {
        assertEquals(0f, TrackingState(distanceToNextStepM = 250.0, stepLengthM = 200.0).stepProgress, 0f)
        assertEquals(1f, TrackingState(distanceToNextStepM = 0.0, stepLengthM = 200.0).stepProgress, 0f)
    }

    @Test
    fun stepProgressWithoutStepIsZeroAndFullOnArrival() {
        assertEquals(0f, TrackingState().stepProgress, 0f)
        assertEquals(0f, TrackingState(distanceToNextStepM = 10.0, stepLengthM = 0.0).stepProgress, 0f)
        assertEquals(1f, TrackingState(status = NavigationStatus.ARRIVED).stepProgress, 0f)
    }
}
