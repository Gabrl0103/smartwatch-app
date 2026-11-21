package com.rumbo.watchapp.presentation.components

import kotlin.math.cos
import kotlin.math.sin

// Geometría del dial (bisel de marcas y anillo), en funciones puras para probarla con JUnit.
// Las medidas vienen del diseño, hecho sobre la pantalla redonda de 454 px (radio 227): se
// guardan como fracción del radio para que el dial se vea igual en cualquier reloj redondo.

/** Marcas del bisel: una cada 6°, como los minutos de un reloj. */
const val TICK_COUNT = 60

/** Cada cuántas marcas va una más larga. */
const val MAJOR_TICK_EVERY = 5

private const val DESIGN_RADIUS = 227f

/** Radio del anillo de progreso (203 de 227). */
const val RING_RADIUS_FRACTION = 203f / DESIGN_RADIUS

/** Las marcas cortas van del radio 214 al 222; las largas empiezan en 208. */
const val TICK_OUTER_FRACTION = 222f / DESIGN_RADIUS
const val MINOR_TICK_INNER_FRACTION = 214f / DESIGN_RADIUS
const val MAJOR_TICK_INNER_FRACTION = 208f / DESIGN_RADIUS

/**
 * Hueco arriba, centrado en las 12, para que ni el anillo ni las marcas pasen por debajo de la
 * hora. El anillo empieza a la derecha del hueco y avanza en sentido horario.
 */
const val TOP_GAP_DEGREES = 56f
const val RING_START_DEGREES = TOP_GAP_DEGREES / 2
const val RING_SWEEP_DEGREES = 360f - TOP_GAP_DEGREES

/** Un punto en píxeles, con el origen arriba a la izquierda (como el Canvas). */
data class DialPoint(val x: Float, val y: Float)

/** Una marca del bisel: su ángulo (grados, horario desde las 12) y sus dos extremos. */
data class TickMark(val index: Int, val major: Boolean, val angleDegrees: Float, val inner: DialPoint, val outer: DialPoint)

/** Punto a [radius] del centro ([centerX], [centerY]) en el ángulo [degrees], horario desde las 12. */
fun pointOnDial(centerX: Float, centerY: Float, radius: Float, degrees: Float): DialPoint {
    val radians = Math.toRadians(degrees.toDouble())
    return DialPoint(
        x = centerX + radius * sin(radians).toFloat(),
        y = centerY - radius * cos(radians).toFloat(),
    )
}

/** Ángulo de la marca [index]: la 0 en las 12 y cada una 6° más en sentido horario. */
fun tickAngle(index: Int): Float = index * 360f / TICK_COUNT

/** Si la marca [index] cae en el hueco de la hora y no se dibuja. */
fun isTickHidden(index: Int): Boolean {
    val angle = tickAngle(index)
    val fromTop = minOf(angle, 360f - angle)
    return fromTop < TOP_GAP_DEGREES / 2
}

/**
 * Las marcas visibles del bisel de radio [radius] (la mitad del lado de la pantalla) con centro
 * en ([centerX], [centerY]). Las de [MAJOR_TICK_EVERY] en [MAJOR_TICK_EVERY] son más largas.
 */
fun tickMarks(centerX: Float, centerY: Float, radius: Float): List<TickMark> =
    (0 until TICK_COUNT).filterNot(::isTickHidden).map { i ->
        val major = i % MAJOR_TICK_EVERY == 0
        val angle = tickAngle(i)
        val inner = radius * if (major) MAJOR_TICK_INNER_FRACTION else MINOR_TICK_INNER_FRACTION
        TickMark(
            index = i,
            major = major,
            angleDegrees = angle,
            inner = pointOnDial(centerX, centerY, inner, angle),
            outer = pointOnDial(centerX, centerY, radius * TICK_OUTER_FRACTION, angle),
        )
    }

/**
 * Ángulo de la punta del anillo (donde va el punto brillante) para un avance de 0 a 1: en 0
 * está al borde derecho del hueco de la hora y en 1 da la vuelta hasta el borde izquierdo.
 */
fun progressDotAngle(progress: Float): Float =
    RING_START_DEGREES + RING_SWEEP_DEGREES * progress.coerceIn(0f, 1f)

// Margen para que una marca justo en la punta cuente como encendida pese al redondeo.
private const val ANGLE_EPSILON = 1e-3f

/**
 * Si la marca [index] se enciende con un avance de [progress]: las visibles que ya quedaron
 * detrás de la punta del anillo. Sin avance no se enciende ninguna; con 1, todas.
 */
fun isTickLit(index: Int, progress: Float): Boolean {
    if (progress <= 0f || isTickHidden(index)) return false
    return tickAngle(index) <= progressDotAngle(progress) + ANGLE_EPSILON
}

/** Cuántas marcas se encienden con [progress]. */
fun litTickCount(progress: Float): Int = (0 until TICK_COUNT).count { isTickLit(it, progress) }

/** El contenido queda 24 (de 227) por dentro del anillo: en el reloj de 454 px, 24 px (12 dp). */
const val CONTENT_CLEARANCE_FRACTION = 24f / DESIGN_RADIUS

/**
 * Hasta dónde puede llegar el contenido, medido desde el centro: el borde interior del anillo
 * (de grosor [ringStroke]) menos la holgura de [CONTENT_CLEARANCE_FRACTION]. Todo en la misma
 * unidad (px o dp); [radius] es media pantalla.
 */
fun dialContentRadius(radius: Float, ringStroke: Float): Float =
    radius * (RING_RADIUS_FRACTION - CONTENT_CLEARANCE_FRACTION) - ringStroke / 2
