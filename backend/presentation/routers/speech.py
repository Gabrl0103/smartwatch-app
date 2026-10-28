from fastapi import APIRouter, Response

from application.use_cases.synthesize_speech import synthesize_speech
from infrastructure.config.container import get_speech_cache, get_speech_synthesizer
from presentation.dto.speech import SpeechRequest

router = APIRouter()


@router.post("/speech", response_class=Response)
def speech(body: SpeechRequest):
    """Devuelve la frase hablada en audio/wav."""
    audio = synthesize_speech(get_speech_synthesizer(), get_speech_cache(), body.text)
    return Response(content=audio, media_type="audio/wav")
