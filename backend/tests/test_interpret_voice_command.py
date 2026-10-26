import pytest

from application.use_cases.interpret_voice_command import interpret_voice_command
from domain.errors import ValidationError
from domain.models.voice import Intent, VoiceCommand


class FakeAssistant:
    def interpret_audio(self, audio, mime_type):
        return VoiceCommand("llévame al parque", Intent.NAVIGATE, destination="parque")


def test_returns_command():
    result = interpret_voice_command(FakeAssistant(), b"abc", "audio/aac")
    assert result.destination == "parque"


def test_ignores_mime_parameters():
    result = interpret_voice_command(FakeAssistant(), b"abc", "audio/aac; charset=x")
    assert result.intent == Intent.NAVIGATE


def test_empty_audio_raises():
    with pytest.raises(ValidationError):
        interpret_voice_command(FakeAssistant(), b"", "audio/aac")


def test_unsupported_format_raises():
    with pytest.raises(ValidationError):
        interpret_voice_command(FakeAssistant(), b"abc", "text/plain")


def test_too_long_audio_raises():
    with pytest.raises(ValidationError):
        interpret_voice_command(FakeAssistant(), b"x" * (5 * 1024 * 1024 + 1), "audio/aac")
