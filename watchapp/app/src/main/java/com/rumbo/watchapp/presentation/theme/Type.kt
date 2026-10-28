package com.rumbo.watchapp.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Typography
import com.rumbo.watchapp.R

// Fuentes empaquetadas en res/font (Google Fonts, licencia OFL en assets/licenses): no dependen
// de un proveedor en línea, así se ven igual en el emulador y sin internet.
val Poppins = FontFamily(
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_bold, FontWeight.Bold),
)

// Escala del reloj: 16 / 20 / 24 / 32 / 40 sp. Ningún texto de la app baja de 16 sp.
private val Size16 = 16.sp
private val Size20 = 20.sp
private val Size24 = 24.sp
private val Size32 = 32.sp
private val Size40 = 40.sp

// Interlineado de 1.25: deja aire para las tildes de las mayúsculas y los signos ¿ ¡.
private fun style(family: FontFamily, weight: FontWeight, size: TextUnit) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, lineHeight = size * 1.25f)

/** Títulos en Poppins (600/700), texto en Inter (400/500/700). */
val RumboTypography = Typography(
    defaultFontFamily = Inter,
    displayLarge = style(Poppins, FontWeight.Bold, Size40),
    displayMedium = style(Poppins, FontWeight.Bold, Size32),
    displaySmall = style(Poppins, FontWeight.Bold, Size24),
    titleLarge = style(Poppins, FontWeight.SemiBold, Size24),
    titleMedium = style(Poppins, FontWeight.SemiBold, Size20),
    titleSmall = style(Poppins, FontWeight.SemiBold, Size16),
    labelLarge = style(Inter, FontWeight.Bold, Size20),
    labelMedium = style(Inter, FontWeight.Bold, Size16),
    labelSmall = style(Inter, FontWeight.Medium, Size16),
    bodyLarge = style(Inter, FontWeight.Normal, Size20),
    bodyMedium = style(Inter, FontWeight.Normal, Size16),
    bodySmall = style(Inter, FontWeight.Normal, Size16),
    bodyExtraSmall = style(Inter, FontWeight.Normal, Size16),
    numeralExtraLarge = style(Inter, FontWeight.Bold, Size40),
    numeralLarge = style(Inter, FontWeight.Bold, Size40),
    numeralMedium = style(Inter, FontWeight.Bold, Size32),
    numeralSmall = style(Inter, FontWeight.Bold, Size24),
    numeralExtraSmall = style(Inter, FontWeight.Bold, Size20),
)
