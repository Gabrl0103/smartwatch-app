from dataclasses import replace

from application.validation import require_coordinates, require_walkable
from domain.models.route import Coordinates, Route
from domain.ports.routing_provider import RoutingProviderPort


def create_route(
    routing: RoutingProviderPort,
    origin: Coordinates | None,
    destination: Coordinates | None,
    destination_name: str = "",
) -> Route:
    """Calcula la ruta a pie entre dos puntos. Falla con errores de dominio."""
    origin = require_coordinates(origin, "origen")
    destination = require_coordinates(destination, "destino")
    require_walkable(origin, destination)
    route = routing.get_walking_route(origin, destination)
    return replace(route, destination_name=destination_name.strip())
