from fastapi.testclient import TestClient

from domain.models.voice import Intent, VoiceCommand
from main import app


class FakeAssistant:
    def __init__(self, error=None):
        self.error = error
        self.questions = []

    def interpret_audio(self, audio, mime_type):
        return VoiceCommand("al parque", Intent.NAVIGATE, destination="parque")

    def answer_question(self, question, context):
        if self.error:
            raise self.error
        self.questions.append((question, context))
        return "Sigue recto cien metros."


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


def use_assistant(monkeypatch, assistant):
    monkeypatch.setattr("presentation.routers.voice.get_voice_assistant", lambda: assistant)


def test_ask_endpoint_answers_with_the_route_context(monkeypatch):
    assistant = FakeAssistant()
    use_assistant(monkeypatch, assistant)
    response = TestClient(app).post(
        "/voice/ask", json={"question": "¿qué sigue?", "context": "Navegando hacia Parque Bolívar."}
    )
    assert response.status_code == 200
    assert response.json() == {"answer": "Sigue recto cien metros."}
    assert assistant.questions == [("¿qué sigue?", "Navegando hacia Parque Bolívar.")]


def test_ask_without_question_is_a_validation_error(monkeypatch):
    use_assistant(monkeypatch, FakeAssistant())
    response = TestClient(app).post("/voice/ask", json={})
    assert response.status_code == 422
    assert response.json()["error"]["message"] == "Falta la pregunta."


def test_ask_with_gemini_quota_exhausted_is_429(monkeypatch):
    from domain.errors import QuotaExhaustedError

    use_assistant(monkeypatch, FakeAssistant(QuotaExhaustedError("Se agotó la cuota.")))
    response = TestClient(app).post("/voice/ask", json={"question": "¿qué sigue?"})
    assert response.status_code == 429
    assert response.json()["error"]["code"] == "QUOTA_EXHAUSTED"
