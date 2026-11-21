import httpx
import pytest
from fastapi.testclient import TestClient

from application.tile_cache import TileCache
from application.use_cases.get_map_tile import get_map_tile
from domain.errors import ExternalServiceError, ValidationError
from infrastructure.maps.geoapify_tiles import GeoapifyTiles
from main import app

PNG = b"\x89PNG\r\n\x1a\nfake"
client = TestClient(app)


def make_tiles(handler, style="dark-matter"):
    return GeoapifyTiles("k", httpx.Client(transport=httpx.MockTransport(handler)), style=style)


class FakeProvider:
    def __init__(self, error=None):
        self.error = error
        self.calls = 0

    def get_tile(self, z, x, y):
        self.calls += 1
        if self.error:
            raise self.error
        return PNG


def use_provider(monkeypatch, provider):
    monkeypatch.setattr("presentation.routers.map_tiles.get_map_tile_provider", lambda: provider)
    monkeypatch.setattr("presentation.routers.map_tiles.get_tile_cache", lambda: TileCache())


def test_geoapify_tile_asks_the_retina_tile_of_the_style_with_the_key():
    seen = {}

    def handler(request):
        seen["url"] = request.url
        return httpx.Response(200, content=PNG)

    assert make_tiles(handler).get_tile(17, 37399, 65093) == PNG
    assert seen["url"].path == "/v1/tile/dark-matter/17/37399/65093@2x.png"
    assert seen["url"].params["apiKey"] == "k"


@pytest.mark.parametrize("reply", [httpx.Response(429), httpx.Response(200, content=b"<html>")])
def test_geoapify_tile_errors_are_external_service_errors(reply):
    with pytest.raises(ExternalServiceError):
        make_tiles(lambda _: reply).get_tile(17, 1, 1)


def test_geoapify_tile_without_connection_is_an_external_service_error():
    def handler(request):
        raise httpx.ConnectError("sin red", request=request)

    with pytest.raises(ExternalServiceError):
        make_tiles(handler).get_tile(17, 1, 1)


def test_repeated_tiles_come_from_the_cache():
    provider = FakeProvider()
    cache = TileCache()

    get_map_tile(provider, cache, 17, 5, 6)
    get_map_tile(provider, cache, 17, 5, 6)

    assert provider.calls == 1


def test_tile_cache_drops_the_least_used():
    cache = TileCache(max_tiles=2)
    cache.put((1, 0, 0), b"a")
    cache.put((1, 0, 1), b"b")
    cache.get((1, 0, 0))
    cache.put((1, 1, 0), b"c")

    assert cache.get((1, 0, 1)) is None
    assert len(cache) == 2


@pytest.mark.parametrize("z, x, y", [(0, 0, 0), (20, 0, 0), (2, 4, 0), (2, 0, -1)])
def test_invalid_tiles_are_rejected(z, x, y):
    with pytest.raises(ValidationError):
        get_map_tile(FakeProvider(), TileCache(), z, x, y)


def test_tile_endpoint_returns_png_with_long_cache(monkeypatch):
    use_provider(monkeypatch, FakeProvider())

    response = client.get("/map/tiles/17/37399/65093.png")

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    assert "max-age" in response.headers["cache-control"]
    assert response.content == PNG


def test_tile_endpoint_rejects_zoom_out_of_range(monkeypatch):
    use_provider(monkeypatch, FakeProvider())

    response = client.get("/map/tiles/20/1/1.png")

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


def test_tile_endpoint_returns_502_when_geoapify_fails(monkeypatch):
    use_provider(monkeypatch, FakeProvider(ExternalServiceError("Geoapify respondió con error 429.")))

    response = client.get("/map/tiles/17/1/1.png")

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "EXTERNAL_SERVICE_ERROR"
