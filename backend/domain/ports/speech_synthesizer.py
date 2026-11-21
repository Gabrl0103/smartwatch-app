from typing import Protocol


class SpeechSynthesizerPort(Protocol):
    def synthesize(self, text: str) -> bytes:
        """Devuelve la frase hablada como un archivo de audio WAV o MP3."""
        ...
