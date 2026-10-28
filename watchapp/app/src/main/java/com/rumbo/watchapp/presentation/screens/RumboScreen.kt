package com.rumbo.watchapp.presentation.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.ScreenScaffold
import com.rumbo.watchapp.presentation.components.RingStrokeWidth
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// Lo que el contenido se separa del borde cuando hay anillo: el anillo y un poco de aire.
private val RingInset = RingStrokeWidth + 6.dp
private val ItemSpacing = 8.dp

// Lo más que se achica un elemento al pasar por el borde. Solo se atenúa si se achica más de
// FADE_FROM_SCALE, para que un texto apenas reducido se siga leyendo con todo su contraste.
private const val MIN_EDGE_SCALE = 0.6f
private const val FADE_FROM_SCALE = 0.75f
private const val MIN_EDGE_ALPHA = 0.5f

/**
 * Márgenes en proporción a la pantalla. En la redonda, 10 % a los lados y 14 % arriba y abajo:
 * así una píldora a lo ancho cabe entera en el círculo en la primera y en la última posición, y
 * lo primero queda debajo de la hora. En la cuadrada bastan los márgenes de Wear. [ring] se suma
 * a los lados y abajo; arriba no hace falta porque el anillo deja libre el hueco de la hora.
 */
@Composable
private fun screenPadding(ring: Dp): PaddingValues {
    val config = LocalConfiguration.current
    val round = config.isScreenRound
    val horizontal = config.screenWidthDp.dp * (if (round) 0.10f else 0.052f) + ring
    val vertical = config.screenHeightDp.dp * (if (round) 0.14f else 0.12f)
    return PaddingValues(start = horizontal, top = vertical, end = horizontal, bottom = vertical + ring)
}

/**
 * Contenedor de todas las pantallas: centra el contenido si cabe y, si no, se desplaza con el
 * dedo o la corona; la hora se esconde al desplazar. [paneTitle] es lo que anuncia TalkBack al
 * cambiar de pantalla. Con [edgeRing] dibuja el anillo en el borde y aleja el contenido de él.
 */
@Composable
fun RumboScreen(
    paneTitle: String,
    modifier: Modifier = Modifier,
    edgeRing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val ring = if (edgeRing != null) RingInset else 0.dp
    val padding = screenPadding(ring)
    val body: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        edgeRing?.invoke()
        RoundColumn(
            scrollState = scrollState,
            contentPadding = contentPadding,
            edgeInset = ring,
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
    if (edgeRing == null) {
        ScreenScaffold(scrollState = scrollState, modifier = modifier, contentPadding = padding, content = body)
    } else {
        // El anillo ocupa el borde: ahí no cabe además el indicador de desplazamiento.
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
 * o de abajo se achica (y se atenúa) lo justo para entrar en el círculo, como en las listas de
 * Wear, en vez de quedar cortado (o encima del anillo, a [edgeInset] del borde). El modificador
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
        val contentHeight = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        val width = constraints.maxWidth
        // Con fillMaxSize antes del scroll, minHeight es el alto visible: si sobra, se centra.
        val height = max(contentHeight, constraints.minHeight)
        // La pantalla entera es la ventana; el contenido empieza tras el margen de arriba.
        val screenWidth = config.screenWidthDp.dp.toPx()
        val screenHeight = config.screenHeightDp.dp.toPx()
        val radius = min(screenWidth, screenHeight) / 2 - edgeInset.toPx()
        val centerY = screenHeight / 2
        val paddingTop = contentPadding.calculateTopPadding().toPx()
        layout(width, height) {
            var y = (height - contentHeight) / 2
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
                        scaleX = scale
                        scaleY = scale
                        val fade = ((scale - MIN_EDGE_SCALE) / (FADE_FROM_SCALE - MIN_EDGE_SCALE)).coerceIn(0f, 1f)
                        alpha = MIN_EDGE_ALPHA + (1 - MIN_EDGE_ALPHA) * fade
                    }
                }
                y += placeable.height + gap
            }
        }
    }
}

/**
 * Escala (entre [MIN_EDGE_SCALE] y 1) con la que un elemento centrado en horizontal, a
 * [centerOffset] px del centro de la pantalla, cabe en el círculo de radio [radius]. El elemento
 * se toma como un rectángulo de esquinas redondeadas (radio = mitad del lado menor), como las
 * píldoras y el texto centrado.
 */
private fun fitScale(halfWidth: Float, centerOffset: Float, halfHeight: Float, radius: Float): Float {
    fun fits(scale: Float): Boolean {
        val w = halfWidth * scale
        val h = halfHeight * scale
        val corner = min(w, h)
        val farthest = hypot(w - corner, abs(centerOffset) + h - corner) + corner
        return farthest <= radius
    }
    if (fits(1f)) return 1f
    if (!fits(MIN_EDGE_SCALE)) return MIN_EDGE_SCALE
    var low = MIN_EDGE_SCALE
    var high = 1f
    repeat(8) {
        val mid = (low + high) / 2
        if (fits(mid)) low = mid else high = mid
    }
    return low
}
