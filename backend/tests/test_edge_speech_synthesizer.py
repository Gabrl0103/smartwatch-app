import aiohttp
import edge_tts
import pytest

from domain.errors import ExternalServiceError
from infrastructure.speech.edge_speech_synthesizer import VOICE_NAME, EdgeSpeechSynthesizer


def fake_stream(chunks, seen=None):
    async def stream(text, voice):
        if seen is not None:
            seen.update(text=text, voice=voice)
        for chunk in chunks:
            if isinstance(chunk, Exception):
                raise chunk
            yield chunk

    return stream


def test_joins_audio_chunks_and_skips_boundaries():
    seen = {}
    chunks = [
        {"type": "audio", "data": b"\xff\xf3"},
        {"type": "SentenceBoundary", "text": "Hola"},
        {"type": "audio", "data": b"mp3"},
    ]
    synthesizer = EdgeSpeechSynthesizer(stream=fake_stream(chunks, seen))

    assert synthesizer.synthesize("Gire a la derecha.") == b"\xff\xf3mp3"
    assert seen == {"text": "Gire a la derecha.", "voice": VOICE_NAME}


def test_spanish_colombian_voice_by_default():
    assert VOICE_NAME.startswith("es-CO-")


def test_empty_audio_is_an_error():
    synthesizer = EdgeSpeechSynthesizer(stream=fake_stream([{"type": "SentenceBoundary"}]))

    with pytest.raises(ExternalServiceError, match="vacía"):
        synthesizer.synthesize("Hola")


@pytest.mark.parametrize(
    "error",
    [
        edge_tts.exceptions.NoAudioReceived("sin audio"),
        aiohttp.ClientConnectionError("sin red"),
        TimeoutError(),
    ],
)
def test_connection_and_service_errors_become_external_service_error(error):
    synthesizer = EdgeSpeechSynthesizer(stream=fake_stream([error]))

    with pytest.raises(ExternalServiceError, match="respaldo"):
        synthesizer.synthesize("Hola")
