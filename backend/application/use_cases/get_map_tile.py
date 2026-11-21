from application.tile_cache import TileCache
from domain.errors import ValidationError
from domain.ports.map_tiles import MapTileProviderPort

MIN_ZOOM = 1
MAX_ZOOM = 19


def get_map_tile(provider: MapTileProviderPort, cache: TileCache, z: int, x: int, y: int) -> bytes:
    """Valida el tile pedido y devuelve su PNG; los ya pedidos salen de la caché."""
    if not MIN_ZOOM <= z <= MAX_ZOOM:
        raise ValidationError(f"El zoom debe estar entre {MIN_ZOOM} y {MAX_ZOOM}.")
    tiles_per_side = 2**z
    if not (0 <= x < tiles_per_side and 0 <= y < tiles_per_side):
        raise ValidationError("Ese tile no existe en ese zoom.")
    key = (z, x, y)
    tile = cache.get(key)
    if tile is None:
        tile = provider.get_tile(z, x, y)
        cache.put(key, tile)
    return tile
