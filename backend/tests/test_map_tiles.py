import gzip

import httpx
import pytest
from fastapi.testclient import TestClient

from application.tile_cache import TileCache
from application.use_cases.get_map_tile import get_map_tile, get_street_names
from domain.errors import ExternalServiceError, ValidationError
from domain.models.map_tile import MapFeature, VectorTile
from infrastructure.maps.geoapify_vector_tiles import GeoapifyVectorTiles
from infrastructure.maps.mvt import decode_tile
from infrastructure.maps.pillow_tile_renderer import PillowTileRenderer
from main import app

client = TestClient(app)


# --- Un MVT mínimo escrito a mano: una capa "transportation_name" con una calle con nombre. ---

def _varint(n: int) -> bytes:
    out = bytearray()
    while True:
        byte = n & 0x7F
        n >>= 7
        out.append(byte | (0x80 if n else 0))
        if not n:
            return bytes(out)


def _field(number: int, payload: bytes) -> bytes:
    return _varint(number << 3 | 2) + _varint(len(payload)) + payload


def _zigzag(n: int) -> int:
    return (n << 1) ^ (n >> 31)


def mvt(layer="transportation_name", name="Calle 18", kind="minor") -> bytes:
    # Línea de (0, 0) a (4096, 4096): MoveTo 1 punto, LineTo 1 punto.
    geometry = [9, _zigzag(0), _zigzag(0), 10, _zigzag(4096), _zigzag(4096)]
    feature = (
        _field(2, b"".join(_varint(v) for v in [0, 0, 1, 1]))
        + _varint(3 << 3) + _varint(2)
        + _field(4, b"".join(_varint(v) for v in geometry))
    )
    layer_bytes = (
        _field(1, layer.encode())
        + _field(2, feature)
        + _field(3, b"name") + _field(3, b"class")
        + _field(4, _field(1, name.encode())) + _field(4, _field(1, kind.encode()))
        + _varint(5 << 3) + _varint(4096)
    )
    return _field(3, layer_bytes)


ROAD = MapFeature("transportation", "line", [[(0.0, 2048.0), (4096.0, 2048.0)]], kind="minor")
NAMED = MapFeature("transportation_name", "line", [[(0.0, 0.0), (4096.0, 4096.0)]], name="Calle 18")


class FakeSource:
    max_zoom = 14

    def __init__(self, error=None):
        self.error = error
        self.asked = []

    def get_vector_tile(self, z, x, y):
        self.asked.append((z, x, y))
        if self.error:
            raise self.error
        return VectorTile(z, x, y, 4096, [ROAD, NAMED])


def use_source(monkeypatch, source):
    monkeypatch.setattr("presentation.routers.map_tiles.get_vector_tile_source", lambda: source)
    monkeypatch.setattr("presentation.routers.map_tiles.get_vector_tile_cache", lambda: TileCache())
    monkeypatch.setattr("presentation.routers.map_tiles.get_map_image_cache", lambda: TileCache())


def test_the_decoder_reads_layers_names_and_lines_even_gzipped():
    tile = decode_tile(gzip.compress(mvt()), 14, 1, 2)

    [street] = tile.layer("transportation_name")
    assert street.name == "Calle 18"
    assert street.kind == "minor"
    assert street.parts == [[(0.0, 0.0), (4096.0, 4096.0)]]


def test_geoapify_vector_tile_is_asked_with_the_key_and_decoded():
    seen = {}

    def handler(request):
        seen["url"] = request.url
        return httpx.Response(200, content=mvt())

    tile = GeoapifyVectorTiles("k", httpx.Client(transport=httpx.MockTransport(handler))).get_vector_tile(14, 4674, 8136)

    assert seen["url"].path == "/v1/tile/vector/14/4674/8136.pbf"
    assert seen["url"].params["apiKey"] == "k"
    assert tile.layer("transportation_name")[0].name == "Calle 18"


@pytest.mark.parametrize("reply", [httpx.Response(429), httpx.Response(200, content=b"\x1a\xff\xff")])
def test_geoapify_vector_errors_are_external_service_errors(reply):
    tiles = GeoapifyVectorTiles("k", httpx.Client(transport=httpx.MockTransport(lambda _: reply)))
    with pytest.raises(ExternalServiceError):
        tiles.get_vector_tile(14, 1, 1)


def test_a_zoom_17_tile_is_drawn_from_its_zoom_14_ancestor_once():
    source = FakeSource()
    vectors, images = TileCache(), TileCache()

    for x in (37399, 37398):
        png = get_map_tile(source, PillowTileRenderer(), vectors, images, 17, x, 65093)

    assert png.startswith(b"\x89PNG")
    assert source.asked == [(14, 4674, 8136)]


def test_street_names_come_in_lat_lng_inside_their_tile():
    [street] = get_street_names(FakeSource(), TileCache(), 14, 4674, 8136)

    assert street.name == "Calle 18"
    (north, west), (south, east) = street.line
    assert north > south and east > west
    assert 1.20 < south < north < 1.24 and -77.30 < west < east < -77.26


@pytest.mark.parametrize("z, x, y", [(0, 0, 0), (20, 0, 0), (2, 4, 0), (2, 0, -1)])
def test_invalid_tiles_are_rejected(z, x, y):
    with pytest.raises(ValidationError):
        get_map_tile(FakeSource(), PillowTileRenderer(), TileCache(), TileCache(), z, x, y)


def test_street_names_are_only_by_zoom_14():
    with pytest.raises(ValidationError):
        get_street_names(FakeSource(), TileCache(), 17, 1, 1)


def test_tile_cache_drops_the_least_used():
    cache = TileCache(max_tiles=2)
    cache.put((1, 0, 0), b"a")
    cache.put((1, 0, 1), b"b")
    cache.get((1, 0, 0))
    cache.put((1, 1, 0), b"c")

    assert cache.get((1, 0, 1)) is None
    assert len(cache) == 2


def test_tile_endpoint_returns_png_with_long_cache(monkeypatch):
    use_source(monkeypatch, FakeSource())

    response = client.get("/map/tiles/17/37399/65093.png")

    assert response.status_code == 200
    assert response.headers["content-type"] == "image/png"
    assert "max-age" in response.headers["cache-control"]


def test_streets_endpoint_returns_names_and_lines(monkeypatch):
    use_source(monkeypatch, FakeSource())

    response = client.get("/map/streets/14/4674/8136.json")

    assert response.status_code == 200
    assert response.json()["streets"][0]["name"] == "Calle 18"
    assert len(response.json()["streets"][0]["line"]) == 2


def test_tile_endpoint_rejects_zoom_out_of_range(monkeypatch):
    use_source(monkeypatch, FakeSource())

    response = client.get("/map/tiles/20/1/1.png")

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


def test_tile_endpoint_returns_502_when_geoapify_fails(monkeypatch):
    use_source(monkeypatch, FakeSource(ExternalServiceError("Geoapify respondió con error 429.")))

    response = client.get("/map/tiles/17/1/1.png")

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "EXTERNAL_SERVICE_ERROR"
