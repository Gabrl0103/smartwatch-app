package com.rumbo.watchapp.infrastructure.network

import com.rumbo.watchapp.domain.error.AppError
import com.rumbo.watchapp.domain.port.AssistantRepository
import org.json.JSONObject

/** Pregunta al asistente con POST /voice/ask. Los errores llegan con el mensaje del backend. */
object AssistantApiClient : AssistantRepository {

    override suspend fun ask(question: String, context: String): String {
        val request = JSONObject().put("question", question).put("context", context)
        val body = backendPostJson("/voice/ask", request).requireOk()
        val answer = parseJson(body) { it.optString("answer") }.trim()
        if (answer.isEmpty()) throw AppError("El asistente no respondió, intenta de nuevo")
        return answer
    }
}
