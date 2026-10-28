from sqlalchemy import insert, select
from sqlalchemy.engine import Engine
from sqlalchemy.exc import IntegrityError

from domain.models.device import Device
from infrastructure.database.tables import devices


def _to_domain(row) -> Device:
    return Device(row.id, row.type, row.registered_at)


class SqlDeviceRepository:
    def __init__(self, engine: Engine):
        self._engine = engine

    def register(self, user_id: int, device_id: str, device_type: str) -> Device:
        existing = self._find(device_id)
        if existing:
            return existing
        statement = (
            insert(devices)
            .values(id=device_id, user_id=user_id, type=device_type)
            .returning(*devices.c)
        )
        try:
            with self._engine.begin() as connection:
                return _to_domain(connection.execute(statement).one())
        except IntegrityError:
            # Otro request lo registró entre la búsqueda y el insert.
            existing = self._find(device_id)
            if existing is None:
                raise
            return existing

    def _find(self, device_id: str) -> Device | None:
        query = select(devices).where(devices.c.id == device_id)
        with self._engine.connect() as connection:
            row = connection.execute(query).one_or_none()
        return _to_domain(row) if row else None
