import base64
import io
import json
import wave

import httpx
import pytest

from domain.errors import ExternalServiceError
from infrastructure.speech.gemini_speech_synthesizer import (
    VOICE_NAME,
    GeminiSpeechSynthesizer,
    pcm_to_wav,
)

PCM = b"\x01\x00\x02\x00\x03\x00\x04\x00"


def make_synthesizer(handler, key="k", model="tts"):
    client = httpx.Client(transport=httpx.MockTransport(handler))
    return GeminiSpeechSynthesizer(key, model, client=client)


def audio_reply(data: bytes, mime_type="audio/L16;codec=pcm;rate=24000"):
    inline = {"mimeType": mime_type, "data": base64.b64encode(data).decode("ascii")}
    return {"candidates": [{"content": {"parts": [{"inlineData": inline}]}}]}


def read_wav(audio: bytes):
    with wave.open(io.BytesIO(audio)) as wav:
        return wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.readframes(10)


def test_sends_text_with_audio_modality_and_voice():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["key"] = request.headers["x-goog-api-key"]
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json=audio_reply(PCM))

    make_synthesizer(handler).synthesize("Gira a la izquierda.")

    config = seen["body"]["generationConfig"]
    assert seen["url"].endswith("/tts:generateContent")
    assert seen["key"] == "k"
    assert "Gira a la izquierda." in seen["body"]["contents"][0]["parts"][0]["text"]
    assert config["responseModalities"] == ["AUDIO"]
    assert config["speechConfig"]["voiceConfig"]["prebuiltVoiceConfig"]["voiceName"] == VOICE_NAME


def test_wraps_pcm_in_wav_mono_16_bit_at_given_rate():
    synthesizer = make_synthesizer(lambda r: httpx.Response(200, json=audio_reply(PCM)))

    audio = synthesizer.synthesize("Hola")

    assert audio[:4] == b"RIFF"
    assert read_wav(audio) == (1, 2, 24000, PCM)


def test_uses_rate_from_mime_type():
    reply = audio_reply(PCM, "audio/L16;rate=16000")
    synthesizer = make_synthesizer(lambda r: httpx.Response(200, json=reply))

    assert read_wav(synthesizer.synthesize("Hola"))[2] == 16000


def test_returns_wav_unchanged_when_gemini_already_sends_wav():
    wav = pcm_to_wav(PCM)
    synthesizer = make_synthesizer(lambda r: httpx.Response(200, json=audio_reply(wav, "audio/wav")))

    assert synthesizer.synthesize("Hola") == wav


def test_quota_exceeded_raises_clear_error():
    synthesizer = make_synthesizer(lambda r: httpx.Response(429, json={}))

    with pytest.raises(ExternalServiceError, match="cuota"):
        synthesizer.synthesize("Hola")


def test_other_http_error_raises_external_service_error():
    synthesizer = make_synthesizer(lambda r: httpx.Response(500, json={}))

    with pytest.raises(ExternalServiceError, match="500"):
        synthesizer.synthesize("Hola")


def test_connection_error_raises_external_service_error():
    def handler(request):
        raise httpx.ConnectError("sin red")

    with pytest.raises(ExternalServiceError):
        make_synthesizer(handler).synthesize("Hola")


def test_reply_without_audio_raises_external_service_error():
    reply = {"candidates": [{"content": {"parts": [{"text": "hola"}]}}]}
    synthesizer = make_synthesizer(lambda r: httpx.Response(200, json=reply))

    with pytest.raises(ExternalServiceError):
        synthesizer.synthesize("Hola")


def test_missing_key_raises_external_service_error():
    synthesizer = make_synthesizer(lambda r: httpx.Response(200), key="")

    with pytest.raises(ExternalServiceError):
        synthesizer.synthesize("Hola")
