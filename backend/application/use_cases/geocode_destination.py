from application.place_kinds import place_kind_of
from application.validation import MAX_WALKING_DISTANCE_M
from domain.errors import NotFoundError, ValidationError
from domain.models.place_kind import PLACE_KIND_LABELS
from domain.models.route import Coordinates, Place
from domain.ports.geocoding import GeocodingPort

# Hasta dónde se busca "la droguería más cercana": más lejos ya no es cerca para ir a pie.
NEAREST_RADIUS_M = 2_000


def geocode_destination(
    geocoder: GeocodingPort, query: str, near: Coordinates | None = None
) -> Place:
    """Busca el destino pedido por el usuario. Un tipo de lugar sin nombre ("droguería más
    cercana") da el más cercano a [near] en [NEAREST_RADIUS_M]; un nombre se busca solo a
    distancia caminable, para no proponer un lugar de otra ciudad. Falla con errores de dominio."""
    query = query.strip()
    if not query:
        raise ValidationError("El destino no puede estar vacío.")

    kind = place_kind_of(query)
    if kind is not None:
        place = geocoder.nearest(kind, near, NEAREST_RADIUS_M)
        if place is None:
            raise NotFoundError(f"No encontré {PLACE_KIND_LABELS[kind][0]} cerca de ti.")
        return place

    place = geocoder.search(query, near, MAX_WALKING_DISTANCE_M)
    if place is None:
        raise NotFoundError(f"No encontré «{query}» cerca de ti.")
    return place
