from typing import Protocol

from domain.models.route import Coordinates, Route


class RoutingProviderPort(Protocol):
    def get_walking_route(self, origin: Coordinates, destination: Coordinates) -> Route: ...
