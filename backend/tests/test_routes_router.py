from fastapi.testclient import TestClient

from domain.models.route import Coordinates, Maneuver, Route, RouteStep
from main import app

BODY = {
    "origin": {"lat": 1.2142, "lng": -77.2785},
    "destination": {"lat": 1.2006, "lng": -77.2786},
    "destination_name": "Parque Bolívar",
}


class FakeRouting:
    def get_walking_route(self, origin, destination):
        step = RouteStep(1, Maneuver.ARRIVE, "Ha llegado.", 0, destination)
        return Route(origin, destination, "", 150, 140, [step], [origin, destination])


class FakeRouteRepository:
    def __init__(self, error=None):
        self.error = error
        self.saved = []

    def save(self, user_id, route, status):
        if self.error:
            raise self.error
        self.saved.append((user_id, route.destination_name, status.value))
        return 42


def use_routing(monkeypatch, repository=None):
    repository = repository or FakeRouteRepository()
    monkeypatch.setattr("presentation.routers.routes.get_routing_provider", lambda: FakeRouting())
    monkeypatch.setattr("presentation.routers.routes.get_route_repository", lambda: repository)
    return repository


def test_create_route_returns_route(monkeypatch):
    repository = use_routing(monkeypatch)
    response = TestClient(app).post("/routes", json=BODY)
    assert response.status_code == 200
    data = response.json()
    assert data["route_id"] == 42
    assert repository.saved == [(1, "Parque Bolívar", "active")]
    assert data["destination_name"] == "Parque Bolívar"
    assert data["distance_m"] == 150
    assert data["steps"] == [
        {
            "order": 1,
            "maneuver": "arrive",
            "instruction": "Ha llegado.",
            "distance_m": 0,
            "lat": 1.2006,
            "lng": -77.2786,
            "street": "",
            "roundabout_exit": None,
            "then_maneuver": None,
            "destination_side": "",
        }
    ]
    # 150 m en 140 s (1,07 m/s) se aleja más de un 10 % de 1,25 m/s: se recalcula.
    assert data["duration_s"] == 120
    assert data["polyline"] == [[1.2142, -77.2785], [1.2006, -77.2786]]


def test_route_is_returned_even_if_saving_fails(monkeypatch):
    use_routing(monkeypatch, FakeRouteRepository(RuntimeError("base caída")))
    response = TestClient(app).post("/routes", json=BODY)
    assert response.status_code == 200
    assert response.json()["route_id"] is None
    assert response.json()["distance_m"] == 150


def test_missing_destination_returns_422(monkeypatch):
    use_routing(monkeypatch)
    response = TestClient(app).post("/routes", json={"origin": BODY["origin"]})
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


def test_out_of_range_origin_returns_422(monkeypatch):
    use_routing(monkeypatch)
    body = {**BODY, "origin": {"lat": 120, "lng": 0}}
    response = TestClient(app).post("/routes", json=body)
    assert response.status_code == 422


def test_destination_too_far_returns_422_with_message(monkeypatch):
    use_routing(monkeypatch)
    body = {**BODY, "destination": {"lat": 4.7110, "lng": -74.0721}}
    response = TestClient(app).post("/routes", json=body)
    assert response.status_code == 422
    assert response.json()["error"]["message"] == "Estás muy lejos del destino"
