package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decide qué hacer con los toques de una pantalla que reconoce el doble toque en cualquier parte.
 * Va aparte del gesto para poder probarlo sin Compose.
 *
 * Un botón actúa al soltar el primer toque: sin más, un doble toque que cayera sobre "Terminar"
 * terminaría la ruta. Por eso los botones de la pantalla pasan por [click], que espera
 * [timeoutMs] por si llega un segundo toque y, si llega, no hace nada.
 */
class DoubleTapGate(
    private val scope: CoroutineScope,
    private val timeoutMs: Long,
    private val onDoubleTap: () -> Unit,
) {
    private var lastTapUpMs: Long? = null
    private var pendingClick: Job? = null

    /** true si este toque llega a tiempo para ser el segundo. Se decide al bajar, no al soltar. */
    fun onDown(downMs: Long): Boolean {
        val second = lastTapUpMs?.let { downMs - it <= timeoutMs } == true
        if (second) pendingClick?.cancel()
        return second
    }

    fun onTap(second: Boolean, upMs: Long) {
        lastTapUpMs = if (second) null else upMs
        if (second) onDoubleTap()
    }

    fun click(action: () -> Unit) {
        pendingClick?.cancel()
        pendingClick = scope.launch {
            delay(timeoutMs)
            action()
        }
    }
}

/**
 * Doble toque en cualquier parte, también encima de los botones: se escucha en la pasada inicial,
 * antes que ellos, y el segundo toque se consume para que el botón no lo reciba. Arrastrar para
 * desplazar no cuenta. Con TalkBack no llega nada aquí: el lector se queda con los toques y activa
 * lo que tenga enfocado (ver onClick en la semántica de la pantalla).
 */
fun Modifier.doubleTapAnywhere(gate: DoubleTapGate): Modifier = pointerInput(gate) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val second = gate.onDown(down.uptimeMillis)
        val up = awaitTapUp(down, viewConfiguration.touchSlop) ?: return@awaitEachGesture
        if (second) up.consume()
        gate.onTap(second, up.uptimeMillis)
    }
}

private suspend fun AwaitPointerEventScope.awaitTapUp(down: PointerInputChange, touchSlop: Float): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return null
        if ((change.position - down.position).getDistance() > touchSlop) return null
        if (change.changedToUpIgnoreConsumed()) return change
    }
}
