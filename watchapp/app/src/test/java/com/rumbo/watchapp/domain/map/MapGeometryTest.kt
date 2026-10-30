package com.rumbo.watchapp.domain.map

import com.rumbo.watchapp.domain.geo.LocalProjection
import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.model.Coordinates
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOLERANCE = 1e-6

class MapGeometryTest {

    private val view = Viewport(0.0, 0.0, 100.0, 100.0)

    private fun assertPoint(x: Double, y: Double, point: Point2?) {
        assertEquals(x, point!!.x, TOLERANCE)
        assertEquals(y, point.y, TOLERANCE)
    }

    @Test
    fun aPointNorthOfTheOriginProjectsToMetersNorth() {
        val origin = Coordinates(0.0, 0.0)

        val point = LocalProjection(origin).toXY(Coordinates(0.001, 0.0))

        assertEquals(0.0, point.x, TOLERANCE)
        assertEquals(111.195, point.y, 0.01)
    }

    @Test
    fun aDegreeOfLongitudeIsShorterAwayFromTheEquator() {
        val projection = LocalProjection(Coordinates(60.0, 0.0))

        // cos(60°) = 0,5.
        assertEquals(55.6, projection.toXY(Coordinates(60.0, 0.001)).x, 0.1)
    }

    @Test
    fun pointsInAStraightLineStayInAStraightLine() {
        val projection = LocalProjection(Coordinates(1.2, -77.3))

        val points = (0..4).map { projection.toXY(Coordinates(1.2 + it * 0.001, -77.3 + it * 0.002)) }

        val ratio = points[1].y / points[1].x
        points.drop(2).forEach { assertEquals(ratio, it.y / it.x, 1e-3) }
    }

    @Test
    fun headingNorthLeavesTheMapAsItIs() {
        assertPoint(0.0, 10.0, rotateToHeading(Point2(0.0, 10.0), 0.0))
        assertPoint(10.0, 0.0, rotateToHeading(Point2(10.0, 0.0), 0.0))
    }

    @Test
    fun headingEastPutsTheEastUp() {
        assertPoint(0.0, 10.0, rotateToHeading(Point2(10.0, 0.0), 90.0))
        // Lo que estaba al norte queda a la izquierda de quien va al este.
        assertPoint(-10.0, 0.0, rotateToHeading(Point2(0.0, 10.0), 90.0))
    }

    @Test
    fun headingSouthPutsTheSouthUp() {
        assertPoint(0.0, 10.0, rotateToHeading(Point2(0.0, -10.0), 180.0))
    }

    @Test
    fun headingWestPutsTheWestUp() {
        assertPoint(0.0, 10.0, rotateToHeading(Point2(-10.0, 0.0), 270.0))
    }

    @Test
    fun rotatingKeepsDistances() {
        val rotated = rotateToHeading(Point2(30.0, 40.0), 37.0)

        assertEquals(50.0, hypot(rotated.x, rotated.y), TOLERANCE)
    }

    @Test
    fun aLineInsideTheViewportIsKeptWhole() {
        val line = listOf(Point2(10.0, 10.0), Point2(50.0, 50.0), Point2(90.0, 20.0))

        assertEquals(listOf(line), clipToViewport(line, view))
    }

    @Test
    fun aLineThatLeavesTheViewportIsCutAtTheEdge() {
        val pieces = clipToViewport(listOf(Point2(50.0, 50.0), Point2(150.0, 50.0)), view)

        assertEquals(1, pieces.size)
        assertPoint(50.0, 50.0, pieces[0][0])
        assertPoint(100.0, 50.0, pieces[0][1])
    }

    @Test
    fun aLineThatCrossesTheViewportKeepsOnlyTheMiddle() {
        val pieces = clipToViewport(listOf(Point2(-50.0, 50.0), Point2(150.0, 50.0)), view)

        assertPoint(0.0, 50.0, pieces.single()[0])
        assertPoint(100.0, 50.0, pieces.single()[1])
    }

