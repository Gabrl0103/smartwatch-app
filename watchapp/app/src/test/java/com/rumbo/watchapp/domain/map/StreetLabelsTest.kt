package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.Point2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetLabelsTest {

    private val center = Point2(1_000.0, 1_000.0)
    // Los botones tapan lo que queda a más de 200 px por debajo de la persona.
    private val buttons = ScreenBox(-400.0, 200.0, 400.0, 400.0)
    private val area = LabelArea(Point2(0.0, 0.0), radius = 400.0, personClearance = 10.0, blocked = listOf(buttons))

    // Una calle de este a oeste que pasa 60 px al norte de la persona.
    private val eastWest = WorldStreet("Calle 18", listOf(Point2(800.0, 940.0), Point2(1_200.0, 940.0)))
    private val northSouth = WorldStreet("Carrera 26", listOf(Point2(1_060.0, 1_200.0), Point2(1_060.0, 800.0)))

    private fun place(streets: List<WorldStreet>, heading: Double, max: Int = 4) =
        placeStreetLabels(streets, center, heading, 1.0, area, { it.length * 7.0 }, 14.0, max)

    @Test
    fun walkingSouthTheNamesAreNeverUpsideDown() {
        for (heading in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0)) {
            place(listOf(eastWest, northSouth), heading).forEach {
                assertTrue("rumbo $heading: ${it.angleDeg}", it.angleDeg > -90.0 && it.angleDeg <= 90.0)
            }
        }
    }

    @Test
    fun aStreetAheadIsWrittenAboveThePersonFollowingIt() {
        val label = place(listOf(eastWest), heading = 0.0).single()

        assertEquals(0.0, label.angleDeg, 1e-9)
        assertEquals(-60.0, label.position.y, 1e-9)
    }

    @Test
    fun eachStreetIsNamedOnceAndTheCapIsRespected() {
        val split = eastWest.copy(points = listOf(Point2(800.0, 940.0), Point2(1_000.0, 940.0), Point2(1_200.0, 940.0)))

        assertEquals(1, place(listOf(split, eastWest), 0.0).size)
        assertEquals(1, place(listOf(eastWest, northSouth), 0.0, max = 1).size)
    }

    @Test
    fun namesUnderTheButtonsOrTooShortAreLeftOut() {
        val behindTheButtons = WorldStreet("Calle 20", listOf(Point2(800.0, 1_300.0), Point2(1_200.0, 1_300.0)))
        val tooShort = WorldStreet("Calle 21", listOf(Point2(990.0, 940.0), Point2(1_010.0, 940.0)))

        assertTrue(place(listOf(behindTheButtons, tooShort), 0.0).isEmpty())
    }

    @Test
    fun theStreetNamesTileIsTheZoom14Ancestor() {
        assertEquals(TileId(14, 4674, 8136), streetNamesTile(TileId(17, 37399, 65093)))
    }
}
