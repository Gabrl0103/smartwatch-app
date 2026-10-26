import os
from dataclasses import dataclass

from dotenv import load_dotenv

load_dotenv()


@dataclass(frozen=True)
class Settings:
    database_url: str
    ors_api_key: str
    gemini_api_key: str
    gemini_model: str


def load_settings() -> Settings:
    return Settings(
        database_url=os.getenv("DATABASE_URL", ""),
        ors_api_key=os.getenv("ORS_API_KEY", ""),
        gemini_api_key=os.getenv("GEMINI_API_KEY", ""),
        gemini_model=os.getenv("GEMINI_MODEL", ""),
    )
