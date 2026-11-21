import math

from application.tile_cache import TileCache
from domain.errors import ValidationError
from domain.models.map_tile import StreetName, VectorTile
from domain.ports.map_tiles import MapTileRendererPort, VectorTileSourcePort

MIN_ZOOM = 1
MAX_ZOOM = 19
# Los nombres de calle se piden por tile de zoom 14: uno cubre 64 tiles del mapa de cerca (zoom 17).
STREET_NAMES_ZOOM = 14


def _validate(z: int, x: int, y: int) -> None:
    if not MIN_ZOOM <= z <= MAX_ZOOM:
        raise ValidationError(f"El zoom debe estar entre {MIN_ZOOM} y {MAX_ZOOM}.")
    tiles_per_side = 2**z
    if not (0 <= x < tiles_per_side and 0 <= y < tiles_per_side):
        raise ValidationError("Ese tile no existe en ese zoom.")


def _source_tile(source: VectorTileSourcePort, vectors: TileCache[VectorTile], z: int, x: int, y: int) -> VectorTile:
    """El tile vectorial que cubre (z, x, y): el mismo o, más cerca que su zoom máximo, su ancestro."""
    source_z = min(z, source.max_zoom)
    shift = z - source_z
    key = (source_z, x >> shift, y >> shift)
    tile = vectors.get(key)
    if tile is None:
        tile = source.get_vector_tile(*key)
        vectors.put(key, tile)
    return tile


def get_map_tile(
    source: VectorTileSourcePort,
    renderer: MapTileRendererPort,
    vectors: TileCache[VectorTile],
    images: TileCache[bytes],
    z: int,
    x: int,
    y: int,
) -> bytes:
    """Valida el tile pedido y devuelve su PNG sin nombres; lo ya dibujado sale de la caché."""
    _validate(z, x, y)
    key = (z, x, y)
    image = images.get(key)
    if image is None:
        image = renderer.render(_source_tile(source, vectors, z, x, y), z, x, y)
        images.put(key, image)
    return image


def get_street_names(
    source: VectorTileSourcePort, vectors: TileCache[VectorTile], z: int, x: int, y: int
) -> list[StreetName]:
    """Las calles con nombre del tile (zoom 14) en [lat, lng], para que el reloj las rotule derechas."""
    _validate(z, x, y)
    if z != STREET_NAMES_ZOOM:
        raise ValidationError(f"Los nombres de calle se piden con zoom {STREET_NAMES_ZOOM}.")
    tile = _source_tile(source, vectors, z, x, y)
    streets = []
    for feature in tile.layer("transportation_name"):
        if not feature.name or feature.geometry != "line":
            continue
        for part in feature.parts:
            if len(part) >= 2:
                streets.append(StreetName(feature.name, [_to_lat_lng(tile, u, v) for u, v in part]))
    return streets


def _to_lat_lng(tile: VectorTile, u: float, v: float) -> tuple[float, float]:
    n = 2**tile.z
    world_x = (tile.x + u / tile.extent) / n
    world_y = (tile.y + v / tile.extent) / n
    lng = world_x * 360.0 - 180.0
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * world_y))))
    return round(lat, 6), round(lng, 6)
