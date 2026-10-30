import base64
import html
import json

import httpx

from domain.errors import ExternalServiceError, QuotaExhaustedError
from domain.models.voice import Intent, VoiceCommand

BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

INTERPRET_PROMPT = (
    "Eres el asistente de voz de una app de navegación a pie para personas ciegas, "
    "en Pasto, Colombia. Escucha el audio en español y responde SOLO con JSON con "
    "estos campos: "
    '"text" (transcripción literal), '
    '"intent" (uno de: navigate, guardar_destino, repetir_instruccion, cancelar_ruta, '
    "cuanto_falta, estado_guardado, ask), "
    '"destination" (nombre del lugar si el intent es navigate, si no null), '
    '"question" (la pregunta si el intent es ask, si no null). '
    "navigate = quiere ir a un lugar. "
    'guardar_destino = quiere guardar el destino actual ("guarda este lugar", "guárdalo"). '
    'repetir_instruccion = pide repetir la última instrucción ("repite", "¿qué dijiste?"). '
    'cancelar_ruta = quiere detener o terminar la ruta ("cancela", "termina la ruta"). '
    'cuanto_falta = pregunta cuánto falta para llegar ("¿cuánto falta?", "¿ya casi llego?"). '
    'estado_guardado = pregunta si el destino actual está guardado ("¿este lugar está '
    'guardado?", "¿ya lo guardé?"). '
    "ask = cualquier otra pregunta. "
    "Si no se entiende nada, devuelve text vacío e intent ask."
)

RESPONSE_SCHEMA = {
    "type": "OBJECT",
    "properties": {
        "text": {"type": "STRING"},
        "intent": {"type": "STRING", "enum": [i.value for i in Intent]},
        "destination": {"type": "STRING", "nullable": True},
        "question": {"type": "STRING", "nullable": True},
    },
    "required": ["text", "intent"],
}

# Lo que la app hace y lo que no: sin esto Gemini promete, por ejemplo, avisar de obstáculos,
# algo peligroso de creer para una persona ciega.
ANSWER_PROMPT = (
    "Eres el asistente de voz de Rumbo, una app de reloj que guía a pie a una "
    "persona ciega en Pasto, Colombia. Rumbo solo hace esto: busca una ruta a pie "
    "hacia un lugar que se pide por voz o desde los lugares guardados, y durante la "
    "ruta usa el GPS para avisar con vibraciones y voz los giros, si la persona se "
    "salió de la ruta (y la recalcula), si se perdió la señal de GPS y la llegada; "
    "también repite la indicación y responde preguntas sobre la ruta. Rumbo NO ve: "
    "no tiene cámara ni detecta obstáculos, escalones, huecos, semáforos, carros ni "
    "personas, y no sabe qué hay alrededor. Nunca digas que puedes hacer algo fuera "
    "de esa lista. Para distancias e indicaciones usa solo el contexto de la ruta; "
    "si la respuesta no está ahí, di con honestidad que no lo sabes, sin inventar. "
    "Si preguntan por cruzar una calle o por obstáculos, recuerda que Rumbo no los "
    "detecta y que use su bastón o pida ayuda. Responde en español, en máximo 2 "
    "frases cortas y claras, sin símbolos ni listas.\n"
    "Contexto de la ruta: {context}\nPregunta: {question}"
)


class GeminiVoiceAssistant:
    def __init__(
        self,
        api_key: str,
        model: str,
        client: httpx.Client | None = None,
    ):
        self._api_key = api_key
        self._model = model
        self._client = client or httpx.Client(timeout=30)

    def interpret_audio(self, audio: bytes, mime_type: str) -> VoiceCommand:
        parts = [
            {"text": INTERPRET_PROMPT},
            {
                "inline_data": {
                    "mime_type": mime_type,
                    "data": base64.b64encode(audio).decode("ascii"),
                }
            },
        ]
        text = self._generate(
            parts,
            {
                "responseMimeType": "application/json",
                "responseSchema": RESPONSE_SCHEMA,
                "temperature": 0,
            },
        )
        return self._parse_command(text)

    def answer_question(self, question: str, context: str) -> str:
        prompt = ANSWER_PROMPT.format(context=context or "sin ruta activa", question=question)
        return _clean(self._generate([{"text": prompt}], {"temperature": 0.3}))

    def _generate(self, parts: list[dict], generation_config: dict) -> str:
        if not self._api_key or not self._model:
            raise ExternalServiceError("Falta configurar GEMINI_API_KEY o GEMINI_MODEL.")
        url = f"{BASE_URL}/{self._model}:generateContent"
        body = {
            "contents": [{"parts": parts}],
            "generationConfig": generation_config,
        }
        try:
            response = self._client.post(
                url, json=body, headers={"x-goog-api-key": self._api_key}
            )
        except httpx.HTTPError as error:
            raise ExternalServiceError("No pude conectar con Gemini.") from error
        if response.status_code == 429:
            raise QuotaExhaustedError(
                "Se agotó la cuota gratuita del asistente de Gemini. Intenta más tarde."
            )
        if response.status_code != 200:
            raise ExternalServiceError(
                f"Gemini respondió con error {response.status_code}."
            )
        try:
            return response.json()["candidates"][0]["content"]["parts"][0]["text"]
        except (KeyError, IndexError, ValueError) as error:
            raise ExternalServiceError("Gemini devolvió una respuesta inesperada.") from error

    @staticmethod
    def _parse_command(text: str) -> VoiceCommand:
        try:
            data = json.loads(text)
            intent = Intent(data["intent"])
        except (ValueError, KeyError, TypeError) as error:
            raise ExternalServiceError("No pude interpretar la respuesta de Gemini.") from error
        return VoiceCommand(
            text=_clean(data.get("text")),
            intent=intent,
            destination=_clean(data.get("destination")) or None,
            question=_clean(data.get("question")) or None,
        )


def _clean(text: str | None) -> str:
    """A veces Gemini escribe las tildes como entidades HTML ("navegaci&oacute;n")."""
    return html.unescape(text or "").strip()
