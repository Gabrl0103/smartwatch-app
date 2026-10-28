package com.rumbo.watchapp.presentation.theme

import androidx.compose.ui.graphics.Color

// Design tokens de Rumbo. Ningún estado se comunica solo con color: siempre va con texto.
val Background = Color(0xFF0B0E14)
val Surface = Color(0xFF151922)
val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFFA0A8B8)
val Primary = Color(0xFF4F8EFF)
val Accent = Color(0xFFFFC857)
val Border = Color(0xFF2A2F3A)

// Texto sobre botones y fondos de color claro (primario, acento y estados): oscuro, porque el
// blanco sobre #4F8EFF da 3.2:1 y este da 6.1:1. Sobre los demás colores pasa de 6:1.
val OnColor = Background

// Estados de la navegación
val StatusOnRoute = Primary
val StatusTurnSoon = Accent
val StatusOffRoute = Color(0xFFFF8A3D)
val StatusNoSignal = Color(0xFF8A93A6)
val StatusArrived = Color(0xFF3DDC84)
