from fastapi.testclient import TestClient

from domain.models.route import Coordinates, Place
from main import app


class FakeGeocoder:
    def __init__(self, result):
        self.result = result
        self.near = None

    def search(self, query, near=None):
        self.near = near
        return self.result


def use_geocoder(monkeypatch, geocoder):
    monkeypatch.setattr("presentation.routers.places.get_geocoder", lambda: geocoder)


def test_search_returns_place(monkeypatch):
    geocoder = FakeGeocoder(Place("Parque Bolívar", Coordinates(1.2, -77.3)))
    use_geocoder(monkeypatch, geocoder)
    response = TestClient(app).get("/places/search", params={"q": "parque", "lat": 1.21, "lng": -77.28})
    assert response.status_code == 200
    assert response.json() == {"name": "Parque Bolívar", "lat": 1.2, "lng": -77.3}
    assert geocoder.near == Coordinates(1.21, -77.28)


def test_search_not_found_returns_404(monkeypatch):
    use_geocoder(monkeypatch, FakeGeocoder(None))
    response = TestClient(app).get("/places/search", params={"q": "nada"})
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "NOT_FOUND"


def test_search_empty_query_returns_422(monkeypatch):
    use_geocoder(monkeypatch, FakeGeocoder(None))
    response = TestClient(app).get("/places/search")
    assert response.status_code == 422
