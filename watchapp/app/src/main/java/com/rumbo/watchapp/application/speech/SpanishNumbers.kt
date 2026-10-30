package com.rumbo.watchapp.application.speech

private val UNITS = listOf(
    "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
    "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho",
    "diecinueve", "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco",
    "veintiséis", "veintisiete", "veintiocho", "veintinueve",
)
private val TENS = listOf(
    "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa",
)
private val HUNDREDS = listOf(
    "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos",
    "setecientos", "ochocientos", "novecientos",
)

private val EXIT_ORDINALS = listOf(
    "primera", "segunda", "tercera", "cuarta", "quinta", "sexta", "séptima", "octava", "novena", "décima",
)

/** "la segunda salida"; de la once en adelante, "la salida número once". */
fun exitInWords(n: Int): String =
    EXIT_ORDINALS.getOrNull(n - 1)?.let { "la $it salida" } ?: "la salida número ${numberInWords(n)}"

/** 0..999 en palabras ("treinta y uno", "ciento cinco"); fuera de ese rango, en cifras. */
fun numberInWords(n: Int): String = when {
    n !in 0..999 -> n.toString()
    n < 30 -> UNITS[n]
    n < 100 -> TENS[n / 10] + if (n % 10 == 0) "" else " y ${UNITS[n % 10]}"
    n == 100 -> "cien"
    else -> HUNDREDS[n / 100] + if (n % 100 == 0) "" else " ${numberInWords(n % 100)}"
}

/** Forma que va antes de un sustantivo masculino: "un metro", "veintiún minutos". */
fun numberBeforeNoun(n: Int): String {
    val words = numberInWords(n)
    return when {
        words.endsWith("veintiuno") -> words.removeSuffix("veintiuno") + "veintiún"
        words.endsWith("uno") -> words.removeSuffix("uno") + "un"
        else -> words
    }
}

/** "un minuto", "veintiséis minutos". */
fun countOf(n: Int, singular: String, plural: String): String =
    if (n == 1) "un $singular" else "${numberBeforeNoun(n)} $plural"
