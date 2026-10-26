from dataclasses import dataclass
from enum import Enum


class Intent(str, Enum):
    NAVIGATE = "navigate"
    ASK = "ask"
    CANCEL = "cancel"
    REPEAT = "repeat"


@dataclass(frozen=True)
class VoiceCommand:
    text: str
    intent: Intent
    destination: str | None = None
    question: str | None = None
