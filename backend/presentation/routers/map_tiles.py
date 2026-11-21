from fastapi import APIRouter, Response

from application.use_cases.get_map_tile import get_map_tile, get_street_names
from infrastructure.config.container import (
    get_map_image_cache,
    get_map_tile_renderer,
    get_vector_tile_cache,
    get_vector_tile_source,
)

router = APIRouter()

# Un tile de un zoom dado casi no cambia: el reloj y cualquier caché intermedia lo guardan 30 días.
TILE_CACHE_CONTROL = "public, max-age=2592000, immutable"


@router.get("/map/tiles/{z}/{x}/{y}.png", response_class=Response)
def map_tile(z: int, x: int, y: int):
    """Tile del mapa en PNG (512 px), sin nombres de calle: el reloj los escribe derechos."""
    tile = get_map_tile(
        get_vector_tile_source(), get_map_tile_renderer(), get_vector_tile_cache(), get_map_image_cache(), z, x, y
    )
    return Response(content=tile, media_type="image/png", headers={"Cache-Control": TILE_CACHE_CONTROL})


@router.get("/map/streets/{z}/{x}/{y}.json")
def street_names(z: int, x: int, y: int, response: Response):
    """Calles con nombre del tile de zoom 14: `{"streets": [{"name", "line": [[lat, lng], ...]}]}`."""
    streets = get_street_names(get_vector_tile_source(), get_vector_tile_cache(), z, x, y)
    response.headers["Cache-Control"] = TILE_CACHE_CONTROL
    return {"streets": [{"name": street.name, "line": street.line} for street in streets]}
