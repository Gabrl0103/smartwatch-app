from domain.errors import NotFoundError, ValidationError
from domain.models.route import Coordinates, Place
from domain.ports.geocoding import GeocodingPort


def geocode_destination(
    geocoder: GeocodingPort, query: str, near: Coordinates | None = None
) -> Place:
    """Busca el destino pedido por el usuario. Falla con errores de dominio."""
    query = query.strip()
    if not query:
        raise ValidationError("El destino no puede estar vacío.")

    place = geocoder.search(query, near)
    if place is None:
        raise NotFoundError(f"No encontré el lugar «{query}».")
    return place
