package com.rumbo.watchapp.presentation.screens

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

// Tras este tiempo sin tocar la pantalla ni cambiar de estado, se atenúa para ahorrar batería.
private const val DIM_AFTER_MS = 15_000L

// Brillo atenuado de la ventana (0 a 1): se sigue leyendo de cerca, sin encandilar ni gastar tanto.
private const val DIM_BRIGHTNESS = 0.3f

/**
 * Mantiene la pantalla encendida mientras este composable esté visible. Con [dimmed] baja el brillo
 * de la ventana a [DIM_BRIGHTNESS]; si no (o al salir), vuelve al brillo del sistema.
 */
@Composable
fun KeepScreenOn(dimmed: Boolean = false) {
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }
    LaunchedEffect(window, dimmed) {
        window?.setBrightness(if (dimmed) DIM_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }
}

/**
 * Pantalla encendida mientras se navega, atenuada tras [DIM_AFTER_MS] sin uso. Vuelve al brillo
 * normal al tocar [content] (sin consumir el toque) o cuando cambia [wakeKey], p. ej. el estado:
 * un giro, un desvío o la llegada tienen que verse bien.
 */
@Composable
fun ScreenOnWhileNavigating(wakeKey: Any?, content: @Composable () -> Unit) {
    var touches by remember { mutableIntStateOf(0) }
    var dimmed by remember { mutableStateOf(false) }
    LaunchedEffect(wakeKey, touches) {
        dimmed = false
        delay(DIM_AFTER_MS)
        dimmed = true
    }
    KeepScreenOn(dimmed)
    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    touches++
                }
            }
        },
    ) {
        content()
    }
}

private fun Window.setBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}
