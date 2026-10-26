from typing import Protocol

from domain.models.route import Coordinates, Place


class GeocodingPort(Protocol):
    def search(self, query: str, near: Coordinates | None = None) -> Place | None: ...
