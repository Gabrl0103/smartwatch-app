from fastapi.testclient import TestClient

from application.speech_cache import SpeechCache
from domain.errors import ExternalServiceError, QuotaExhaustedError
from main import app

client = TestClient(app)


class FakeSynthesizer:
    def __init__(self, error=None):
        self.error = error

    def synthesize(self, text):
        if self.error:
            raise self.error
        return b"RIFF" + text.encode()


def use_synthesizer(monkeypatch, synthesizer):
    monkeypatch.setattr("presentation.routers.speech.get_speech_synthesizer", lambda: synthesizer)
    monkeypatch.setattr("presentation.routers.speech.get_speech_cache", lambda: SpeechCache())


def test_speech_returns_wav(monkeypatch):
    use_synthesizer(monkeypatch, FakeSynthesizer())

    response = client.post("/speech", json={"text": "Hola"})

    assert response.status_code == 200
    assert response.headers["content-type"] == "audio/wav"
    assert response.content == b"RIFFHola"


def test_speech_without_text_returns_422(monkeypatch):
    use_synthesizer(monkeypatch, FakeSynthesizer())

    response = client.post("/speech", json={})

    assert response.status_code == 422
    assert response.json()["error"]["message"] == "Falta el texto."


def test_speech_quota_error_returns_429_quota_exhausted(monkeypatch):
    error = QuotaExhaustedError("Se agotó la cuota gratuita de voz de Gemini. Intenta más tarde.")
    use_synthesizer(monkeypatch, FakeSynthesizer(error))

    response = client.post("/speech", json={"text": "Hola"})

    assert response.status_code == 429
    assert response.json()["error"]["code"] == "QUOTA_EXHAUSTED"
    assert "cuota" in response.json()["error"]["message"]


def test_speech_other_gemini_error_returns_502(monkeypatch):
    use_synthesizer(monkeypatch, FakeSynthesizer(ExternalServiceError("Gemini respondió con error 500.")))

    response = client.post("/speech", json={"text": "Hola"})

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "EXTERNAL_SERVICE_ERROR"
