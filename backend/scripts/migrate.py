"""Aplica en orden las migraciones SQL de database/migrations/ y anota cuáles ya corrió.

Uso, desde backend/: python scripts/migrate.py
Lee DATABASE_URL del entorno o de backend/.env. Se puede correr varias veces: solo aplica
las migraciones que no estén en la tabla schema_migrations.
"""

import re
import sys
from pathlib import Path

# Permite correrlo como script (python scripts/migrate.py) y no solo como módulo.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sqlalchemy import text  # noqa: E402
from sqlalchemy.engine import Engine  # noqa: E402

from infrastructure.config.settings import load_settings  # noqa: E402
from infrastructure.database.engine import create_db_engine  # noqa: E402

MIGRATIONS_DIR = Path(__file__).resolve().parents[2] / "database" / "migrations"

_CREATE_SCHEMA_MIGRATIONS = """
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
"""


def split_statements(sql: str) -> list[str]:
    """Quita los comentarios -- y separa por ';'. Las migraciones no usan ';' ni '--' en textos."""
    without_comments = re.sub(r"--[^\n]*", "", sql)
    return [statement.strip() for statement in without_comments.split(";") if statement.strip()]


def applied_versions(engine: Engine) -> set[str]:
    with engine.begin() as connection:
        connection.execute(text(_CREATE_SCHEMA_MIGRATIONS))
        return set(connection.execute(text("SELECT version FROM schema_migrations")).scalars())


def apply_migrations(engine: Engine, directory: Path = MIGRATIONS_DIR) -> list[str]:
    """Corre las migraciones pendientes en orden, cada una en su transacción.

    Devuelve los nombres de las que aplicó. Si una falla, se detiene sin anotarla.
    """
    done = applied_versions(engine)
    applied = []
    for path in sorted(directory.glob("*.sql")):
        if path.name in done:
            continue
        with engine.begin() as connection:
            for statement in split_statements(path.read_text(encoding="utf-8")):
                connection.exec_driver_sql(statement)
            connection.execute(
                text("INSERT INTO schema_migrations (version) VALUES (:version)"),
                {"version": path.name},
            )
        applied.append(path.name)
    return applied


def main() -> int:
    database_url = load_settings().database_url
    if not database_url:
        print("Falta configurar DATABASE_URL.", file=sys.stderr)
        return 1
    applied = apply_migrations(create_db_engine(database_url))
    if applied:
        print("Migraciones aplicadas:", ", ".join(applied))
    else:
        print("No hay migraciones pendientes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
