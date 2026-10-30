package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.model.Coordinates
import com.rumbo.watchapp.domain.model.Maneuver
import com.rumbo.watchapp.domain.model.Route
import com.rumbo.watchapp.domain.model.RouteStep
import com.rumbo.watchapp.testing.at
import com.rumbo.watchapp.testing.lRoute
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOLERANCE = 0.5

class RouteMapTest {

    // Pantalla de 454 px: el radio útil es 200, así que 150 m ocupan 200 px.
    private val view = MapView(width = 454.0, height = 454.0, margin = 27.0)
    private val center = Point2(227.0, 227.0)
    private val route = lRoute()
    private val geometry = RouteGeometry(route)

    private fun assertPoint(x: Double, y: Double, point: Point2?) {
        assertNotNull(point)
        assertEquals(x, point!!.x, TOLERANCE)
        assertEquals(y, point.y, TOLERANCE)
    }

    private fun frame(
        position: Coordinates? = at(0.0, 100.0),
        heading: Double? = 0.0,
        progress: Double = 100.0,
        zoom: MapZoom = MapZoom.NEARBY,
        geometry: RouteGeometry = this.geometry,
    ) = mapFrame(geometry, position, heading, progress, zoom, view)

    @Test
    fun thePersonGoesInTheCenterOfTheNearbyView() {
        assertPoint(227.0, 227.0, frame().person)
    }

    @Test
    fun oneHundredAndFiftyMetersAheadReachTheEdgeOfTheNearbyView() {
        // El giro está 100 m adelante: 100 × 200 / 150 px hacia arriba.
        assertPoint(227.0, 227.0 - 133.3, frame().nextTurn)
    }

    @Test
    fun headingEastTurnsTheMapSoTheRouteAheadGoesUp() {
        val frame = frame(position = at(50.0, 200.0), heading = 90.0, progress = 250.0)

        val ahead = frame.remaining.flatten().last()

        assertEquals(227.0, ahead.x, TOLERANCE)
        assertTrue("La ruta sigue hacia arriba: $ahead", ahead.y < 227.0)
    }

    @Test
    fun headingSouthPutsWhatIsNorthDown() {
        val frame = frame(position = at(0.0, 100.0), heading = 180.0)

        assertTrue(frame.nextTurn!!.y > center.y)
    }

    @Test
    fun theArrowPointsUpInTheNearbyViewBecauseTheMapTurnsInstead() {
        assertEquals(0.0, frame(heading = 90.0).personHeadingDeg, 0.0)
    }

    @Test
    fun theArrowShowsTheHeadingInTheWholeRouteViewWhereNorthIsUp() {
        assertEquals(90.0, frame(heading = 90.0, zoom = MapZoom.WHOLE_ROUTE).personHeadingDeg, 0.0)
    }

    @Test
    fun withoutHeadingItUsesTheDirectionOfTheRoute() {
        val north = frame(position = at(0.0, 100.0), heading = null, progress = 100.0, zoom = MapZoom.WHOLE_ROUTE)
        val east = frame(position = at(100.0, 200.0), heading = null, progress = 300.0, zoom = MapZoom.WHOLE_ROUTE)

        assertEquals(0.0, north.personHeadingDeg, 0.5)
        assertEquals(90.0, east.personHeadingDeg, 0.5)
    }

    @Test
    fun withoutAPositionThePersonGoesOnTheRoute() {
        val frame = frame(position = null, heading = null, progress = 0.0, zoom = MapZoom.WHOLE_ROUTE)
        val withPosition = frame(position = route.polyline.first(), heading = null, progress = 0.0, zoom = MapZoom.WHOLE_ROUTE)

        assertPoint(withPosition.person!!.x, withPosition.person!!.y, frame.person)
    }

    @Test
    fun walkedAndRemainingMeetAtTheProgress() {
        val frame = frame()

        val cut = frame.walked.flatten().last()
        assertPoint(cut.x, cut.y, frame.remaining.flatten().first())
        assertPoint(227.0, 227.0, cut)
    }

    @Test
    fun theFarEndOfALongRouteIsClippedInTheNearbyView() {
        val frame = frame()

        // La ruta sigue 200 m al este del giro, fuera de los 150 m: se corta en el borde de la pantalla.
        assertTrue(frame.route.flatten().all { view.viewport.contains(it) })
        assertTrue(frame.route.flatten().size < route.polyline.size)
    }

    @Test
    fun theDestinationOutOfViewIsNotDrawn() {
        assertNull(frame(zoom = MapZoom.NEARBY).destination)
    }

    @Test
    fun theDestinationInViewIsMarked() {
        assertNotNull(frame(position = at(150.0, 200.0), heading = 90.0, progress = 350.0).destination)
    }

    @Test
    fun thePassedTurnIsNoLongerTheNextOne() {
        val frame = frame(position = at(50.0, 200.0), heading = 90.0, progress = 250.0)

        assertNull(frame.nextTurn)
    }

