package com.rumbo.watchapp.domain.error

const val NO_CONNECTION_MESSAGE = "Sin conexión. Revisa el internet del reloj"
const val SERVER_WAKING_MESSAGE = "El servidor está despertando, espera un momento"
const val GPS_UNAVAILABLE_MESSAGE = "No encuentro señal GPS. Sal a un lugar abierto e intenta de nuevo"
const val TOO_FAR_MESSAGE = "Estás muy lejos del destino"

/** Error con un mensaje en español listo para mostrar (y leer) al usuario. */
open class AppError(message: String) : Exception(message)

/**
 * El backend rechazó la petición por el límite de un servicio gratuito (HTTP 429, QUOTA_EXHAUSTED).
 * [retryAfterMs] es cuánto esperar para volver a pedir, si el backend lo dice.
 */
class QuotaExhaustedError(message: String, val retryAfterMs: Long? = null) : AppError(message)

/** El reloj no tiene internet: no se pudo resolver ni conectar con el servidor. */
class NoConnectionError : AppError(NO_CONNECTION_MESSAGE)

/** Render tiene el servidor dormido: tarda en contestar o responde 502/503/504 mientras arranca. */
class ServerWakingError : AppError(SERVER_WAKING_MESSAGE)

/** No llegó una lectura GPS reciente y precisa a tiempo para usarla como origen de la ruta. */
class GpsUnavailableError : AppError(GPS_UNAVAILABLE_MESSAGE)

/** El destino queda a más de [com.rumbo.watchapp.domain.navigation.MAX_ROUTE_DISTANCE_M]. */
class TooFarError : AppError(TOO_FAR_MESSAGE)