    @Test
    fun aLineThatLeavesAndComesBackGivesTwoPieces() {
        val pieces = clipToViewport(
            listOf(Point2(50.0, 50.0), Point2(150.0, 50.0), Point2(150.0, 80.0), Point2(50.0, 80.0)),
            view,
        )

        assertEquals(2, pieces.size)
        assertPoint(100.0, 80.0, pieces[1][0])
        assertPoint(50.0, 80.0, pieces[1][1])
    }

    @Test
    fun aLineEntirelyOutsideDisappears() {
        assertEquals(emptyList<List<Point2>>(), clipToViewport(listOf(Point2(200.0, 200.0), Point2(300.0, 200.0)), view))
    }

    @Test
    fun aLinePassingOutsideACornerDisappears() {
        assertEquals(emptyList<List<Point2>>(), clipToViewport(listOf(Point2(110.0, -20.0), Point2(120.0, 20.0)), view))
    }

    @Test
    fun aSinglePointIsVisibleOnlyInside() {
        assertEquals(listOf(listOf(Point2(5.0, 5.0))), clipToViewport(listOf(Point2(5.0, 5.0)), view))
        assertEquals(emptyList<List<Point2>>(), clipToViewport(listOf(Point2(500.0, 5.0)), view))
    }

    @Test
    fun anEmptyLineStaysEmpty() {
        assertEquals(emptyList<List<Point2>>(), clipToViewport(emptyList(), view))
    }

    private val straight = listOf(Point2(0.0, 0.0), Point2(0.0, 100.0), Point2(100.0, 100.0))
    private val along = listOf(0.0, 100.0, 200.0)

    @Test
    fun splittingCutsBetweenTwoPointsAndBothPartsTouch() {
        val (walked, remaining) = splitAlong(straight, along, 150.0)

        assertEquals(listOf(Point2(0.0, 0.0), Point2(0.0, 100.0), Point2(50.0, 100.0)), walked)
        assertEquals(listOf(Point2(50.0, 100.0), Point2(100.0, 100.0)), remaining)
    }

    @Test
    fun beforeStartingNothingIsWalked() {
        assertEquals(emptyList<Point2>() to straight, splitAlong(straight, along, 0.0))
    }

    @Test
    fun afterTheEndNothingRemains() {
        assertEquals(straight to emptyList<Point2>(), splitAlong(straight, along, 200.0))
        assertEquals(straight to emptyList<Point2>(), splitAlong(straight, along, 999.0))
    }

    @Test
    fun aRouteOfOnePointHasNothingToSplit() {
        val point = listOf(Point2(1.0, 1.0))

        assertEquals(emptyList<Point2>() to point, splitAlong(point, listOf(0.0), 50.0))
    }

    @Test
    fun theBearingFollowsTheRoute() {
        assertEquals(0.0, bearingAlong(straight, along, 50.0), TOLERANCE)
        assertEquals(90.0, bearingAlong(straight, along, 150.0), TOLERANCE)
        assertEquals(0.0, bearingAlong(straight, along, 0.0), TOLERANCE)
        assertEquals(90.0, bearingAlong(straight, along, 999.0), TOLERANCE)
    }

    @Test
    fun theBearingOfARouteHeadingSouthWestIsBelowTheCircle() {
        val line = listOf(Point2(0.0, 0.0), Point2(-10.0, -10.0))

        assertEquals(225.0, bearingAlong(line, listOf(0.0, 14.0), 5.0), TOLERANCE)
    }

    @Test
    fun aRouteWithoutDirectionPointsNorth() {
        assertEquals(0.0, bearingAlong(listOf(Point2(1.0, 1.0)), listOf(0.0), 0.0), TOLERANCE)
        assertEquals(0.0, bearingAlong(emptyList(), emptyList(), 0.0), TOLERANCE)
    }

    @Test
    fun aPointAlongTheRoute() {
        assertPoint(50.0, 100.0, pointAlong(straight, along, 150.0))
        assertPoint(100.0, 100.0, pointAlong(straight, along, 999.0))
        assertPoint(1.0, 1.0, pointAlong(listOf(Point2(1.0, 1.0)), listOf(0.0), 10.0))
        assertNull(pointAlong(emptyList(), emptyList(), 0.0))
    }

    @Test
    fun viewportContainsItsEdges() {
        assertTrue(view.contains(Point2(100.0, 0.0)))
    }
}
