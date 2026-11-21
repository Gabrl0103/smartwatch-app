package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rumbo.watchapp.application.usecase.TrackingState
import com.rumbo.watchapp.domain.geo.Point2
import com.rumbo.watchapp.domain.map.LabelArea
import com.rumbo.watchapp.domain.map.MapFrame
import com.rumbo.watchapp.domain.map.ScreenBox
import com.rumbo.watchapp.domain.map.MapView
import com.rumbo.watchapp.domain.map.MapZoom
import com.rumbo.watchapp.domain.map.RouteGeometry
import com.rumbo.watchapp.domain.map.TILE_SIZE
import com.rumbo.watchapp.domain.map.TileRouteGeometry
import com.rumbo.watchapp.domain.map.TileScene
import com.rumbo.watchapp.domain.map.mapFrame
import com.rumbo.watchapp.domain.map.placeStreetLabels
import com.rumbo.watchapp.domain.map.smoothHeading
import com.rumbo.watchapp.domain.map.tileOrigin
import com.rumbo.watchapp.domain.map.tileScene
import com.rumbo.watchapp.domain.navigation.NavigationStatus
import com.rumbo.watchapp.presentation.theme.Background
import com.rumbo.watchapp.presentation.theme.Border
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.StatusArrived
import com.rumbo.watchapp.presentation.theme.StatusNoSignal
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.StatusTurnSoon
import com.rumbo.watchapp.presentation.theme.TextPrimary
import com.rumbo.watchapp.presentation.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Lo que se deja libre junto al borde redondo.
private val EdgeMargin = 14.dp
private val RouteWidth = 5.dp
private val RouteBaseWidth = 9.dp
private val OffRouteHighlightWidth = 13.dp
private val TurnRadius = 10.dp
private val TurnStroke = 3.dp
private val DestinationRadius = 12.dp
private val PersonRadius = 7.dp
private val ArrowTip = 19.dp
private val ArrowHalfWidth = 8.dp
private val Outline = 2.dp

// Mapa de cerca: un tile de 256 px de mundo se dibuja en 256 dp; a densidad 2 cada píxel del tile
// @2x cae en un píxel de pantalla. A zoom 17 en Pasto son unos 0,6 m por píxel.
private val TileDp = 256.dp
private val GpsRouteWidth = 7.dp
private val GpsRouteBorderWidth = 11.dp
private val GpsOffRouteWidth = 15.dp
private const val WALKED_ALPHA = 0.4f

// Cuánto se acerca el rumbo mostrado al del GPS en cada cuadro (1 por segundo): no salta con el ruido.
private const val HEADING_SMOOTHING = 0.4

/** Altura de la flecha en el mapa de cerca, en fracción de la pantalla: un poco abajo del centro. */
const val PERSON_ANCHOR = 0.53f

