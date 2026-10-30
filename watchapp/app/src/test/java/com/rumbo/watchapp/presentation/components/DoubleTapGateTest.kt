package com.rumbo.watchapp.presentation.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val TIMEOUT_MS = 300L

@OptIn(ExperimentalCoroutinesApi::class)
class DoubleTapGateTest {

    private var doubleTaps = 0
    private var clicks = 0

    private fun TestScope.gate() = DoubleTapGate(backgroundScope, TIMEOUT_MS) { doubleTaps++ }

    private fun DoubleTapGate.tap(downMs: Long, upMs: Long = downMs + 50) = onTap(onDown(downMs), upMs)

    @Test
    fun twoQuickTapsAreADoubleTap() = runTest {
        val gate = gate()

        gate.tap(1_000)
        gate.tap(1_200)

        assertEquals(1, doubleTaps)
    }

    @Test
    fun aSingleTapIsNot() = runTest {
        gate().tap(1_000)

        assertEquals(0, doubleTaps)
    }

    @Test
    fun twoSlowTapsAreNot() = runTest {
        val gate = gate()

        gate.tap(1_000)
        gate.tap(1_351)

        assertEquals(0, doubleTaps)
    }

    @Test
    fun theTimeoutCountsFromLiftingTheFirstFinger() = runTest {
        val gate = gate()

        gate.tap(1_000, upMs = 1_400)
        gate.tap(1_700)

        assertEquals(1, doubleTaps)
    }

    @Test
    fun aThirdTapStartsOver() = runTest {
        val gate = gate()

        gate.tap(1_000)
        gate.tap(1_200)
        gate.tap(1_400)

        assertEquals(1, doubleTaps)
    }

    @Test
    fun fourQuickTapsAreTwoDoubleTaps() = runTest {
        val gate = gate()

        listOf(1_000L, 1_200L, 1_400L, 1_600L).forEach { gate.tap(it) }

        assertEquals(2, doubleTaps)
    }

    @Test
    fun aButtonActsOnlyAfterTheWindowCloses() = runTest {
        val gate = gate()

        gate.tap(1_000)
        gate.click { clicks++ }
        advanceTimeBy(TIMEOUT_MS - 1)
        runCurrent()
        assertEquals(0, clicks)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, clicks)
    }

    @Test
    fun aDoubleTapOverAButtonNeverRunsTheButton() = runTest {
        val gate = gate()

        gate.tap(1_000)
        gate.click { clicks++ }
        gate.tap(1_200)
        advanceTimeBy(TIMEOUT_MS * 2)
        runCurrent()

        assertEquals(0, clicks)
        assertEquals(1, doubleTaps)
    }

    @Test
    fun theButtonIsCancelledWhenTheSecondFingerLandsNotWhenItLifts() = runTest {
        val gate = gate()

        gate.tap(1_000)
        gate.click { clicks++ }
        // El segundo dedo baja a tiempo pero se levanta después de cerrada la ventana.
        val second = gate.onDown(1_200)
        advanceTimeBy(TIMEOUT_MS * 2)
        runCurrent()
        gate.onTap(second, 1_600)

        assertEquals(0, clicks)
        assertEquals(1, doubleTaps)
    }

    @Test
    fun aDoubleTapDoesNotCancelALaterButton() = runTest {
        val gate = gate()

        gate.tap(1_000)
        gate.tap(1_200)
        gate.click { clicks++ }
        advanceTimeBy(TIMEOUT_MS)
        runCurrent()

        assertEquals(1, clicks)
    }
}
