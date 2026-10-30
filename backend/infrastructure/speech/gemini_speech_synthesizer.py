import base64
import io
import math
import re
import wave

import httpx

from domain.errors import ExternalServiceError, QuotaExhaustedError

BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

# Voz prebuilt de Gemini ("clara" según la guía). Los modelos TTS 3.8 leen el texto tal cual,
# así que no se manda ninguna instrucción de estilo: la voz y el idioma del texto dan el acento.
VOICE_NAME = "Iapetus"

# Gemini entrega PCM 16-bit mono; la frecuencia viene en el mimeType (audio/L16;rate=24000).
DEFAULT_SAMPLE_RATE = 24000
SAMPLE_WIDTH_BYTES = 2
CHANNELS = 1

# Sin cuota por hoy no sirve insistir enseguida: esto, si Gemini no dice cuándo se renueva.
DAILY_QUOTA_RETRY_S = 30 * 60


class GeminiSpeechSynthesizer:
    def __init__(
        self,
        api_key: str,
        model: str,
        voice: str = VOICE_NAME,
        client: httpx.Client | None = None,
    ):
        self._api_key = api_key
        self._model = model
        self._voice = voice
        self._client = client or httpx.Client(timeout=30)

    def synthesize(self, text: str) -> bytes:
        if not self._api_key or not self._model:
            raise ExternalServiceError("Falta configurar GEMINI_API_KEY o GEMINI_TTS_MODEL.")
        body = {
            "contents": [{"parts": [{"text": text}]}],
            "generationConfig": {
                "responseModalities": ["AUDIO"],
                "speechConfig": {
                    "voiceConfig": {"prebuiltVoiceConfig": {"voiceName": self._voice}}
                },
            },
        }
        try:
            response = self._client.post(
                f"{BASE_URL}/{self._model}:generateContent",
                json=body,
                headers={"x-goog-api-key": self._api_key},
            )
        except httpx.HTTPError as error:
            raise ExternalServiceError("No pude conectar con Gemini para generar la voz.") from error
        if response.status_code == 429:
            raise quota_error(response)
        if response.status_code != 200:
            raise ExternalServiceError(
                f"Gemini respondió con error {response.status_code} al generar la voz."
            )
        try:
            inline = response.json()["candidates"][0]["content"]["parts"][0]["inlineData"]
            audio = base64.b64decode(inline["data"], validate=True)
        except (KeyError, IndexError, TypeError, ValueError) as error:
            raise ExternalServiceError("Gemini devolvió un audio inesperado.") from error
        if not audio:
            raise ExternalServiceError("Gemini devolvió un audio vacío.")
        if audio[:4] == b"RIFF":
            return audio
        return pcm_to_wav(audio, _sample_rate(inline.get("mimeType", "")))


def quota_error(response: httpx.Response) -> QuotaExhaustedError:
    """Gemini gratis tiene dos límites de voz: 3 frases por minuto, que se libera solo, y 10 por día.
    Los detalles del 429 dicen cuál fue y cuánto esperar (`retryDelay`)."""
    details = _error_details(response)
    violations = [
        violation
        for detail in details
        for violation in detail.get("violations", [])
        if isinstance(violation, dict)
    ]
    retry_after_s = _retry_delay_s(details)
    if any("PerDay" in str(violation.get("quotaId", "")) for violation in violations):
        return QuotaExhaustedError(
            "Se agotó la cuota gratuita de voz de Gemini. Intenta más tarde.",
            DAILY_QUOTA_RETRY_S if retry_after_s is None else retry_after_s,
        )
    return QuotaExhaustedError(
        "La voz de Gemini llegó a su límite por minuto. Intenta en unos segundos.", retry_after_s
    )


def _error_details(response: httpx.Response) -> list[dict]:
    try:
        details = response.json()["error"]["details"]
    except (KeyError, TypeError, ValueError):
        return []
    return [detail for detail in details if isinstance(detail, dict)] if isinstance(details, list) else []


def _retry_delay_s(details: list[dict]) -> int | None:
    for detail in details:
        match = re.fullmatch(r"(\d+(?:\.\d+)?)s", str(detail.get("retryDelay", "")))
        if match:
            return math.ceil(float(match.group(1)))
    return None


def pcm_to_wav(pcm: bytes, sample_rate: int = DEFAULT_SAMPLE_RATE) -> bytes:
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(CHANNELS)
        wav.setsampwidth(SAMPLE_WIDTH_BYTES)
        wav.setframerate(sample_rate)
        wav.writeframes(pcm)
    return buffer.getvalue()


def _sample_rate(mime_type: str) -> int:
    match = re.search(r"rate=(\d+)", mime_type)
    return int(match.group(1)) if match else DEFAULT_SAMPLE_RATE
