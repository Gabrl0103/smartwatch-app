package com.rumbo.watchapp.domain.port

interface AssistantRepository {
    /**
     * Respuesta corta (hasta dos frases) a [question]. [context] describe la ruta en curso para
     * que pueda responder "¿cuánto falta?"; vacío si no se está navegando.
     */
    suspend fun ask(question: String, context: String): String
}
