from fastapi import FastAPI
from fastapi.testclient import TestClient

from domain.errors import ExternalServiceError, NotFoundError, QuotaExhaustedError, ValidationError
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

    @app.get("/quota")
    def quota():
        raise QuotaExhaustedError("Sin cuota.")

    @app.get("/quota-wait")
    def quota_wait():
        raise QuotaExhaustedError("Espera.", retry_after_s=27)

    @app.get("/external")
    def external():
        raise ExternalServiceError("Falló.")

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


def test_quota_exhausted_maps_to_429_before_its_parent():
    r = _client().get("/quota")
    assert r.status_code == 429
    assert r.json()["error"]["code"] == "QUOTA_EXHAUSTED"
    assert _client().get("/external").status_code == 502


def test_quota_error_says_how_long_to_wait_when_it_knows():
    r = _client().get("/quota-wait")
    assert r.status_code == 429
    assert r.headers["retry-after"] == "27"
    assert r.json()["error"] == {"code": "QUOTA_EXHAUSTED", "message": "Espera.", "retry_after_s": 27}


def test_quota_error_without_a_wait_does_not_invent_one():
    r = _client().get("/quota")
    assert "retry-after" not in r.headers
    assert "retry_after_s" not in r.json()["error"]


def test_unexpected_error_maps_to_500_without_leaking_details():
    r = _client().get("/boom")
    assert r.status_code == 500
    assert "fallo interno" not in r.text
