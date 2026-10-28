package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon

/**
 * Íconos de estado, todos de trazo (2 px en una grilla de 24) para que se lean igual de gruesos.
 * [description] es lo que dice TalkBack si el ícono no va acompañado de otro texto.
 */
enum class StateIconKind(val description: String, vararg paths: String) {
    MIC(
        "Micrófono",
        "M9 5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0z",
        "M5.5 11a6.5 6.5 0 0 0 13 0",
        "M12 17.5V21",
    ),
    LEFT("Gira a la izquierda", "M17 20v-8a4 4 0 0 0-4-4H5", "M9 4L5 8l4 4"),
    RIGHT("Gira a la derecha", "M7 20v-8a4 4 0 0 1 4-4h8", "M15 4l4 4-4 4"),
    STRAIGHT("Sigue recto", "M12 20V4", "M6 10l6-6 6 6"),
    U_TURN("Vuelta en U", "M16 20V10a4 4 0 0 0-8 0v7", "M4 13l4 4 4-4"),
    ROUNDABOUT(
        "Rotonda",
        "M7.5 9.5a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0-9 0",
        "M12 14v7",
        "M16.5 9.5H21",
        "M18 6.5l3 3-3 3",
    ),
    WARNING("Advertencia", "M12 3.5L2.5 20h19z", "M12 9.5v4.5", "M12 17.25v0.01"),
    NO_SIGNAL(
        "Sin señal GPS",
        "M12 21c-3.5-3.5-6.5-7-6.5-11a6.5 6.5 0 0 1 13 0c0 4-3 7.5-6.5 11z",
        "M10 10a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
        "M3 3l18 18",
    ),
    ARRIVED("Llegaste", "M4.5 12.5l5 5L19.5 7");

    val vector: ImageVector by lazy { strokeIcon(name, paths) }
}

private fun strokeIcon(name: String, paths: Array<out String>): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Ícono grande de estado (≈ 72 dp) en el color del estado. */
@Composable
fun StateIcon(
    kind: StateIconKind,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    contentDescription: String? = kind.description,
) {
    Icon(
        imageVector = kind.vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = color,
    )
}
