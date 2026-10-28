"""Composition root: único lugar donde se eligen las implementaciones."""

from functools import lru_cache

from sqlalchemy.engine import Engine

from domain.errors import ExternalServiceError
from domain.ports.database_health import DatabaseHealthPort
from domain.ports.device_repository import DeviceRepositoryPort
from domain.ports.geocoding import GeocodingPort
from domain.ports.route_repository import RouteRepositoryPort
from domain.ports.routing_provider import RoutingProviderPort
from domain.ports.saved_place_repository import SavedPlaceRepositoryPort
from domain.ports.vibration_event_repository import VibrationEventRepositoryPort
from domain.ports.voice_assistant import VoiceAssistantPort
from domain.ports.voice_command_repository import VoiceCommandRepositoryPort
from infrastructure.assistant.gemini_voice_assistant import GeminiVoiceAssistant
from infrastructure.config.settings import load_settings
from infrastructure.database.engine import create_db_engine
from infrastructure.database.sql_database_health import SqlDatabaseHealth
from infrastructure.database.sql_device_repository import SqlDeviceRepository
from infrastructure.database.sql_route_repository import SqlRouteRepository
from infrastructure.database.sql_saved_place_repository import SqlSavedPlaceRepository
from infrastructure.database.sql_vibration_event_repository import SqlVibrationEventRepository
from infrastructure.database.sql_voice_command_repository import SqlVoiceCommandRepository
from infrastructure.geocoding.geoapify_geocoder import GeoapifyGeocoder
from infrastructure.routing.geoapify_routing import GeoapifyRouting


@lru_cache
def _engine() -> Engine:
    database_url = load_settings().database_url
    if not database_url:
        raise ExternalServiceError("Falta configurar DATABASE_URL.")
    return create_db_engine(database_url)


@lru_cache
def get_database_health() -> DatabaseHealthPort:
    return SqlDatabaseHealth(_engine())


@lru_cache
def get_saved_place_repository() -> SavedPlaceRepositoryPort:
    return SqlSavedPlaceRepository(_engine())


@lru_cache
def get_route_repository() -> RouteRepositoryPort:
    return SqlRouteRepository(_engine())


@lru_cache
def get_voice_command_repository() -> VoiceCommandRepositoryPort:
    return SqlVoiceCommandRepository(_engine())


@lru_cache
def get_vibration_event_repository() -> VibrationEventRepositoryPort:
    return SqlVibrationEventRepository(_engine())


@lru_cache
def get_device_repository() -> DeviceRepositoryPort:
    return SqlDeviceRepository(_engine())


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
