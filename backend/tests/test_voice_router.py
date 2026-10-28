from fastapi.testclient import TestClient

from domain.models.voice import Intent, VoiceCommand
from main import app


class FakeAssistant:
    def interpret_audio(self, audio, mime_type):
        return VoiceCommand("al parque", Intent.NAVIGATE, destination="parque")


class FakeCommands:
    def __init__(self, error=None):
        self.error = error
        self.saved = []

    def save(self, user_id, command):
        if self.error:
            raise self.error
        self.saved.append(command.text)
        return 1


def use_fakes(monkeypatch, commands):
    monkeypatch.setattr("presentation.routers.voice.get_voice_assistant", lambda: FakeAssistant())
    monkeypatch.setattr("presentation.routers.voice.get_voice_command_repository", lambda: commands)


def test_interpret_endpoint_saves_command(monkeypatch):
    commands = FakeCommands()
    use_fakes(monkeypatch, commands)
    client = TestClient(app)
    response = client.post("/voice/interpret", content=b"abc", headers={"content-type": "audio/aac"})
    assert response.status_code == 200
    assert response.json()["destination"] == "parque"
    assert commands.saved == ["al parque"]


def test_interpret_answers_even_if_saving_fails(monkeypatch):
    use_fakes(monkeypatch, FakeCommands(RuntimeError("base caída")))
    client = TestClient(app)
    response = client.post("/voice/interpret", content=b"abc", headers={"content-type": "audio/aac"})
    assert response.status_code == 200
    assert response.json()["intent"] == "navigate"


def test_interpret_empty_audio_returns_422():
    client = TestClient(app)
    response = client.post("/voice/interpret", content=b"", headers={"content-type": "audio/aac"})
    assert response.status_code == 422
