import base64
import io
import json
import wave

import httpx
import pytest

from domain.errors import ExternalServiceError, QuotaExhaustedError
from infrastructure.speech.gemini_speech_synthesizer import (
    DAILY_QUOTA_RETRY_S,
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
    # Solo la frase: cualquier instrucción extra en el texto se leería en voz alta.
    assert seen["body"]["contents"] == [{"parts": [{"text": "Gira a la izquierda."}]}]
    assert "systemInstruction" not in seen["body"]
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


def rate_limit_reply(quota_id, retry_delay="27s"):
    """Forma real del 429 de Gemini TTS, copiada de una respuesta del 2026-10-30."""
    return {
        "error": {
            "code": 429,
            "message": "You exceeded your current quota, please check your plan and billing details.",
            "status": "RESOURCE_EXHAUSTED",
            "details": [
                {"@type": "type.googleapis.com/google.rpc.Help", "links": []},
                {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [
                        {
                            "quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests",
                            "quotaId": quota_id,
                            "quotaDimensions": {"model": "gemini-3.8-flash-tts", "location": "global"},
                            "quotaValue": "3",
                        }
                    ],
                },
                {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": retry_delay},
            ],
        }
    }


def quota_error_of(reply):
    synthesizer = make_synthesizer(lambda r: httpx.Response(429, json=reply))
    with pytest.raises(QuotaExhaustedError) as error:
        synthesizer.synthesize("Hola")
    return error.value


def test_the_limit_per_minute_says_how_long_to_wait():
    error = quota_error_of(rate_limit_reply("GenerateRequestsPerMinutePerProjectPerModel-FreeTier"))

    assert error.retry_after_s == 27
    assert "por minuto" in error.message


def test_a_fractional_delay_rounds_up():
    reply = rate_limit_reply("GenerateRequestsPerMinutePerProjectPerModel-FreeTier", "26.28s")

    assert quota_error_of(reply).retry_after_s == 27


def test_the_daily_quota_waits_until_gemini_says_it_renews():
    error = quota_error_of(rate_limit_reply("GenerateRequestsPerDayPerProjectPerModel-FreeTier", "43157s"))

    assert error.retry_after_s == 43157
    assert "cuota" in error.message


def test_the_daily_quota_without_a_delay_waits_half_an_hour():
    reply = rate_limit_reply("GenerateRequestsPerDayPerProjectPerModel-FreeTier")
    reply["error"]["details"].pop()

    assert quota_error_of(reply).retry_after_s == DAILY_QUOTA_RETRY_S


@pytest.mark.parametrize(
    "reply",
    [{}, {"error": {}}, {"error": {"details": "nada"}}, {"error": {"details": ["nada", {"retryDelay": "mucho"}]}}],
)
def test_a_429_without_details_does_not_invent_a_wait(reply):
    assert quota_error_of(reply).retry_after_s is None


def test_a_429_that_is_not_json_is_still_a_quota_error():
    synthesizer = make_synthesizer(lambda r: httpx.Response(429, text="Too Many Requests"))

    with pytest.raises(QuotaExhaustedError) as error:
        synthesizer.synthesize("Hola")
    assert error.value.retry_after_s is None


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
