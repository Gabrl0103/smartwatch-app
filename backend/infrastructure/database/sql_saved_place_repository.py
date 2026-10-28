from sqlalchemy import delete, insert, select
from sqlalchemy.engine import Engine

from domain.models.route import Coordinates
from domain.models.saved_place import SavedPlace
from infrastructure.database.tables import saved_places


def _to_domain(row) -> SavedPlace:
    return SavedPlace(row.id, row.name, Coordinates(row.lat, row.lng), row.created_at)


class SqlSavedPlaceRepository:
    def __init__(self, engine: Engine):
        self._engine = engine

    def list(self, user_id: int) -> list[SavedPlace]:
        query = (
            select(saved_places)
            .where(saved_places.c.user_id == user_id)
            .order_by(saved_places.c.name, saved_places.c.id)
        )
        with self._engine.connect() as connection:
            return [_to_domain(row) for row in connection.execute(query)]

    def add(self, user_id: int, name: str, coordinates: Coordinates) -> SavedPlace:
        statement = (
            insert(saved_places)
            .values(user_id=user_id, name=name, lat=coordinates.lat, lng=coordinates.lng)
            .returning(*saved_places.c)
        )
        with self._engine.begin() as connection:
            return _to_domain(connection.execute(statement).one())

    def delete(self, user_id: int, place_id: int) -> bool:
        statement = delete(saved_places).where(
            saved_places.c.id == place_id, saved_places.c.user_id == user_id
        )
        with self._engine.begin() as connection:
            return connection.execute(statement).rowcount > 0
