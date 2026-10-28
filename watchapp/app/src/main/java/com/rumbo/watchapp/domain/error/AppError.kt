package com.rumbo.watchapp.domain.error

/** Error con un mensaje en español listo para mostrar (y leer) al usuario. */
open class AppError(message: String) : Exception(message)

/** El backend avisó que se agotó la cuota de un servicio gratuito (HTTP 429, QUOTA_EXHAUSTED). */
class QuotaExhaustedError(message: String) : AppError(message)
