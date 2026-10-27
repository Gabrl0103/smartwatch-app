"""Composition root: único lugar donde se eligen las implementaciones."""

from functools import lru_cache

from domain.errors import ExternalServiceError
from domain.ports.database_health import DatabaseHealthPort
from domain.ports.geocoding import GeocodingPort
from domain.ports.routing_provider import RoutingProviderPort
from domain.ports.voice_assistant import VoiceAssistantPort
from infrastructure.assistant.gemini_voice_assistant import GeminiVoiceAssistant
from infrastructure.config.settings import load_settings
from infrastructure.database.engine import create_db_engine
from infrastructure.database.sql_database_health import SqlDatabaseHealth
from infrastructure.geocoding.geoapify_geocoder import GeoapifyGeocoder
from infrastructure.routing.geoapify_routing import GeoapifyRouting


@lru_cache
def get_database_health() -> DatabaseHealthPort:
    settings = load_settings()
    if not settings.database_url:
        raise ExternalServiceError("Falta configurar DATABASE_URL.")
    return SqlDatabaseHealth(create_db_engine(settings.database_url))


@lru_cache
def get_voice_assistant() -> VoiceAssistantPort:
    settings = load_settings()
    return GeminiVoiceAssistant(settings.gemini_api_key, settings.gemini_model)


def _geoapify_api_key() -> str:
    api_key = load_settings().geoapify_api_key
    if not api_key:
        raise ExternalServiceError("Falta configurar GEOAPIFY_API_KEY.")
    return api_key


@lru_cache
def get_geocoder() -> GeocodingPort:
    return GeoapifyGeocoder(_geoapify_api_key())


@lru_cache
def get_routing_provider() -> RoutingProviderPort:
    return GeoapifyRouting(_geoapify_api_key())
