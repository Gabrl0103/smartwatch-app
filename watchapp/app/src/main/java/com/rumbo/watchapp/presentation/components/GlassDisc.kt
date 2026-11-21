package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.theme.AssistantChip
import com.rumbo.watchapp.presentation.theme.AssistantLight
import com.rumbo.watchapp.presentation.theme.Border
import com.rumbo.watchapp.presentation.theme.GlassBottom
import com.rumbo.watchapp.presentation.theme.GlassEdge
import com.rumbo.watchapp.presentation.theme.GlassTop
import com.rumbo.watchapp.presentation.theme.OnColor
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.PrimaryLight
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.Surface
import com.rumbo.watchapp.presentation.theme.TextPrimary

/** Relleno de un disco: de [top] (arriba, más claro) a [bottom]. El vidrio es el de siempre. */
data class DiscFill(val top: Color, val bottom: Color) {
    companion object {
        val Glass = DiscFill(GlassTop, GlassBottom)
    }
}

// El halo se extiende este tanto más allá del disco y empieza con esta transparencia.
private val HaloSpread = 18.dp
private const val HALO_ALPHA = 0.22f

/**
 * Disco de vidrio: degradado radial (#242C3C → #131824, con la luz arriba), borde blanco al 9 %
 * y un halo del color del estado ([halo]) alrededor. Con [fill] se rellena de color (la llegada,
 * el micrófono). Con [rings] lleva dos aros tenues alrededor (sin señal). Todo quieto.
 */
@Composable
fun GlassDisc(
    size: Dp,
    halo: Color,
    modifier: Modifier = Modifier,
    fill: DiscFill = DiscFill.Glass,
    rings: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2
                val spread = HaloSpread.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to halo.copy(alpha = HALO_ALPHA),
                        1f to Color.Transparent,
                        center = center,
                        radius = r + spread,
                    ),
                    radius = r + spread,
                )
                if (rings) {
                    listOf(10.dp to 0.45f, 20.dp to 0.3f).forEach { (gap, alpha) ->
                        drawCircle(halo.copy(alpha = alpha), radius = r + gap.toPx(), style = Stroke(1.5.dp.toPx()))
                    }
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to fill.top,
                        0.7f to fill.bottom,
                        center = Offset(center.x, this.size.height * 0.3f),
                        radius = r * 1.4f,
                    ),
                    radius = r,
                )
            }
            .border(1.dp, GlassEdge, CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Estilos de [RoundAction]. El ícono y el texto sobre color van oscuros ([OnColor]); sobre el
 * vidrio, claros. DANGER es el naranja de "desviado", para terminar.
 */
enum class RoundActionStyle(val fill: DiscFill?, val iconColor: Color) {
    PRIMARY(DiscFill(PrimaryLight, Primary), OnColor),
    ASSISTANT(DiscFill(AssistantLight, AssistantChip), OnColor),
    SECONDARY(null, TextPrimary),
    DANGER(null, StatusOffRoute),
}

/** Diámetro del círculo de [RoundAction]; con el texto debajo, el área táctil pasa de 48 dp. */
val RoundActionSize = 52.dp

/**
 * Botón redondo con ícono y, debajo, su nombre escrito ([label]): nunca solo un ícono. Todo
 * (círculo y texto) es una sola área táctil, que TalkBack lee como "[label], botón".
 */
@Composable
fun RoundAction(
    icon: StateIconKind,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RoundActionStyle = RoundActionStyle.SECONDARY,
    size: Dp = RoundActionSize,
) {
    Column(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .widthIn(max = 84.dp)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = label),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val circle = Modifier.size(size)
        val iconSize = size * 0.46f
        if (style.fill != null) {
            GlassDisc(size, halo = style.fill.bottom, modifier = circle, fill = style.fill) {
                StateIcon(icon, style.iconColor, size = iconSize, contentDescription = null)
            }
        } else {
            Box(
                modifier = circle
                    .drawBehind { drawCircle(Surface) }
                    .border(1.dp, Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                StateIcon(icon, style.iconColor, size = iconSize, contentDescription = null)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, lineHeight = 17.sp),
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
