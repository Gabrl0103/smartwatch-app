package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.R
import com.rumbo.watchapp.presentation.theme.Border
import com.rumbo.watchapp.presentation.theme.OnColor
import com.rumbo.watchapp.presentation.theme.Primary
import com.rumbo.watchapp.presentation.theme.StatusOffRoute
import com.rumbo.watchapp.presentation.theme.Surface
import com.rumbo.watchapp.presentation.theme.TextPrimary
import com.rumbo.watchapp.presentation.theme.TextSecondary

/** Alto mínimo de todo lo que se toca: 52 dp, por encima de los 48 dp de accesibilidad. */
val TouchMinHeight = 52.dp

private val PillBorder = BorderStroke(1.dp, Border)

/** PRIMARY: la acción principal de la pantalla; DANGER: borrar. El texto sobre color va oscuro. */
enum class PillStyle(val container: Color, val content: Color, val secondaryContent: Color) {
    PRIMARY(Primary, OnColor, OnColor),
    SECONDARY(Surface, TextPrimary, TextSecondary),
    DANGER(StatusOffRoute, OnColor, OnColor),
}

/** Botón grande en píldora: a lo ancho, mínimo 52 dp de alto, borde de 1 dp y sin sombra. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.SECONDARY,
    secondaryLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = TouchMinHeight),
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = style.container,
            contentColor = style.content,
            secondaryContentColor = style.secondaryContent,
        ),
        border = PillBorder,
        secondaryLabel = secondaryLabel?.let {
            {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Botón redondo del micrófono (≈ 108 dp), la acción principal de Inicio. */
@Composable
fun MicButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(108.dp),
        shapes = IconButtonDefaults.shapes(CircleShape),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary, contentColor = OnColor),
        border = PillBorder,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = contentDescription,
            modifier = Modifier.size(52.dp),
        )
    }
}

/** Interruptor en píldora; [label] debe decir el estado ("Voz: sí"), no solo el color. */
@Composable
fun PillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchMinHeight)
            .border(PillBorder, CircleShape),
        shape = CircleShape,
        colors = SwitchButtonDefaults.switchButtonColors(
            checkedContainerColor = Surface,
            checkedContentColor = TextPrimary,
            checkedSecondaryContentColor = TextSecondary,
            checkedThumbColor = OnColor,
            checkedThumbIconColor = Primary,
            checkedTrackColor = Primary,
            checkedTrackBorderColor = Primary,
            uncheckedContainerColor = Surface,
            uncheckedContentColor = TextPrimary,
            uncheckedSecondaryContentColor = TextSecondary,
            uncheckedThumbColor = TextSecondary,
            uncheckedTrackColor = Surface,
            uncheckedTrackBorderColor = TextSecondary,
        ),
        secondaryLabel = secondaryLabel?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
