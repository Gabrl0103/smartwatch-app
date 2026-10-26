"""Composition root: único lugar donde se eligen las implementaciones."""

from functools import lru_cache

from domain.errors import ExternalServiceError
from domain.ports.database_health import DatabaseHealthPort
from infrastructure.config.settings import load_settings
from infrastructure.database.engine import create_db_engine
from infrastructure.database.sql_database_health import SqlDatabaseHealth


@lru_cache
def get_database_health() -> DatabaseHealthPort:
    settings = load_settings()
    if not settings.database_url:
        raise ExternalServiceError("Falta configurar DATABASE_URL.")
    return SqlDatabaseHealth(create_db_engine(settings.database_url))
