from sqlalchemy import text
from sqlalchemy.engine import Engine
from sqlalchemy.exc import SQLAlchemyError


class SqlDatabaseHealth:
    def __init__(self, engine: Engine):
        self._engine = engine

    def is_available(self) -> bool:
        try:
            with self._engine.connect() as connection:
                connection.execute(text("SELECT 1"))
            return True
        except SQLAlchemyError:
            return False
