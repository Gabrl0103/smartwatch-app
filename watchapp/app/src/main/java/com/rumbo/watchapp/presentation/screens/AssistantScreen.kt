package com.rumbo.watchapp.presentation.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.rumbo.watchapp.presentation.components.Dial
import com.rumbo.watchapp.presentation.components.PillButton
import com.rumbo.watchapp.presentation.components.RingStyle
import com.rumbo.watchapp.presentation.components.RoundAction
import com.rumbo.watchapp.presentation.components.RoundActionStyle
import com.rumbo.watchapp.presentation.components.SpeakingBars
import com.rumbo.watchapp.presentation.components.StateIconKind
import com.rumbo.watchapp.presentation.components.StatusPill
import com.rumbo.watchapp.presentation.theme.AssistantBubbleBottom
import com.rumbo.watchapp.presentation.theme.AssistantBubbleTop
import com.rumbo.watchapp.presentation.theme.AssistantChip
import com.rumbo.watchapp.presentation.theme.Border
import com.rumbo.watchapp.presentation.theme.Surface
import com.rumbo.watchapp.presentation.theme.TextSecondary
import com.rumbo.watchapp.presentation.theme.TickMajorAssistant
import com.rumbo.watchapp.presentation.theme.TickMinorAssistant

private val QuestionShape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val AnswerShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)

/**
 * Asistente: insignia "Asistente", lo que preguntó la persona en una burbuja gris, la respuesta
 * en burbujas moradas, una por frase (también se dice en voz alta, y TalkBack la anuncia), y
 * "Hablando" con las barras mientras suena (su lugar está reservado: nada se mueve al terminar).
 * Debajo el micrófono, "Preguntar otra vez", y [backLabel] ("Volver a la ruta" o "Inicio").
 */
@Composable
fun AssistantScreen(
    question: String,
    answer: String,
    speaking: Boolean,
    onAskAgain: () -> Unit,
    onBack: () -> Unit,
    backLabel: String,
) {
    RumboScreen(
        paneTitle = "Asistente",
        dial = {
            Dial(AssistantChip, RingStyle.IDLE, majorColor = TickMajorAssistant, minorColor = TickMinorAssistant)
        },
    ) {
        StatusPill("Asistente", AssistantChip, icon = StateIconKind.SPARKLE)
        Text(
            text = question,
            modifier = Modifier
                .semantics { contentDescription = "Preguntaste: $question" }
                .background(Surface, QuestionShape)
                .border(1.dp, Border, QuestionShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Una burbuja por frase: cada una entra entera en el círculo y se lee al desplazar, en vez
        // de un bloque largo cortado por el borde. TalkBack lee y anuncia la respuesta completa en
        // la primera.
        val sentences = answerSentences(answer)
        sentences.forEachIndexed { i, sentence ->
            AnswerBubble(
                text = sentence,
                modifier = if (i == 0) {
                    Modifier.clearAndSetSemantics {
                        contentDescription = answer
                        liveRegion = LiveRegionMode.Polite
                    }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            )
        }
        val talking by animateFloatAsState(if (speaking) 1f else 0f, label = "habla")
        // Su lugar está reservado: al terminar de hablar no se mueve nada.
        Row(
            modifier = Modifier.graphicsLayer { alpha = talking }.clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeakingBars(AssistantChip, maxHeight = 16.dp, barWidth = 3.dp, barGap = 2.dp)
            Text("Hablando", style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp), color = TextSecondary)
        }
        RoundAction(StateIconKind.MIC, "Preguntar otra vez", onAskAgain, style = RoundActionStyle.ASSISTANT)
        PillButton(backLabel, onClick = onBack)
    }
}

/** Las frases de [answer], cortadas tras punto, signo de cierre o puntos suspensivos. */
internal fun answerSentences(answer: String): List<String> =
    answer.trim().split(Regex("""(?<=[.!?…])\s+""")).filter { it.isNotBlank() }.ifEmpty { listOf(answer) }

@Composable
private fun AnswerBubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(Brush.verticalGradient(listOf(AssistantBubbleTop, AssistantBubbleBottom)), AnswerShape)
            .border(1.dp, AssistantChip.copy(alpha = 0.45f), AnswerShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
        textAlign = TextAlign.Center,
    )
}
