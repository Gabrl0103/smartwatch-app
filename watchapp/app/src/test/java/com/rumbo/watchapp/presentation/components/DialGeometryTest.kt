package com.rumbo.watchapp.presentation.components

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialGeometryTest {

    // La pantalla del diseño: 454 px, radio 227, centro en (227, 227).
    private val c = 227f
    private val r = 227f

    // ---- Posición de las marcas ----

    @Test
    fun sixtyMarksEverySixDegreesWithTheTopOnesLeftOutForTheTime() {
        val marks = tickMarks(c, c, r)
        // Se esconden las que quedan a menos de 28° de las 12: 0, 6, 12, 18 y 24 a un lado y 24, 18, 12, 6 al otro.
        assertEquals(TICK_COUNT - 9, marks.size)
        assertEquals((5..55).toList(), marks.map { it.index })
        marks.forEach { assertEquals(it.index * 6f, it.angleDegrees, 1e-4f) }
    }

    @Test
    fun everyFifthMarkIsLongerAndStartsAtRadius208() {
        tickMarks(c, c, r).forEach { mark ->
            val inner = hypot(mark.inner.x - c, mark.inner.y - c)
            val outer = hypot(mark.outer.x - c, mark.outer.y - c)
            assertEquals(222f, outer, 0.01f)
            if (mark.index % 5 == 0) {
                assertTrue(mark.major)
                assertEquals(208f, inner, 0.01f)
            } else {
                assertFalse(mark.major)
                assertEquals(214f, inner, 0.01f)
            }
        }
    }

    @Test
    fun theMarkAtThreeOClockIsOnTheRightAndTheOneAtSixAtTheBottom() {
        val marks = tickMarks(c, c, r).associateBy { it.index }
        val three = marks.getValue(15)
        assertEquals(c + 222f, three.outer.x, 0.01f)
        assertEquals(c, three.outer.y, 0.01f)
        val six = marks.getValue(30)
        assertEquals(c, six.outer.x, 0.01f)
        assertEquals(c + 222f, six.outer.y, 0.01f)
        val nine = marks.getValue(45)
        assertEquals(c - 208f, nine.inner.x, 0.01f)
    }

    @Test
    fun marksScaleWithTheScreen() {
        // Un reloj de 227 dp de lado (radio 113,5): todo a la mitad, en el mismo ángulo.
        val half = tickMarks(113.5f, 113.5f, 113.5f).associateBy { it.index }
        assertEquals(113.5f + 111f, half.getValue(15).outer.x, 0.01f)
        assertEquals(113.5f + 104f, half.getValue(30).inner.y, 0.01f)
    }

    // ---- Marcas encendidas según el avance ----

    @Test
    fun noProgressLightsNothingAndFullProgressLightsEveryVisibleMark() {
        assertEquals(0, litTickCount(0f))
        assertEquals(0, litTickCount(-0.5f))
        assertEquals(TICK_COUNT - 9, litTickCount(1f))
        assertEquals(TICK_COUNT - 9, litTickCount(1.7f))
    }

    @Test
    fun halfwayLightsTheMarksUpToSixOClock() {
        // La punta en 0,5 está a 28 + 304 / 2 = 180°: de la 5 (30°) a la 30 (180°).
        assertEquals((5..30).toList(), (0 until TICK_COUNT).filter { isTickLit(it, 0.5f) })
    }

    @Test
    fun marksHiddenUnderTheTimeNeverLightUp() {
        listOf(0, 1, 2, 3, 4, 56, 57, 58, 59).forEach { assertFalse("marca $it", isTickLit(it, 1f)) }
    }

    @Test
    fun litMarksOnlyGrowAsTheProgressGrows() {
        var previous = 0
        (0..100).forEach { step ->
            val count = litTickCount(step / 100f)
            assertTrue("$step: $count < $previous", count >= previous)
            previous = count
        }
    }

    @Test
    fun aMarkLightsExactlyWhenTheTipReachesIt() {
        // La marca 10 está a 60°: la punta llega con (60 − 28) / 304 de avance.
        val reach = (60f - RING_START_DEGREES) / RING_SWEEP_DEGREES
        assertFalse(isTickLit(10, reach - 0.001f))
        assertTrue(isTickLit(10, reach))
    }

    // ---- Ángulo del punto en la punta ----

    @Test
    fun theDotGoesFromTheRightOfTheGapToItsLeft() {
        assertEquals(28f, progressDotAngle(0f), 1e-4f)
        assertEquals(180f, progressDotAngle(0.5f), 1e-4f)
        assertEquals(332f, progressDotAngle(1f), 1e-4f)
    }

    @Test
    fun theDotStaysOnTheRingWhenTheProgressIsOutOfRange() {
        assertEquals(progressDotAngle(0f), progressDotAngle(-1f), 1e-4f)
        assertEquals(progressDotAngle(1f), progressDotAngle(3f), 1e-4f)
    }

    @Test
    fun theDotSitsOnTheRingAtRadius203() {
        val ring = r * RING_RADIUS_FRACTION
        listOf(0f, 0.25f, 0.5f, 0.72f, 1f).forEach { progress ->
            val dot = pointOnDial(c, c, ring, progressDotAngle(progress))
            assertEquals(203f, hypot(dot.x - c, dot.y - c), 0.01f)
        }
        // A mitad de camino está abajo del todo.
        val bottom = pointOnDial(c, c, ring, progressDotAngle(0.5f))
        assertEquals(c, bottom.x, 0.01f)
        assertEquals(c + 203f, bottom.y, 0.01f)
    }

    // ---- Espacio del contenido ----

    @Test
    fun contentStaysTwentyFourDesignPixelsInsideTheRing() {
        // En el diseño (radio 227, anillo de 6 px en 203): acaba en 200 y el contenido en 176.
        assertEquals(176f, dialContentRadius(227f, 6f), 0.01f)
        // En el reloj de 227 dp: anillo de 6 dp en 101,5, por dentro en 98,5; contenido 12 dp más adentro.
        assertEquals(86.5f, dialContentRadius(113.5f, 6f), 0.01f)
    }
}
