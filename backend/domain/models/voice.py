from dataclasses import dataclass
from enum import Enum


class Intent(str, Enum):
    NAVIGATE = "navigate"
    ASK = "ask"
    SAVE_DESTINATION = "guardar_destino"
    REPEAT = "repetir_instruccion"
    CANCEL = "cancelar_ruta"
    HOW_FAR = "cuanto_falta"
    SAVED_STATUS = "estado_guardado"


@dataclass(frozen=True)
class VoiceCommand:
    text: str
    intent: Intent
    destination: str | None = None
    question: str | None = None
