from sqlalchemy import insert
from sqlalchemy.engine import Engine

from infrastructure.database.tables import vibration_events


class SqlVibrationEventRepository:
    def __init__(self, engine: Engine):
        self._engine = engine

    def save(self, pattern: str, route_step_id: int | None) -> int:
        statement = (
            insert(vibration_events)
            .values(pattern=pattern, route_step_id=route_step_id)
            .returning(vibration_events.c.id)
        )
        with self._engine.begin() as connection:
            return connection.execute(statement).scalar_one()
