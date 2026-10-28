from typing import Protocol

from domain.models.route import Route, RouteStatus


class RouteRepositoryPort(Protocol):
    def save(self, user_id: int, route: Route, status: RouteStatus) -> int:
        """Guarda la ruta con sus pasos y devuelve su id."""
        ...

    def find_step_id(self, route_id: int, step_order: int) -> int | None: ...