// Nombres de calle: los escribe el reloj, derechos, con un halo del color del fondo para leerse
// sobre las calles. Lejos del borde redondo, de la flecha y sin pisarse; como mucho cuatro.
private val StreetLabelStyle = TextStyle(color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val StreetLabelHalo = 3.dp
private val StreetLabelEdge = 24.dp
private val StreetLabelPersonClearance = 26.dp
private const val MAX_STREET_LABELS = 4

/**
 * El mapa de la ruta [geometry] con lo que trae [state]. De cerca es un mapa tipo GPS con calles
 * ([TileMapCanvas]); la ruta entera se dibuja sola, sin tiles. Se dibuja una vez por cada [state]
 * o [zoom] nuevo (o tile que llega), sin animaciones. [topInset] y [bottomInset] son lo que tapan
 * la hora y los controles: la ruta entera se ajusta entre ellos.
 */
@Composable
fun RouteMapCanvas(
    state: TrackingState,
    geometry: RouteGeometry?,
    zoom: MapZoom,
    description: String,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
    labelBlockers: List<Rect> = emptyList(),
) {
    if (zoom == MapZoom.NEARBY) {
        TileMapCanvas(state, geometry, description, labelBlockers, modifier)
        return
    }
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

/**
 * Mapa de cerca tipo GPS: calles reales (tiles del backend, sin nombres) giradas para que el rumbo
 * quede arriba, los nombres de calle escritos derechos encima, luego la ruta y la flecha fija en
 * [PERSON_ANCHOR]. Un tile que no llegó deja ver el fondo oscuro; la ruta y los marcadores se
 * dibujan igual. Los nombres no pisan [labelBlockers] (lo que la pantalla pone encima, en
 * píxeles de la pantalla entera, que es lo que ocupa este mapa).
 */
@Composable
private fun TileMapCanvas(
    state: TrackingState,
    geometry: RouteGeometry?,
    description: String,
    labelBlockers: List<Rect>,
    modifier: Modifier,
) {
    val tiles = LocalMapTiles.current
    val textMeasurer = rememberTextMeasurer()
    val config = LocalConfiguration.current
    val tileGeometry = remember(geometry) { geometry?.let(::TileRouteGeometry) }
    val radius = remember(config) { visibleRadiusDp(config.screenWidthDp, config.screenHeightDp, config.isScreenRound) }
    val shownHeading = remember { doubleArrayOf(Double.NaN) }
    val scene = remember(tileGeometry, state) {
        tileGeometry?.let { tileScene(it, state.position, state.headingDeg, state.progressM, radius) }?.let {
            val heading = smoothHeading(shownHeading[0].takeUnless(Double::isNaN), it.headingDeg, HEADING_SMOOTHING)
            shownHeading[0] = heading
            it.copy(headingDeg = heading)
        }
    }
    SideEffect { scene?.let { tiles?.request(it.tiles) } }
    Canvas(modifier.fillMaxSize(), contentDescription = description) {
        drawRect(Background)
        if (scene == null) return@Canvas
        val anchor = Offset(size.width / 2, size.height * PERSON_ANCHOR)
        // Leer version aquí hace que el mapa se redibuje cuando llega un tile.
        tiles?.version
        val scale = TileDp.toPx() / TILE_SIZE
        translate(anchor.x, anchor.y) {
            rotate(-scene.headingDeg.toFloat(), pivot = Offset.Zero) { drawTiles(scene, scale, tiles) }
            drawStreetLabels(scene, scale, tiles, textMeasurer, labelArea(anchor, labelBlockers))
            rotate(-scene.headingDeg.toFloat(), pivot = Offset.Zero) { drawRouteOnTiles(scene, state.status, scale) }
        }
        drawPerson(anchor, 0f, personColor(state.status))
    }
}

// Hasta dónde se ve desde la flecha, en dp (= píxeles de mundo): en la redonda, el radio más lo
// que la flecha está corrida del centro; en la cuadrada, hasta la esquina más lejana.
private fun visibleRadiusDp(widthDp: Int, heightDp: Int, round: Boolean): Double {
    val anchor = heightDp * PERSON_ANCHOR.toDouble()
    val reach = if (round) {
        min(widthDp, heightDp) / 2.0 + abs(anchor - heightDp / 2.0)
    } else {
        hypot(widthDp / 2.0, max(anchor, heightDp - anchor))
    }
    return reach * TileDp.value / TILE_SIZE + 4
}

// Lo que queda libre para los nombres, respecto de la flecha.
private fun DrawScope.labelArea(anchor: Offset, blockers: List<Rect>) = LabelArea(
    blocked = blockers.map {
        ScreenBox(
            (it.left - anchor.x).toDouble(),
            (it.top - anchor.y).toDouble(),
            (it.right - anchor.x).toDouble(),
            (it.bottom - anchor.y).toDouble(),
        )
    },
    circleCenter = Point2(0.0, (size.height / 2 - anchor.y).toDouble()),
    radius = (min(size.width, size.height) / 2 - StreetLabelEdge.toPx()).toDouble(),
    personClearance = StreetLabelPersonClearance.toPx().toDouble(),
)

private fun Point2.local(center: Point2, scale: Double) = Point2((x - center.x) * scale, (y - center.y) * scale)

private fun DrawScope.drawTiles(scene: TileScene, scale: Double, tiles: MapTileImages?) {
    for (tile in scene.tiles) {
        val image = tiles?.image(tile) ?: continue
        val origin = tileOrigin(tile).local(scene.center, scale)
        val left = origin.x.roundToInt()
        val top = origin.y.roundToInt()
        // Los bordes se redondean por separado: así dos tiles vecinos no dejan una raya entre ellos.
        val right = (origin.x + TILE_SIZE * scale).roundToInt()
        val bottom = (origin.y + TILE_SIZE * scale).roundToInt()
        drawImage(
            image = image,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(right - left, bottom - top),
            filterQuality = FilterQuality.Low,
        )
    }
}

// Cada nombre se gira como su calle en pantalla, pero nunca más de 90°: siempre se lee derecho.
private fun DrawScope.drawStreetLabels(
    scene: TileScene,
    scale: Double,
    tiles: MapTileImages?,
    textMeasurer: TextMeasurer,
    area: LabelArea,
) {
    val streets = tiles?.streets(scene.tiles).orEmpty()
    if (streets.isEmpty()) return
    val height = textMeasurer.measure("Calle", StreetLabelStyle).size.height.toDouble()
    val labels = placeStreetLabels(
        streets = streets,
        center = scene.center,
        headingDeg = scene.headingDeg,
        scale = scale,
        area = area,
        width = { textMeasurer.measure(it, StreetLabelStyle).size.width.toDouble() },
        height = height,
        maxLabels = MAX_STREET_LABELS,
    )
    val halo = Stroke(StreetLabelHalo.toPx(), join = StrokeJoin.Round)
    for (label in labels) {
        val layout = textMeasurer.measure(label.name, StreetLabelStyle)
        val center = label.position.toOffset()
        val topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
        rotate(label.angleDeg.toFloat(), pivot = center) {
            drawText(layout, color = Background, topLeft = topLeft, drawStyle = halo)
            // El relleno se pide explícito: si no, el texto hereda el trazo del halo y sale hueco.
            drawText(layout, color = TextSecondary, topLeft = topLeft, drawStyle = Fill)
        }
    }
}

private fun DrawScope.drawRouteOnTiles(scene: TileScene, status: NavigationStatus, scale: Double) {
    val walked = listOf(scene.walked.map { it.local(scene.center, scale) })
    val remaining = listOf(scene.remaining.map { it.local(scene.center, scale) })
    if (status == NavigationStatus.OFF_ROUTE) drawLines(remaining, StatusOffRoute, GpsOffRouteWidth)
    drawLines(walked, Primary.copy(alpha = WALKED_ALPHA), GpsRouteWidth)
    drawLines(remaining, TextPrimary, GpsRouteBorderWidth)
    drawLines(remaining, Primary, GpsRouteWidth)
    scene.destination?.let { drawDestination(it.local(scene.center, scale).toOffset()) }
    scene.nextTurn?.let { drawTurn(it.local(scene.center, scale).toOffset()) }
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
