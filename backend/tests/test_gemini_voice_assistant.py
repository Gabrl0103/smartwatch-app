import json

import httpx
import pytest

from domain.errors import ExternalServiceError
from domain.models.voice import Intent
from infrastructure.assistant.gemini_voice_assistant import GeminiVoiceAssistant


def make_assistant(handler, key="k", model="m"):
    client = httpx.Client(transport=httpx.MockTransport(handler))
    return GeminiVoiceAssistant(key, model, client)


def gemini_reply(text):
    return {"candidates": [{"content": {"parts": [{"text": text}]}}]}


def test_interpret_audio_parses_command_and_sends_audio():
    seen = {}

    def handler(request):
        seen["url"] = str(request.url)
        seen["key"] = request.headers["x-goog-api-key"]
        seen["body"] = json.loads(request.content)
        payload = {"text": "llévame al parque", "intent": "navigate", "destination": "parque"}
        return httpx.Response(200, json=gemini_reply(json.dumps(payload)))

    command = make_assistant(handler).interpret_audio(b"audio", "audio/aac")
    assert command.intent == Intent.NAVIGATE
    assert command.destination == "parque"
    assert seen["url"].endswith("/m:generateContent")
    assert seen["key"] == "k"
    assert seen["body"]["contents"][0]["parts"][1]["inline_data"]["mime_type"] == "audio/aac"


def test_http_error_raises_external_service_error():
    assistant = make_assistant(lambda r: httpx.Response(429, json={}))
    with pytest.raises(ExternalServiceError):
        assistant.interpret_audio(b"a", "audio/aac")


def test_invalid_json_raises_external_service_error():
    assistant = make_assistant(lambda r: httpx.Response(200, json=gemini_reply("no es json")))
    with pytest.raises(ExternalServiceError):
        assistant.interpret_audio(b"a", "audio/aac")


def test_missing_key_raises_external_service_error():
    assistant = make_assistant(lambda r: httpx.Response(200), key="")
    with pytest.raises(ExternalServiceError):
        assistant.interpret_audio(b"a", "audio/aac")


def test_answer_question_returns_text():
    assistant = make_assistant(lambda r: httpx.Response(200, json=gemini_reply(" Sigue recto. ")))
    assert assistant.answer_question("¿hacia dónde?", "ruta") == "Sigue recto."
