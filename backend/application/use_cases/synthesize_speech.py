from application.speech_cache import SpeechCache
from application.validation import require_text
from domain.ports.speech_synthesizer import SpeechSynthesizerPort

MAX_TEXT_LENGTH = 300


def synthesize_speech(
    synthesizer: SpeechSynthesizerPort, cache: SpeechCache, text: str | None
) -> bytes:
    """Valida la frase y devuelve su audio WAV; las frases repetidas salen de la caché."""
    text = require_text(text, "el texto", MAX_TEXT_LENGTH)
    audio = cache.get(text)
    if audio is None:
        audio = synthesizer.synthesize(text)
        cache.put(text, audio)
    return audio
