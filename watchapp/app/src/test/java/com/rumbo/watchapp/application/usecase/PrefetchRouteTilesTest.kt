package com.rumbo.watchapp.application.usecase

import com.rumbo.watchapp.application.map.RequestPacer
import com.rumbo.watchapp.domain.map.MAP_TILE_ZOOM
import com.rumbo.watchapp.domain.map.RouteGeometry
import com.rumbo.watchapp.domain.map.TileId
import com.rumbo.watchapp.domain.map.TileRouteGeometry
import com.rumbo.watchapp.domain.map.tileScene
import com.rumbo.watchapp.domain.port.MapTileSource
import com.rumbo.watchapp.domain.port.NoLogger
import com.rumbo.watchapp.testing.readDemoRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchRouteTilesTest {

    private class FakeTiles(private val failing: Set<TileId> = emptySet()) : MapTileSource {
        val asked = mutableListOf<TileId>()

        override suspend fun load(tile: TileId): ByteArray? {
            asked += tile
            return if (tile in failing) null else byteArrayOf(1)
        }
    }

    @Test
    fun theDemoRouteCorridorIsPrefetchedOnceUnderTheCap() = runTest {
        val tiles = FakeTiles()

        val loaded = PrefetchRouteTiles(tiles, NoLogger)(readDemoRoute())

        assertEquals(tiles.asked.toSet().size, tiles.asked.size)
        assertEquals(tiles.asked.size, loaded)
        assertTrue(loaded in 1..PREFETCH_MAX_TILES)
        // Para estimar créditos de Geoapify (0,25 por tile); se ve en el informe de la prueba.
        println("Ruta demo: $loaded tiles a zoom $MAP_TILE_ZOOM")
    }

    @Test
    fun tilesThatDoNotArriveAreSkipped() = runTest {
        val route = readDemoRoute()
        val first = TileRouteGeometry(RouteGeometry(route)).corridor(MAP_VIEW_RADIUS_PX, 1).first()

        val loaded = PrefetchRouteTiles(FakeTiles(failing = setOf(first)), NoLogger, maxTiles = 5)(route)

        assertEquals(4, loaded)
    }

    @Test
    fun thePacerLetsAtMostFourStartPerSecond() = runTest {
        val pacer = RequestPacer(maxPerSecond = 4, maxConcurrent = 2, clock = { testScheduler.currentTime })
        val starts = (1..8).map { async { pacer.run { testScheduler.currentTime } } }.awaitAll()

        assertTrue(starts.max() - starts.min() >= 1_750)
    }

    @Test
    fun theSceneHasThePersonTheTilesAndTheNextTurn() {
        val route = readDemoRoute()
        val geometry = TileRouteGeometry(RouteGeometry(route))

        val scene = tileScene(geometry, null, null, 10.0, MAP_VIEW_RADIUS_PX)!!

        assertTrue(scene.tiles.isNotEmpty())
        assertTrue(scene.walked.isNotEmpty() && scene.remaining.isNotEmpty())
        assertTrue((scene.nextTurnM ?: 0.0) > 0)
    }
}
