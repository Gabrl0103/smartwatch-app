from fastapi.testclient import TestClient

from domain.models.voice import Intent, VoiceCommand
from main import app


class FakeAssistant:
    def interpret_audio(self, audio, mime_type):
        return VoiceCommand("al parque", Intent.NAVIGATE, destination="parque")


def test_interpret_endpoint(monkeypatch):
    monkeypatch.setattr(
        "presentation.routers.voice.get_voice_assistant", lambda: FakeAssistant()
    )
    client = TestClient(app)
    response = client.post("/voice/interpret", content=b"abc", headers={"content-type": "audio/aac"})
    assert response.status_code == 200
    assert response.json()["destination"] == "parque"


def test_interpret_empty_audio_returns_422():
    client = TestClient(app)
    response = client.post("/voice/interpret", content=b"", headers={"content-type": "audio/aac"})
    assert response.status_code == 422
