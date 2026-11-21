from collections import OrderedDict
from typing import Generic, Hashable, TypeVar

DEFAULT_MAX_TILES = 500

V = TypeVar("V")


class TileCache(Generic[V]):
    """Caché en memoria de tiles por clave (z, x, y). Al llenarse descarta el menos usado."""

    def __init__(self, max_tiles: int = DEFAULT_MAX_TILES):
        self._max_tiles = max_tiles
        self._tiles: OrderedDict[Hashable, V] = OrderedDict()

    def get(self, key: Hashable) -> V | None:
        tile = self._tiles.get(key)
        if tile is not None:
            self._tiles.move_to_end(key)
        return tile

    def put(self, key: Hashable, tile: V) -> None:
        self._tiles[key] = tile
        self._tiles.move_to_end(key)
        while len(self._tiles) > self._max_tiles:
            self._tiles.popitem(last=False)

    def __len__(self) -> int:
        return len(self._tiles)
