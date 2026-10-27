import pytest

from domain.errors import ExternalServiceError
from infrastructure.config import container
from infrastructure.config.settings import Settings


def test_geoapify_adapters_require_api_key(monkeypatch):
    monkeypatch.setattr(container, "load_settings", lambda: Settings("", "", "", ""))
    container.get_geocoder.cache_clear()
    container.get_routing_provider.cache_clear()
    with pytest.raises(ExternalServiceError):
        container.get_geocoder()
    with pytest.raises(ExternalServiceError):
        container.get_routing_provider()
