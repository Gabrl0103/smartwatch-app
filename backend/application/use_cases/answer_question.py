from application.validation import require_text
from domain.ports.voice_assistant import VoiceAssistantPort

MAX_QUESTION_LENGTH = 300
MAX_CONTEXT_LENGTH = 600


def answer_question(assistant: VoiceAssistantPort, question: str | None, context: str | None = None) -> str:
    """Responde una pregunta hecha por voz, con el contexto de la ruta en curso si lo hay.

    El contexto lo arma el reloj (destino, estado, siguiente indicación, distancias); si viene
    vacío, el asistente responde sin ruta activa.
    """
    question = require_text(question, "la pregunta", MAX_QUESTION_LENGTH)
    context = (context or "").strip()[:MAX_CONTEXT_LENGTH]
    return assistant.answer_question(question, context)
