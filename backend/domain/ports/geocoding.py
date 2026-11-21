from typing import Protocol

from domain.models.place_kind import PlaceKind
from domain.models.route import Coordinates, Place


class GeocodingPort(Protocol):
    def search(self, query: str, near: Coordinates | None = None, within_m: float | None = None) -> Place | None:
        """El lugar con ese nombre más relevante cerca de [near]; con [within_m], solo a esa distancia."""
        ...

    def nearest(self, kind: PlaceKind, near: Coordinates | None, within_m: float) -> Place | None:
        """El lugar de ese tipo más cercano a [near], a [within_m] como mucho."""
        ...
