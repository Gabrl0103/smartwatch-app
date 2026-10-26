from domain.errors import ExternalServiceError
from domain.ports.database_health import DatabaseHealthPort


def check_database_health(database: DatabaseHealthPort) -> None:
    """Falla con un error de dominio si la base de datos no responde."""
    if not database.is_available():
        raise ExternalServiceError("No hay conexión con la base de datos.")
