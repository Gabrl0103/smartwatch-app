from sqlalchemy import insert, select
from sqlalchemy.engine import Engine

from domain.models.route import Route, RouteStatus
from infrastructure.database.tables import route_steps, routes


class SqlRouteRepository:
    def __init__(self, engine: Engine):
        self._engine = engine

    def save(self, user_id: int, route: Route, status: RouteStatus) -> int:
        """Guarda la ruta y sus pasos en una sola transacción."""
        with self._engine.begin() as connection:
            route_id = connection.execute(
                insert(routes)
                .values(
                    user_id=user_id,
                    origin_lat=route.origin.lat,
                    origin_lng=route.origin.lng,
                    destination_lat=route.destination.lat,
                    destination_lng=route.destination.lng,
                    destination_name=route.destination_name,
                    status=status.value,
                )
                .returning(routes.c.id)
            ).scalar_one()
            if route.steps:
                connection.execute(
                    insert(route_steps),
                    [
                        {
                            "route_id": route_id,
                            "step_order": step.order,
                            "maneuver": step.maneuver.value,
                            "distance_to_next_m": step.distance_to_next_m,
                            "lat": step.point.lat,
                            "lng": step.point.lng,
                        }
                        for step in route.steps
                    ],
                )
        return route_id

    def find_step_id(self, route_id: int, step_order: int) -> int | None:
        query = select(route_steps.c.id).where(
            route_steps.c.route_id == route_id, route_steps.c.step_order == step_order
        )
        with self._engine.connect() as connection:
            return connection.execute(query).scalar_one_or_none()