    @Test
    fun theWholeRouteFitsInsideTheCircle() {
        val frame = frame(zoom = MapZoom.WHOLE_ROUTE)

        val all = frame.route.flatten()
        assertEquals(route.polyline.size, all.size)
        all.forEach { assertTrue("$it", hypot(it.x - center.x, it.y - center.y) <= 200.0 + TOLERANCE) }
    }

    @Test
    fun theWholeRouteAlsoShowsAPersonFarOffTheRoute() {
        val far = frame(position = at(-300.0, -300.0), zoom = MapZoom.WHOLE_ROUTE)

        assertTrue(view.viewport.contains(far.person!!))
    }

    @Test
    fun theWholeRouteStaysBetweenTheInsets() {
        val insets = MapView(454.0, 454.0, 27.0, topInset = 50.0, bottomInset = 150.0)

        val frame = mapFrame(geometry, at(0.0, 100.0), 0.0, 100.0, MapZoom.WHOLE_ROUTE, insets)

        frame.route.flatten().forEach { assertTrue("$it", it.y in 50.0 - TOLERANCE..304.0 + TOLERANCE) }
    }

    // Hora arriba y controles abajo, como en la pantalla: la franja libre queda por encima del centro.
    private val highBand = MapView(454.0, 454.0, 27.0, topInset = 48.0, bottomInset = 234.0)

    @Test
    fun theWholeRouteStaysInsideTheCircleWhenTheFreeBandIsHigh() {
        val frame = mapFrame(geometry, at(0.0, 100.0), 0.0, 100.0, MapZoom.WHOLE_ROUTE, highBand)

        frame.route.flatten().forEach {
            assertTrue("$it", hypot(it.x - center.x, it.y - center.y) <= 200.0 + TOLERANCE)
            assertTrue("$it", it.y in 48.0 - TOLERANCE..220.0 + TOLERANCE)
        }
    }

    @Test
    fun aWideRouteUsesTheWidthOfTheScreenEvenIfTheFreeBandIsLow() {
        val wide = RouteGeometry(Route("Este", 0.0, 0.0, emptyList(), listOf(at(0.0, 0.0), at(300.0, 0.0))))

        val line = mapFrame(wide, null, null, 0.0, MapZoom.WHOLE_ROUTE, highBand).route.flatten()

        assertTrue("Ancho: ${line.last().x - line.first().x}", line.last().x - line.first().x > 300)
    }

    @Test
    fun theWholeRouteFillsTheFreeBandWhenItIsTall() {
        val tall = RouteGeometry(Route("Norte", 0.0, 0.0, emptyList(), listOf(at(0.0, 0.0), at(0.0, 300.0))))

        val line = mapFrame(tall, null, null, 0.0, MapZoom.WHOLE_ROUTE, highBand).route.flatten()

        assertEquals(172.0, line.first().y - line.last().y, TOLERANCE)
    }

    @Test
    fun theWholeRouteKeepsNorthUp() {
        val frame = frame(heading = 90.0, zoom = MapZoom.WHOLE_ROUTE)

        val points = frame.route.flatten()
        // La ruta empieza al sur y sigue al norte: su primer punto queda abajo.
        assertTrue(points.first().y > points[20].y)
    }

    @Test
    fun anEmptyRouteDrawsNothing() {
        val empty = RouteGeometry(Route("Nada", 0.0, 0.0, emptyList(), emptyList()))

        assertEquals(MapFrame(), frame(position = null, heading = null, progress = 0.0, geometry = empty))
    }

    @Test
    fun anEmptyRouteWithAPositionStillDrawsNoRoute() {
        val empty = RouteGeometry(Route("Nada", 0.0, 0.0, emptyList(), emptyList()))

        val frame = frame(position = at(1.0, 1.0), heading = 0.0, progress = 0.0, geometry = empty)

        assertEquals(emptyList<List<Point2>>(), frame.route)
        assertNull(frame.destination)
    }

    @Test
    fun aRouteOfOnePointShowsTheDestinationUnderThePerson() {
        val only = at(0.0, 0.0)
        val single = RouteGeometry(Route("Aquí", 0.0, 0.0, emptyList(), listOf(only)))

        val frame = frame(position = null, heading = null, progress = 0.0, geometry = single)

        assertPoint(227.0, 227.0, frame.person)
        assertPoint(227.0, 227.0, frame.destination)
        assertEquals(emptyList<List<Point2>>(), frame.walked)
    }

    @Test
    fun aRouteOfOnePointFitsTheWholeRouteViewToo() {
        val single = RouteGeometry(Route("Aquí", 0.0, 0.0, emptyList(), listOf(at(0.0, 0.0))))

        assertPoint(227.0, 227.0, frame(position = null, heading = null, progress = 0.0, zoom = MapZoom.WHOLE_ROUTE, geometry = single).person)
    }

    @Test
    fun withoutPolylineItUsesTheSteps() {
        val point = at(0.0, 0.0)
        val steps = listOf(RouteStep(1, Maneuver.ARRIVE, "Ha llegado.", 0.0, point.lat, point.lng))

        assertEquals(1, RouteGeometry(Route("Aquí", 0.0, 0.0, steps, emptyList())).points.size)
    }
}
