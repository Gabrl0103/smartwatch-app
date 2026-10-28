from typing import Protocol

from domain.models.route import Coordinates
from domain.models.saved_place import SavedPlace


class SavedPlaceRepositoryPort(Protocol):
    def list(self, user_id: int) -> list[SavedPlace]: ...

    def add(self, user_id: int, name: str, coordinates: Coordinates) -> SavedPlace: ...

    def delete(self, user_id: int, place_id: int) -> bool:
        """Devuelve False si el lugar no existe o no es del usuario."""
        ...
