from typing import Protocol

from domain.models.map_tile import VectorTile


class VectorTileSourcePort(Protocol):
    max_zoom: int

    def get_vector_tile(self, z: int, x: int, y: int) -> VectorTile:
        """Devuelve el tile vectorial (z, x, y), con z hasta `max_zoom`."""
        ...


class MapTileRendererPort(Protocol):
    def render(self, tile: VectorTile, z: int, x: int, y: int) -> bytes:
        """Dibuja como PNG el tile (z, x, y), que cae dentro de `tile` (el mismo o un hijo suyo)."""
        ...
