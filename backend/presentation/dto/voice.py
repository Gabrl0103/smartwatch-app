from pydantic import BaseModel

from domain.models.voice import VoiceCommand


class VoiceCommandResponse(BaseModel):
    text: str
    intent: str
    destination: str | None = None
    question: str | None = None

    @classmethod
    def from_domain(cls, command: VoiceCommand) -> "VoiceCommandResponse":
        return cls(
            text=command.text,
            intent=command.intent.value,
            destination=command.destination,
            question=command.question,
        )
