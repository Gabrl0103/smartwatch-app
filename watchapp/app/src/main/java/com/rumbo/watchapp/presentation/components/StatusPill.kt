package com.rumbo.watchapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.theme.Inter
import com.rumbo.watchapp.presentation.theme.OnColor
import java.util.Locale

/** Alto de la insignia de estado. */
val StatusPillHeight = 28.dp

// Inter 700 a 12 sp con 0,14 em de espaciado, en mayúsculas: se lee como una etiqueta.
private val PillText = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.14.em,
)

private val SPANISH = Locale.forLanguageTag("es")

/**
 * Insignia de estado de 28 dp: rellena del color del estado, con ícono y el nombre en
 * mayúsculas en texto oscuro. El estado siempre va escrito; el color y el ícono lo refuerzan.
 * TalkBack lee [text] tal cual (no en mayúsculas) y, con [announce], lo anuncia cuando cambia.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: StateIconKind? = null,
    announce: Boolean = false,
) {
    Row(
        modifier = modifier
            .heightIn(min = StatusPillHeight)
            .background(color, CircleShape)
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clearAndSetSemantics {
                this.text = AnnotatedString(text)
                if (announce) liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) StateIcon(icon, OnColor, size = 14.dp, contentDescription = null)
        Text(
            text = text.uppercase(SPANISH),
            style = PillText,
            color = OnColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}
