from dataclasses import replace

from domain.errors import ValidationError
from domain.models.route import Coordinates, Route
from domain.ports.routing_provider import RoutingProviderPort


def _validate(point: Coordinates | None, label: str) -> Coordinates:
    if point is None or point.lat is None or point.lng is None:
        raise ValidationError(f"Falta la coordenada de {label}.")
    if not (-90 <= point.lat <= 90 and -180 <= point.lng <= 180):
        raise ValidationError(f"La coordenada de {label} está fuera de rango.")
    return point


def create_route(
    routing: RoutingProviderPort,
    origin: Coordinates | None,
    destination: Coordinates | None,
    destination_name: str = "",
) -> Route:
    """Calcula la ruta a pie entre dos puntos. Falla con errores de dominio."""
    origin = _validate(origin, "origen")
    destination = _validate(destination, "destino")
    route = routing.get_walking_route(origin, destination)
    return replace(route, destination_name=destination_name.strip())
