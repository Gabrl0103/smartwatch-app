from pydantic import BaseModel

from domain.models.route import Place


class PlaceResponse(BaseModel):
    name: str
    lat: float
    lng: float

    @classmethod
    def from_domain(cls, place: Place) -> "PlaceResponse":
        return cls(name=place.name, lat=place.coordinates.lat, lng=place.coordinates.lng)
