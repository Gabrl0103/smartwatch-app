from infrastructure.database.engine import create_db_engine
from infrastructure.database.sql_database_health import SqlDatabaseHealth


def test_is_available_with_working_engine():
    engine = create_db_engine("sqlite://")
    assert SqlDatabaseHealth(engine).is_available() is True


def test_is_not_available_with_unreachable_engine():
    engine = create_db_engine("postgresql://u:p@127.0.0.1:1/db?connect_timeout=1")
    assert SqlDatabaseHealth(engine).is_available() is False
