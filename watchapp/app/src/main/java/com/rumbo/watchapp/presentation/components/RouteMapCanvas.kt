package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.map.MapFrame
import com.rumbo.watchapp.domain.map.MapView
import com.rumbo.watchapp.domain.map.MapZoom
import com.rumbo.watchapp.domain.map.RouteGeometry
import com.rumbo.watchapp.domain.map.mapFrame
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.theme.Background
import com.rumbo.watchapp.presentation.theme.Border
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.theme.TextPrimary

// Lo que se deja libre junto al borde redondo.
private val EdgeMargin = 14.dp
private val RouteWidth = 5.dp
private val RouteBaseWidth = 9.dp
private val OffRouteHighlightWidth = 13.dp
private val TurnRadius = 10.dp
private val TurnStroke = 3.dp
private val DestinationRadius = 10.dp
private val PersonRadius = 7.dp
private val ArrowTip = 19.dp
private val ArrowHalfWidth = 8.dp
private val Outline = 2.dp

/**
 * El mapa de la ruta, solo con lo que trae [state]: no usa teselas ni internet. Se dibuja una vez por
 * cada [state] o [zoom] nuevo, sin animaciones. [topInset] y [bottomInset] son lo que tapan los
 * controles: la ruta entera se ajusta entre ellos.
 */
@Composable
fun RouteMapCanvas(
    state: TrackingState,
    zoom: MapZoom,
    description: String,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val geometry = remember(state.route) { state.route?.let(::RouteGeometry) }
    Canvas(modifier.fillMaxSize(), contentDescription = description) {
        drawRect(Background)
        if (geometry == null) return@Canvas
        val view = MapView(
            width = size.width.toDouble(),
            height = size.height.toDouble(),
            margin = EdgeMargin.toPx().toDouble(),
            topInset = topInset.toPx().toDouble(),
            bottomInset = bottomInset.toPx().toDouble(),
        )
        drawFrame(mapFrame(geometry, state.position, state.headingDeg, state.progressM, zoom, view), state.status)
    }
}

private fun DrawScope.drawFrame(frame: MapFrame, status: NavigationStatus) {
    if (status == NavigationStatus.OFF_ROUTE) drawLines(frame.route, StatusOffRoute, OffRouteHighlightWidth)
    drawLines(frame.route, Border, RouteBaseWidth)
    drawLines(frame.walked, Primary, RouteWidth)
    drawLines(frame.remaining, TextPrimary, RouteWidth)
    frame.destination?.let { drawDestination(it.toOffset()) }
    frame.nextTurn?.let { drawTurn(it.toOffset()) }
    frame.person?.let { drawPerson(it.toOffset(), frame.personHeadingDeg.toFloat(), personColor(status)) }
}

private fun personColor(status: NavigationStatus) = when (status) {
    NavigationStatus.OFF_ROUTE -> StatusOffRoute
    NavigationStatus.NO_SIGNAL -> StatusNoSignal
    else -> StatusTurnSoon
}

private fun DrawScope.drawLines(lines: List<List<Point2>>, color: Color, width: Dp) {
    val stroke = Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    lines.forEach { line ->
        val path = Path()
        line.forEachIndexed { i, point ->
            if (i == 0) path.moveTo(point.x.toFloat(), point.y.toFloat()) else path.lineTo(point.x.toFloat(), point.y.toFloat())
        }
        drawPath(path, color, style = stroke)
    }
}

// Tres formas distintas (diana, aro y punto con flecha): no se distinguen solo por el color.
private fun DrawScope.drawDestination(center: Offset) {
    drawCircle(StatusArrived, DestinationRadius.toPx(), center)
    drawCircle(Background, DestinationRadius.toPx() * 0.6f, center)
    drawCircle(StatusArrived, DestinationRadius.toPx() * 0.3f, center)
}

private fun DrawScope.drawTurn(center: Offset) {
    drawCircle(Background, TurnRadius.toPx(), center)
    drawCircle(StatusTurnSoon, TurnRadius.toPx(), center, style = Stroke(TurnStroke.toPx()))
}

private fun DrawScope.drawPerson(center: Offset, headingDeg: Float, color: Color) {
    rotate(headingDeg, center) {
        val arrow = Path().apply {
            moveTo(center.x, center.y - ArrowTip.toPx())
            lineTo(center.x + ArrowHalfWidth.toPx(), center.y)
            lineTo(center.x - ArrowHalfWidth.toPx(), center.y)
            close()
        }
        drawPath(arrow, Background, style = Stroke(Outline.toPx() * 2, join = StrokeJoin.Round))
        drawPath(arrow, color)
    }
    drawCircle(Background, PersonRadius.toPx() + Outline.toPx(), center)
    drawCircle(color, PersonRadius.toPx(), center)
}

private fun Point2.toOffset() = Offset(x.toFloat(), y.toFloat())
