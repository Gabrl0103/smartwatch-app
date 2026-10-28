import pytest
from sqlalchemy import create_engine, text
from sqlalchemy.exc import OperationalError

from scripts.migrate import MIGRATIONS_DIR, applied_versions, apply_migrations, split_statements


def write(directory, name, sql):
    (directory / name).write_text(sql, encoding="utf-8")


def names(engine):
    with engine.connect() as connection:
        return list(connection.execute(text("SELECT name FROM users ORDER BY id")).scalars())


@pytest.fixture
def engine():
    return create_engine("sqlite://")


@pytest.fixture
def migrations(tmp_path):
    write(tmp_path, "001_create.sql", "-- tabla; de prueba\nCREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);")
    write(tmp_path, "002_seed.sql", "INSERT INTO users (id, name) VALUES (1, 'Usuario');")
    return tmp_path


def test_split_statements_ignores_comments_and_empty_parts():
    sql = "-- comentario; con punto y coma\nCREATE TABLE a (id INT);\n\nINSERT INTO a VALUES (1); -- fin\n"
    assert split_statements(sql) == ["CREATE TABLE a (id INT)", "INSERT INTO a VALUES (1)"]


def test_applies_migrations_in_order_and_records_them(engine, migrations):
    assert apply_migrations(engine, migrations) == ["001_create.sql", "002_seed.sql"]
    assert names(engine) == ["Usuario"]
    assert applied_versions(engine) == {"001_create.sql", "002_seed.sql"}


def test_running_twice_applies_nothing_new(engine, migrations):
    apply_migrations(engine, migrations)
    assert apply_migrations(engine, migrations) == []
    assert names(engine) == ["Usuario"]


def test_only_pending_migrations_run(engine, migrations):
    apply_migrations(engine, migrations)
    write(migrations, "003_more.sql", "INSERT INTO users (id, name) VALUES (2, 'Otro');")
    assert apply_migrations(engine, migrations) == ["003_more.sql"]
    assert names(engine) == ["Usuario", "Otro"]


def test_failed_migration_is_not_recorded(engine, migrations):
    write(migrations, "002_seed.sql", "INSERT INTO tabla_inexistente VALUES (1);")
    with pytest.raises(OperationalError):
        apply_migrations(engine, migrations)
    assert applied_versions(engine) == {"001_create.sql"}


def test_project_migrations_exist_in_order():
    files = sorted(path.name for path in MIGRATIONS_DIR.glob("*.sql"))
    assert files[:2] == ["001_create_tables.sql", "002_seed_default_user.sql"]
