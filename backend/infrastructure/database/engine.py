from sqlalchemy import create_engine
from sqlalchemy.engine import Engine


def normalize_database_url(database_url: str) -> str:
    """Fuerza el driver psycopg2, que es el que instalamos.

    Neon y Render entregan URLs que empiezan con postgres:// o postgresql://.
    """
    for prefix in ("postgres://", "postgresql://"):
        if database_url.startswith(prefix):
            return "postgresql+psycopg2://" + database_url[len(prefix):]
    return database_url


def create_db_engine(database_url: str) -> Engine:
    return create_engine(normalize_database_url(database_url), pool_pre_ping=True)
