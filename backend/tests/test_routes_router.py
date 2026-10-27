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


def use_routing(monkeypatch):
    monkeypatch.setattr("presentation.routers.routes.get_routing_provider", lambda: FakeRouting())


def test_create_route_returns_route(monkeypatch):
    use_routing(monkeypatch)
    response = TestClient(app).post("/routes", json=BODY)
    assert response.status_code == 200
    data = response.json()
    assert data["destination_name"] == "Parque Bolívar"
    assert data["distance_m"] == 150
    assert data["steps"] == [
        {"order": 1, "maneuver": "arrive", "instruction": "Ha llegado.", "distance_m": 0, "lat": 1.2006, "lng": -77.2786}
    ]
    assert data["polyline"] == [[1.2142, -77.2785], [1.2006, -77.2786]]


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
