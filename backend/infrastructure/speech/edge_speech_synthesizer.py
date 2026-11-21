import asyncio
from collections.abc import AsyncIterator, Callable

import aiohttp
import edge_tts

from domain.errors import ExternalServiceError

# Voz neural colombiana de Microsoft Edge, masculina como la de Gemini. Gratis y sin clave.
VOICE_NAME = "es-CO-GonzaloNeural"
CONNECT_TIMEOUT_S = 5
RECEIVE_TIMEOUT_S = 15

Stream = Callable[[str, str], AsyncIterator[dict]]


def _edge_stream(text: str, voice: str) -> AsyncIterator[dict]:
    communicate = edge_tts.Communicate(
        text, voice, connect_timeout=CONNECT_TIMEOUT_S, receive_timeout=RECEIVE_TIMEOUT_S
    )
    return communicate.stream()


class EdgeSpeechSynthesizer:
    """Voz de respaldo: la de Microsoft Edge (edge-tts). Devuelve MP3, que el reloj reproduce
    igual que el WAV de Gemini."""

    def __init__(self, voice: str = VOICE_NAME, stream: Stream = _edge_stream):
        self._voice = voice
        self._stream = stream

    def synthesize(self, text: str) -> bytes:
        try:
            audio = asyncio.run(self._collect(text))
        except (edge_tts.exceptions.EdgeTTSException, aiohttp.ClientError, TimeoutError, OSError) as error:
            raise ExternalServiceError("No pude generar la voz de respaldo.") from error
        if not audio:
            raise ExternalServiceError("La voz de respaldo llegó vacía.")
        return audio

    async def _collect(self, text: str) -> bytes:
        chunks = [chunk["data"] async for chunk in self._stream(text, self._voice) if chunk.get("type") == "audio"]
        return b"".join(chunks)
