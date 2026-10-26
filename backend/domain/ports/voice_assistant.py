from typing import Protocol

from domain.models.voice import VoiceCommand


class VoiceAssistantPort(Protocol):
    def interpret_audio(self, audio: bytes, mime_type: str) -> VoiceCommand: ...

    def answer_question(self, question: str, context: str) -> str: ...
