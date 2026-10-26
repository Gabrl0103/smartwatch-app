from domain.errors import ValidationError
from domain.models.voice import VoiceCommand
from domain.ports.voice_assistant import VoiceAssistantPort

MAX_AUDIO_BYTES = 5 * 1024 * 1024
ALLOWED_MIME_TYPES = {
    "audio/aac",
    "audio/mp3",
    "audio/mpeg",
    "audio/wav",
    "audio/ogg",
    "audio/flac",
    "audio/m4a",
    "audio/webm",
}


def interpret_voice_command(
    assistant: VoiceAssistantPort, audio: bytes, mime_type: str
) -> VoiceCommand:
    """Valida el audio y le pide al asistente la intención y el destino."""
    mime_type = mime_type.split(";")[0].strip().lower()
    if not audio:
        raise ValidationError("No llegó audio.")
    if len(audio) > MAX_AUDIO_BYTES:
        raise ValidationError("El audio es demasiado largo.")
    if mime_type not in ALLOWED_MIME_TYPES:
        raise ValidationError(f"Formato de audio no soportado: {mime_type}.")
    return assistant.interpret_audio(audio, mime_type)
