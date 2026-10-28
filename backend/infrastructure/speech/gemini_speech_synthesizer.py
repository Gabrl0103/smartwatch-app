import base64
import io
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
            raise QuotaExhaustedError(
                "Se agotó la cuota gratuita de voz de Gemini. Intenta más tarde."
            )
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
