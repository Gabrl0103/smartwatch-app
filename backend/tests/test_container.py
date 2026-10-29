import pytest

from domain.errors import ExternalServiceError
from infrastructure.config import container
from infrastructure.config.settings import Settings, load_settings


def test_geoapify_adapters_require_api_key(monkeypatch):
    monkeypatch.setattr(container, "load_settings", lambda: Settings("", "", "", ""))
    container.get_geocoder.cache_clear()
    container.get_routing_provider.cache_clear()
    with pytest.raises(ExternalServiceError):
        container.get_geocoder()
    with pytest.raises(ExternalServiceError):
        container.get_routing_provider()


def test_repositories_require_database_url(monkeypatch):
    monkeypatch.setattr(container, "load_settings", lambda: Settings("", "", "", ""))
    container._engine.cache_clear()
    container.get_saved_place_repository.cache_clear()
    with pytest.raises(ExternalServiceError):
        container.get_saved_place_repository()


def test_tts_model_has_free_tier_default(monkeypatch):
    monkeypatch.delenv("GEMINI_TTS_MODEL", raising=False)
    assert load_settings().gemini_tts_model == "gemini-3.8-flash-tts"
    monkeypatch.setenv("GEMINI_TTS_MODEL", "gemini-3.8-flash-lite-tts")
    assert load_settings().gemini_tts_model == "gemini-3.8-flash-lite-tts"


def test_geoapify_route_type_defaults_to_less_maneuvers(monkeypatch):
    monkeypatch.delenv("GEOAPIFY_ROUTE_TYPE", raising=False)
    assert load_settings().geoapify_route_type == "less_maneuvers"
    monkeypatch.setenv("GEOAPIFY_ROUTE_TYPE", "short")
    assert load_settings().geoapify_route_type == "short"
