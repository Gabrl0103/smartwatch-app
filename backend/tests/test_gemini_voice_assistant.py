import json

import httpx
import pytest

from domain.errors import ExternalServiceError, QuotaExhaustedError
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


@pytest.mark.parametrize(
    "intent",
    [
        Intent.SAVE_DESTINATION,
        Intent.REPEAT,
        Intent.CANCEL,
        Intent.HOW_FAR,
        Intent.SAVED_STATUS,
    ],
)
def test_voice_commands_are_understood(intent):
    payload = {"text": "orden", "intent": intent.value}
    command = make_assistant(
        lambda r: httpx.Response(200, json=gemini_reply(json.dumps(payload)))
    ).interpret_audio(b"a", "audio/aac")
    assert command.intent == intent
    assert command.destination is None


def test_command_names_are_the_ones_the_watch_expects():
    assert Intent.SAVE_DESTINATION.value == "guardar_destino"
    assert Intent.REPEAT.value == "repetir_instruccion"
    assert Intent.CANCEL.value == "cancelar_ruta"
    assert Intent.HOW_FAR.value == "cuanto_falta"
    assert Intent.SAVED_STATUS.value == "estado_guardado"


def test_gemini_can_only_answer_with_known_intents_and_the_prompt_explains_each():
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        return httpx.Response(200, json=gemini_reply('{"text": "", "intent": "ask"}'))

    make_assistant(handler).interpret_audio(b"a", "audio/aac")
    allowed = seen["body"]["generationConfig"]["responseSchema"]["properties"]["intent"]["enum"]
    prompt = seen["body"]["contents"][0]["parts"][0]["text"]
    assert set(allowed) == {intent.value for intent in Intent}
    for intent in Intent:
        assert f"{intent.value} =" in prompt


def test_an_intent_outside_the_list_raises_external_service_error():
    payload = {"text": "repite", "intent": "repeat"}
    assistant = make_assistant(lambda r: httpx.Response(200, json=gemini_reply(json.dumps(payload))))
    with pytest.raises(ExternalServiceError):
        assistant.interpret_audio(b"a", "audio/aac")


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


def test_answer_prompt_says_what_rumbo_cannot_do():
    seen = {}

    def handler(request):
        seen["prompt"] = json.loads(request.content)["contents"][0]["parts"][0]["text"]
        return httpx.Response(200, json=gemini_reply("No detecto obstáculos."))

    make_assistant(handler).answer_question("¿Hay obstáculos?", "Navegando a pie hacia Parque Bolívar.")
    prompt = seen["prompt"]
    assert "NO ve" in prompt and "obstáculos" in prompt
    assert "sin inventar" in prompt
    assert prompt.endswith("Contexto de la ruta: Navegando a pie hacia Parque Bolívar.\nPregunta: ¿Hay obstáculos?")


def test_html_entities_from_gemini_are_decoded():
    payload = {"text": "cancelar navegaci&oacute;n", "intent": "ask", "question": "¿d&oacute;nde estoy?"}
    command = make_assistant(
        lambda r: httpx.Response(200, json=gemini_reply(json.dumps(payload)))
    ).interpret_audio(b"a", "audio/aac")
    assert command.text == "cancelar navegación"
    assert command.question == "¿dónde estoy?"

    assistant = make_assistant(lambda r: httpx.Response(200, json=gemini_reply("Gira a la izquierda en la esquina.&nbsp;")))
    assert assistant.answer_question("¿qué sigue?", "") == "Gira a la izquierda en la esquina."


def test_quota_exhausted_raises_quota_error():
    assistant = make_assistant(lambda r: httpx.Response(429, json={}))
    with pytest.raises(QuotaExhaustedError):
        assistant.answer_question("¿qué sigue?", "")


def test_other_errors_are_not_quota():
    assistant = make_assistant(lambda r: httpx.Response(500, json={}))
    with pytest.raises(ExternalServiceError) as error:
        assistant.answer_question("¿qué sigue?", "")
    assert not isinstance(error.value, QuotaExhaustedError)
