from datetime import datetime

from pydantic import BaseModel

from domain.models.route import Coordinates
from domain.models.saved_place import SavedPlace


class SavedPlaceRequest(BaseModel):
    # Opcionales para que el caso de uso valide y responda con el formato de error común.
    name: str | None = None
    lat: float | None = None
    lng: float | None = None

    def coordinates(self) -> Coordinates:
        return Coordinates(lat=self.lat, lng=self.lng)


class SavedPlaceResponse(BaseModel):
    id: int
    name: str
    lat: float
    lng: float
    created_at: datetime

    @classmethod
    def from_domain(cls, place: SavedPlace) -> "SavedPlaceResponse":
        return cls(
            id=place.id,
            name=place.name,
            lat=place.coordinates.lat,
            lng=place.coordinates.lng,
            created_at=place.created_at,
        )
