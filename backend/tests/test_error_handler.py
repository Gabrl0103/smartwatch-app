from fastapi import FastAPI
from fastapi.testclient import TestClient

from domain.errors import NotFoundError, ValidationError
from presentation.middleware.error_handler import register_error_handlers


def _client():
    app = FastAPI()
    register_error_handlers(app)

    @app.get("/not-found")
    def not_found():
        raise NotFoundError("No existe.")

    @app.get("/invalid")
    def invalid():
        raise ValidationError("Dato inválido.")

    @app.get("/boom")
    def boom():
        raise RuntimeError("fallo interno")

    return TestClient(app, raise_server_exceptions=False)


def test_not_found_maps_to_404():
    r = _client().get("/not-found")
    assert r.status_code == 404
    assert r.json()["error"]["code"] == "NOT_FOUND"


def test_validation_maps_to_422():
    assert _client().get("/invalid").status_code == 422


def test_unexpected_error_maps_to_500_without_leaking_details():
    r = _client().get("/boom")
    assert r.status_code == 500
    assert "fallo interno" not in r.text
