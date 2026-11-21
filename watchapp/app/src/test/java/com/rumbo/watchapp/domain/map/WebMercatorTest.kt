package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMercatorTest {

    private val demoStart = Coordinates(1.214997, -77.278419)

    @Test
    fun theOriginOfTheWorldIsTheCenterOfTheMap() {
        val center = worldPixel(Coordinates(0.0, 0.0), 1)

        assertEquals(256.0, center.x, 1e-9)
        assertEquals(256.0, center.y, 1e-9)
    }

    @Test
    fun theDemoStartFallsInTheTileGeoapifyServesForIt() {
        assertEquals(TileId(17, 37399, 65093), tileAt(worldPixel(demoStart, 17), 17))
    }

    @Test
    fun aPixelAtZoom17IsAboutOneMeterNearTheEquator() {
        assertEquals(1.19, metersPerWorldPixel(demoStart.lat, 17), 0.01)
    }

    @Test
    fun aSmallViewInsideATileNeedsOnlyThatTileAndACornerNeedsFour() {
        assertEquals(listOf(TileId(17, 10, 20)), tilesAround(Point2(10.5 * 256, 20.5 * 256), 50.0, 17))
        assertEquals(4, tilesAround(Point2(11.0 * 256, 21.0 * 256), 50.0, 17).size)
    }

    @Test
    fun theNearestTileComesFirst() {
        val center = Point2(10.9 * 256, 20.5 * 256)

        assertEquals(TileId(17, 10, 20), tilesAround(center, 100.0, 17).first())
    }

    @Test
    fun theCorridorFollowsTheRouteWithoutRepeatsAndRespectsTheCap() {
        val line = listOf(demoStart, Coordinates(1.2100, -77.2784))

        val tiles = corridorTiles(line, 17, 128.0, maxTiles = 200)

        assertEquals(tiles.toSet().size, tiles.size)
        assertTrue(tileAt(worldPixel(line.last(), 17), 17) in tiles)
        assertEquals(5, corridorTiles(line, 17, 128.0, maxTiles = 5).size)
    }

    @Test
    fun theHeadingTurnsTheShortWayRound() {
        assertEquals(0.0, smoothHeading(350.0, 10.0, 0.5), 1e-9)
        assertEquals(90.0, smoothHeading(null, 450.0, 0.5), 1e-9)
        val partway = smoothHeading(0.0, 90.0, 0.4)
        assertTrue(partway > 0 && partway < 90)
    }
}
