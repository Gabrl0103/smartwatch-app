from fastapi import APIRouter, Response

from application.use_cases.get_map_tile import get_map_tile
from infrastructure.config.container import get_map_tile_provider, get_tile_cache

router = APIRouter()

# Un tile de un zoom dado casi no cambia: el reloj y cualquier caché intermedia lo guardan 30 días.
TILE_CACHE_CONTROL = "public, max-age=2592000, immutable"


@router.get("/map/tiles/{z}/{x}/{y}.png", response_class=Response)
def map_tile(z: int, x: int, y: int):
    """Devuelve el tile del mapa en PNG (512 px), pedido a Geoapify sin exponer su clave."""
    tile = get_map_tile(get_map_tile_provider(), get_tile_cache(), z, x, y)
    return Response(content=tile, media_type="image/png", headers={"Cache-Control": TILE_CACHE_CONTROL})
