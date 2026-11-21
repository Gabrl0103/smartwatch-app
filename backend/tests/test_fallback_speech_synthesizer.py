import pytest

from domain.errors import ExternalServiceError, QuotaExhaustedError
from infrastructure.speech.fallback_speech_synthesizer import DEFAULT_PAUSE_S, FallbackSpeechSynthesizer


class FakeSynthesizer:
    def __init__(self, name, error=None):
        self.name = name
        self.error = error
        self.calls = []

    def synthesize(self, text):
        self.calls.append(text)
        if self.error:
            raise self.error
        return f"{self.name}:{text}".encode()


class FakeClock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


def make(primary_error=None, fallback_error=None):
    primary = FakeSynthesizer("gemini", primary_error)
    fallback = FakeSynthesizer("edge", fallback_error)
    clock = FakeClock()
    return FallbackSpeechSynthesizer(primary, fallback, clock), primary, fallback, clock


def test_uses_primary_when_it_works():
    synthesizer, primary, fallback, _ = make()

    assert synthesizer.synthesize("Hola") == b"gemini:Hola"
    assert fallback.calls == []


def test_quota_exhausted_uses_fallback():
    synthesizer, _, fallback, _ = make(primary_error=QuotaExhaustedError("cuota", 30))

    assert synthesizer.synthesize("Hola") == b"edge:Hola"
    assert fallback.calls == ["Hola"]


def test_other_primary_error_uses_fallback():
    synthesizer, _, _, _ = make(primary_error=ExternalServiceError("Falta configurar GEMINI_API_KEY"))

    assert synthesizer.synthesize("Hola") == b"edge:Hola"


def test_after_quota_primary_is_skipped_until_retry_after():
    synthesizer, primary, _, clock = make(primary_error=QuotaExhaustedError("cuota", 30))
    synthesizer.synthesize("Uno")

    clock.now += 29
    assert synthesizer.synthesize("Dos") == b"edge:Dos"
    assert primary.calls == ["Uno"]

    clock.now += 1
    primary.error = None
    assert synthesizer.synthesize("Tres") == b"gemini:Tres"


def test_quota_without_retry_after_pauses_default():
    synthesizer, primary, _, clock = make(primary_error=QuotaExhaustedError("cuota"))
    synthesizer.synthesize("Uno")

    clock.now += DEFAULT_PAUSE_S - 1
    synthesizer.synthesize("Dos")
    assert primary.calls == ["Uno"]

    clock.now += 1
    synthesizer.synthesize("Tres")
    assert primary.calls == ["Uno", "Tres"]


def test_other_primary_error_does_not_pause_primary():
    synthesizer, primary, _, _ = make(primary_error=ExternalServiceError("Gemini respondió 500"))
    synthesizer.synthesize("Uno")
    synthesizer.synthesize("Dos")

    assert primary.calls == ["Uno", "Dos"]


def test_both_failing_raises_fallback_error():
    synthesizer, _, _, _ = make(
        primary_error=QuotaExhaustedError("cuota", 30),
        fallback_error=ExternalServiceError("No pude generar la voz de respaldo."),
    )

    with pytest.raises(ExternalServiceError, match="respaldo") as raised:
        synthesizer.synthesize("Hola")
    assert not isinstance(raised.value, QuotaExhaustedError)
