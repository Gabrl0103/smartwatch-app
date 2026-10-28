package com.rumbo.watchapp.presentation.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import com.rumbo.watchapp.presentation.theme.Background

/**
 * Gesto de volver dentro de la app: el "atrás" del sistema (también el de TalkBack) y, hasta
 * Android 15, deslizar de izquierda a derecha; desde Android 16 el sistema convierte ese gesto en
 * "atrás". Sin [onBack] no se intercepta nada y el gesto cierra la app.
 */
@Composable
fun BackGesture(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    if (onBack == null || Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        content()
        return
    }
    BasicSwipeToDismissBox(onDismissed = onBack) { isBackground ->
        if (isBackground) Box(Modifier.fillMaxSize().background(Background)) else content()
    }
}
