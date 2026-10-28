import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()

DEFAULT_GEMINI_TTS_MODEL = "gemini-3.8-flash-tts"


@dataclass(frozen=True)
class Settings:
    database_url: str
    geoapify_api_key: str
    gemini_api_key: str
    gemini_model: str
    gemini_tts_model: str = DEFAULT_GEMINI_TTS_MODEL


def load_settings() -> Settings:
    return Settings(
        database_url=os.getenv("DATABASE_URL", ""),
        geoapify_api_key=os.getenv("GEOAPIFY_API_KEY", ""),
        gemini_api_key=os.getenv("GEMINI_API_KEY", ""),
        gemini_model=os.getenv("GEMINI_MODEL", ""),
        gemini_tts_model=os.getenv("GEMINI_TTS_MODEL") or DEFAULT_GEMINI_TTS_MODEL,
    )
