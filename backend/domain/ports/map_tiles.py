from typing import Protocol


class MapTileProviderPort(Protocol):
    def get_tile(self, z: int, x: int, y: int) -> bytes:
        """Devuelve el tile (z, x, y) del mapa como PNG."""
        ...
