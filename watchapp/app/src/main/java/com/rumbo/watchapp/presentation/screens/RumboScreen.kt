package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.ScreenScaffold
import com.rumbo.watchapp.presentation.components.RingStrokeWidth
import com.rumbo.watchapp.presentation.components.dialContentRadius
import com.rumbo.watchapp.presentation.theme.Background
import com.rumbo.watchapp.presentation.theme.BackgroundEdge
import com.rumbo.watchapp.presentation.theme.BackgroundGlow
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val ItemSpacing = 8.dp

// Lo más que se achica un elemento al pasar por el borde. Nunca se atenúa: el texto conserva su
// contraste de 4,5:1. Lo que ni así cabe en el círculo no se dibuja hasta que se desplace hacia
// adentro: nada asoma cortado por el borde redondo ni pisa el anillo.
private const val MIN_EDGE_SCALE = 0.6f

/**
 * Márgenes en proporción a la pantalla. En la redonda, 10 % a los lados y 14 % arriba y abajo:
 * así una píldora a lo ancho cabe entera en el círculo en la primera y en la última posición, y
 * lo primero queda debajo de la hora. En la cuadrada bastan los márgenes de Wear. Con [dial], el
 * contenido va dentro del círculo de radio [dialContentRadius]: a 24 (de 227) del anillo por todos
 * lados (y, arriba, por debajo de la hora).
 */
@Composable
private fun screenPadding(dial: Boolean): PaddingValues {
    if (dial) return PaddingValues(dialInset())
    val config = LocalConfiguration.current
    val round = config.isScreenRound
    val horizontal = config.screenWidthDp.dp * (if (round) 0.10f else 0.052f)
    val vertical = config.screenHeightDp.dp * (if (round) 0.14f else 0.12f)
    return PaddingValues(start = horizontal, top = vertical, end = horizontal, bottom = vertical)
}

/** Distancia del borde de la pantalla al círculo donde va el contenido cuando hay dial. */
@Composable
private fun dialInset(): Dp {
    val config = LocalConfiguration.current
    val radius = min(config.screenWidthDp, config.screenHeightDp) / 2f
    return (radius - dialContentRadius(radius, RingStrokeWidth.value)).dp
}

/**
 * Fondo de todas las pantallas: degradado radial #141A27 → #0B0E14 → #06080C con la luz un poco
 * arriba del centro. [glow] cambia la luz del centro: el tinte de Desviado y de Llegada.
 */
@Composable
fun RumboBackground(modifier: Modifier = Modifier, glow: Color = BackgroundGlow) {
    Canvas(modifier.fillMaxSize().clearAndSetSemantics {}) {
        drawRect(
            Brush.radialGradient(
                0f to glow,
                0.55f to Background,
                1f to BackgroundEdge,
                center = Offset(size.width / 2, size.height * 0.38f),
                radius = size.minDimension * 0.8f,
            ),
        )
    }
}

/**
 * Contenedor de todas las pantallas: centra el contenido si cabe y, si no, se desplaza con el
 * dedo o la corona; la hora se esconde al desplazar. [paneTitle] es lo que anuncia TalkBack al
 * cambiar de pantalla. Detrás va el fondo radial con la luz [glow]. Con [dial] dibuja el bisel y
 * el anillo en el borde y deja el contenido por dentro del anillo ([dialContentRadius]).
 * [background] va encima del fondo, a pantalla completa (el mapa). Cuando cambia
 * [resetScrollKey] (el estado de la ruta) vuelve arriba: lo nuevo se ve entero, debajo de la
 * hora, y no queda corrido por lo que se había desplazado antes.
 */
@Composable
fun RumboScreen(
    paneTitle: String,
    modifier: Modifier = Modifier,
    glow: Color = BackgroundGlow,
    dial: (@Composable () -> Unit)? = null,
    background: (@Composable () -> Unit)? = null,
    resetScrollKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(resetScrollKey) { if (scrollState.value != 0) scrollState.scrollTo(0) }
    val inset = if (dial != null) dialInset() else 0.dp
    val padding = screenPadding(dial != null)
    val body: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        RumboBackground(glow = glow)
        background?.invoke()
        dial?.invoke()
        RoundColumn(
            scrollState = scrollState,
            contentPadding = contentPadding,
            edgeInset = inset,
            modifier = Modifier
                .fillMaxSize()
                .semantics { this.paneTitle = paneTitle }
                .requestFocusOnHierarchyActive()
                .rotaryScrollable(RotaryScrollableDefaults.behavior(scrollState), remember { FocusRequester() })
                .verticalScroll(scrollState)
                .padding(contentPadding),
            content = content,
        )
    }
    if (dial == null && background == null) {
        ScreenScaffold(scrollState = scrollState, modifier = modifier, contentPadding = padding, content = body)
    } else {
        // El dial o el mapa ocupan el borde: ahí no cabe además el indicador de desplazamiento.
        ScreenScaffold(
            scrollState = scrollState,
            modifier = modifier,
            contentPadding = padding,
            scrollIndicator = null,
            content = body,
        )
    }
}

