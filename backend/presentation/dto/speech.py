from pydantic import BaseModel


class SpeechRequest(BaseModel):
    text: str | None = None
