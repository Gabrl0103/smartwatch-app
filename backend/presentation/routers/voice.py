from fastapi import APIRouter, Request

from application.use_cases.interpret_voice_command import interpret_voice_command
from infrastructure.config.container import get_voice_assistant
from presentation.dto.voice import VoiceCommandResponse

router = APIRouter(prefix="/voice")


@router.post("/interpret", response_model=VoiceCommandResponse)
async def interpret(request: Request):
    """Recibe el audio crudo en el cuerpo; el formato va en Content-Type (ej. audio/aac)."""
    audio = await request.body()
    mime_type = request.headers.get("content-type", "")
    command = interpret_voice_command(get_voice_assistant(), audio, mime_type)
    return VoiceCommandResponse.from_domain(command)
