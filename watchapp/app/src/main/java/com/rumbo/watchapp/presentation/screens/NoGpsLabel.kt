package com.rumbo.watchapp.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.theme.StatusNoSignal

/** Aviso de que no hay permiso de ubicación: la ruta sale del centro de Pasto. */
@Composable
fun NoGpsLabel() {
    Text(
        text = "Sin GPS",
        style = MaterialTheme.typography.labelMedium,
        color = StatusNoSignal,
        textAlign = TextAlign.Center,
    )
}
