from typing import Protocol

from domain.models.voice import VoiceCommand


class VoiceCommandRepositoryPort(Protocol):
    def save(self, user_id: int, command: VoiceCommand) -> int: ...
