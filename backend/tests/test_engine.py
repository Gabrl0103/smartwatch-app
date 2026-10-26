from infrastructure.database.engine import normalize_database_url


def test_postgresql_prefix_uses_psycopg2():
    url = "postgresql://u:p@host/db?sslmode=require"
    assert normalize_database_url(url) == "postgresql+psycopg2://u:p@host/db?sslmode=require"


def test_postgres_prefix_uses_psycopg2():
    assert normalize_database_url("postgres://u:p@host/db").startswith("postgresql+psycopg2://")


def test_other_urls_are_left_unchanged():
    assert normalize_database_url("sqlite://") == "sqlite://"
