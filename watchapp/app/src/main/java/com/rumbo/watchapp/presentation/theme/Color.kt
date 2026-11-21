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

// Texto sobre botones y fondos de color claro (primario, acento, estados y chip del asistente):
// oscuro, porque el blanco sobre #4F8EFF da 2.9:1 y este da 6.1:1. Sobre los demás pasa de 6:1.
val OnColor = Background

// Asistente de voz. #7C5CFC da 4.4:1 con texto oscuro y 4.0:1 sobre la superficie, menos de
// 4.5:1: se usa solo en rellenos y bordes; el texto y los chips van en #9B82FF (6.5:1).
val Assistant = Color(0xFF7C5CFC)
val AssistantChip = Color(0xFF9B82FF)

// Estados de la navegación
val StatusOnRoute = Primary
val StatusTurnSoon = Accent
val StatusOffRoute = Color(0xFFFF8A3D)
val StatusNoSignal = Color(0xFF8A93A6)
val StatusArrived = Color(0xFF3DDC84)

// Fondo radial de las pantallas: un brillo arriba del centro que se apaga hacia el borde. En
// Desviado y Llegada el brillo toma el tinte del estado (además del texto, nunca en su lugar).
val BackgroundGlow = Color(0xFF141A27)
val BackgroundEdge = Color(0xFF06080C)
val BackgroundGlowOffRoute = Color(0xFF24160F)
val BackgroundGlowArrived = Color(0xFF0F2219)

// Dial: pista del anillo y marcas del bisel apagadas (decorativos, no llevan texto).
val RingTrack = Color(0xFF1B2030)
val TickMajor = Color(0xFF5A6378)
val TickMinor = Color(0xFF2F3544)
val TickMinorOffRoute = Color(0xFF3A2F2A)
val TickMajorNoSignal = Color(0xFF4A5266)
val TickMinorNoSignal = Color(0xFF2A2F3A)
val TickMajorAssistant = Color(0xFF5A5678)
val TickMinorAssistant = Color(0xFF2F2E48)

// Disco de vidrio: degradado radial con un borde blanco al 9 %.
val GlassTop = Color(0xFF242C3C)
val GlassBottom = Color(0xFF131824)
val GlassEdge = Color(0x17FFFFFF)

// Versiones claras para el degradado de los discos y botones de color (arriba más claro).
val PrimaryLight = Color(0xFF7DAEFF)
val ArrivedLight = Color(0xFF6BEBA0)
val AssistantLight = Color(0xFFB7A5FF)

// Burbuja de la respuesta del asistente (el texto va en TextPrimary encima).
val AssistantBubbleTop = Color(0xFF221E3D)
val AssistantBubbleBottom = Color(0xFF181630)