/**
 * Columna centrada con separación fija. En pantalla redonda, lo que pasa por el borde de arriba
 * o de abajo se achica lo justo para entrar en el círculo, como en las listas de Wear, en vez de
 * quedar cortado (el círculo queda a [edgeInset] del borde: con dial, por dentro del anillo); lo
 * que ni achicado cabe espera oculto. No se atenúa, para no perder contraste. El modificador
 * debe incluir el desplazamiento y [contentPadding].
 */
@Composable
private fun RoundColumn(
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    edgeInset: Dp,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val config = LocalConfiguration.current
    val round = config.isScreenRound
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = ItemSpacing.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val itemsHeight = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        val width = constraints.maxWidth
        // La pantalla entera es la ventana; el contenido empieza tras el margen de arriba.
        val screenWidth = config.screenWidthDp.dp.toPx()
        val screenHeight = config.screenHeightDp.dp.toPx()
        val radius = min(screenWidth, screenHeight) / 2 - edgeInset.toPx()
        val centerY = screenHeight / 2
        val paddingTop = contentPadding.calculateTopPadding().toPx()
        val paddingBottom = contentPadding.calculateBottomPadding().toPx()
        // Lo primero (la insignia de estado) nunca queda cortado: si arriba del todo no cabe en el
        // círculo ni achicado, todo baja lo justo para que entre.
        val first = placeables.firstOrNull()
        val drop = if (round && first != null) {
            edgeItemShift(first.width / 2f * MIN_EDGE_SCALE, first.height / 2f, radius, centerY - paddingTop).toInt()
        } else {
            0
        }
        // Y lo último (los botones) puede subir, al desplazar hasta el final, hasta caber entero:
        // sin este espacio quedaba siempre achicado contra el borde de abajo (y performScrollTo
        // nunca lo daba por visible y desplazaba sin fin).
        val last = placeables.lastOrNull()
        val tail = if (round && last != null) {
            edgeItemShift(last.width / 2f, last.height / 2f, radius, screenHeight - paddingBottom - centerY).toInt()
        } else {
            0
        }
        // Con fillMaxSize antes del scroll, minHeight es el alto visible: si sobra, se centra.
        val startY = max((constraints.minHeight - itemsHeight) / 2, drop).coerceAtLeast(0)
        val height = max(startY + itemsHeight + tail, constraints.minHeight)
        layout(width, height) {
            var y = startY
            placeables.forEach { placeable ->
                val x = (width - placeable.width) / 2
                if (!round) {
                    placeable.place(x, y)
                } else {
                    val top = y
                    placeable.placeWithLayer(x, y) {
                        // Se lee el desplazamiento aquí: al desplazar solo se recalcula la capa.
                        val screenTop = paddingTop + top - scrollState.value
                        val scale = fitScale(
                            halfWidth = placeable.width / 2f,
                            centerOffset = screenTop + placeable.height / 2f - centerY,
                            halfHeight = placeable.height / 2f,
                            radius = radius,
                        )
                        scaleX = scale ?: MIN_EDGE_SCALE
                        scaleY = scale ?: MIN_EDGE_SCALE
                        alpha = if (scale == null) 0f else 1f
                    }
                }
                y += placeable.height + gap
            }
        }
    }
}

/**
 * Cuánto acercar al centro un elemento de mitades [halfWidth] × [halfHeight] pegado a un extremo
 * (arriba o abajo), con su borde exterior a [edgeToCenter] px del centro, para que quepa entero
 * (sin achicarse) en el círculo de radio [radius]. Si es tan ancho que no cabe a ninguna altura,
 * lo lleva al centro.
 */
private fun edgeItemShift(halfWidth: Float, halfHeight: Float, radius: Float, edgeToCenter: Float): Float {
    val corner = min(halfWidth, halfHeight)
    val reach = radius - corner
    val side = halfWidth - corner
    // Lo más lejos del centro que puede estar su centro.
    val farthestCenter = if (side > reach) 0f else sqrt(reach * reach - side * side) - halfHeight + corner
    val centerNow = edgeToCenter - halfHeight
    return (centerNow - farthestCenter).coerceAtLeast(0f)
}

/**
 * Escala (entre [MIN_EDGE_SCALE] y 1) con la que un elemento centrado en horizontal, a
 * [centerOffset] px del centro de la pantalla, cabe en el círculo de radio [radius], o null si
 * ni con la mínima cabe. El elemento se toma como un rectángulo de esquinas redondeadas (radio =
 * mitad del lado menor), como las píldoras y el texto centrado.
 */
private fun fitScale(halfWidth: Float, centerOffset: Float, halfHeight: Float, radius: Float): Float? {
    fun fits(scale: Float): Boolean {
        val w = halfWidth * scale
        val h = halfHeight * scale
        val corner = min(w, h)
        val farthest = hypot(w - corner, abs(centerOffset) + h - corner) + corner
        return farthest <= radius
    }
    if (fits(1f)) return 1f
    if (!fits(MIN_EDGE_SCALE)) return null
    var low = MIN_EDGE_SCALE
    var high = 1f
    repeat(8) {
        val mid = (low + high) / 2
        if (fits(mid)) low = mid else high = mid
    }
    return low
}
