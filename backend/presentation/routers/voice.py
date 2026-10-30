from fastapi import APIRouter, Request

from application.use_cases.answer_question import answer_question
from application.use_cases.interpret_voice_command import interpret_voice_command
from application.use_cases.save_history import save_voice_command
from infrastructure.config.container import get_voice_assistant, get_voice_command_repository
from presentation.dto.voice import AnswerResponse, QuestionRequest, VoiceCommandResponse

router = APIRouter(prefix="/voice")


@router.post("/ask", response_model=AnswerResponse)
def ask(body: QuestionRequest):
    """Responde la pregunta del asistente (máx. 2 frases), con el contexto de la ruta si lo hay."""
    return AnswerResponse(answer=answer_question(get_voice_assistant(), body.question, body.context))


@router.post("/interpret", response_model=VoiceCommandResponse)
async def interpret(request: Request):
    """Recibe el audio crudo en el cuerpo; el formato va en Content-Type (ej. audio/aac)."""
    audio = await request.body()
    mime_type = request.headers.get("content-type", "")
    command = interpret_voice_command(get_voice_assistant(), audio, mime_type)
    save_voice_command(get_voice_command_repository, command)
    return VoiceCommandResponse.from_domain(command)
