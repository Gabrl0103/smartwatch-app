from fastapi import APIRouter

from application.use_cases.geocode_destination import geocode_destination
from domain.models.route import Coordinates
from infrastructure.config.container import get_geocoder
from presentation.dto.places import PlaceResponse

router = APIRouter(prefix="/places")


@router.get("/search", response_model=PlaceResponse)
def search(q: str = "", lat: float | None = None, lng: float | None = None):
    """Busca un lugar por nombre; lat/lng (opcionales) priorizan resultados cercanos."""
    near = Coordinates(lat, lng) if lat is not None and lng is not None else None
    place = geocode_destination(get_geocoder(), q, near)
    return PlaceResponse.from_domain(place)
