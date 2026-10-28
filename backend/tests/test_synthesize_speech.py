import pytest

from application.speech_cache import SpeechCache
from application.use_cases.synthesize_speech import MAX_TEXT_LENGTH, synthesize_speech
from domain.errors import ValidationError


class FakeSynthesizer:
    def __init__(self):
        self.calls = []

    def synthesize(self, text):
        self.calls.append(text)
        return f"wav:{text}".encode()


def test_returns_audio_for_text():
    synthesizer = FakeSynthesizer()

    audio = synthesize_speech(synthesizer, SpeechCache(), "  Llegaste a tu destino.  ")

    assert audio == b"wav:Llegaste a tu destino."
    assert synthesizer.calls == ["Llegaste a tu destino."]


def test_repeated_text_comes_from_cache():
    synthesizer = FakeSynthesizer()
    cache = SpeechCache()

    first = synthesize_speech(synthesizer, cache, "Gira a la izquierda.")
    second = synthesize_speech(synthesizer, cache, "Gira a la izquierda.")

    assert first == second
    assert synthesizer.calls == ["Gira a la izquierda."]


def test_empty_text_is_rejected():
    with pytest.raises(ValidationError, match="Falta el texto"):
        synthesize_speech(FakeSynthesizer(), SpeechCache(), "   ")


def test_missing_text_is_rejected():
    with pytest.raises(ValidationError):
        synthesize_speech(FakeSynthesizer(), SpeechCache(), None)


def test_too_long_text_is_rejected():
    synthesizer = FakeSynthesizer()

    with pytest.raises(ValidationError, match="demasiado largo"):
        synthesize_speech(synthesizer, SpeechCache(), "a" * (MAX_TEXT_LENGTH + 1))
    assert synthesizer.calls == []


def test_text_at_max_length_is_accepted():
    audio = synthesize_speech(FakeSynthesizer(), SpeechCache(), "a" * MAX_TEXT_LENGTH)

    assert audio


def test_synthesizer_errors_are_not_cached():
    class FailingOnce(FakeSynthesizer):
        def synthesize(self, text):
            if not self.calls:
                self.calls.append(text)
                raise RuntimeError("cuota")
            return super().synthesize(text)

    synthesizer = FailingOnce()
    cache = SpeechCache()

    with pytest.raises(RuntimeError):
        synthesize_speech(synthesizer, cache, "Hola")
    assert synthesize_speech(synthesizer, cache, "Hola") == b"wav:Hola"


def test_cache_drops_least_recently_used_when_full():
    cache = SpeechCache(max_entries=2)
    cache.put("uno", b"1")
    cache.put("dos", b"2")
    cache.get("uno")
    cache.put("tres", b"3")

    assert len(cache) == 2
    assert cache.get("dos") is None
    assert cache.get("uno") == b"1"
    assert cache.get("tres") == b"3"
