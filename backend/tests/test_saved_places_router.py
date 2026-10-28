from datetime import datetime

from fastapi.testclient import TestClient

from domain.models.saved_place import SavedPlace
from main import app

client = TestClient(app)


class FakeSavedPlaces:
    def __init__(self):
        self.places = {}

    def list(self, user_id):
        return list(self.places.values())

    def add(self, user_id, name, coordinates):
        place = SavedPlace(len(self.places) + 1, name, coordinates, datetime(2026, 10, 28, 12, 0))
        self.places[place.id] = place
        return place

    def delete(self, user_id, place_id):
        return self.places.pop(place_id, None) is not None


def use_repository(monkeypatch):
    repository = FakeSavedPlaces()
    monkeypatch.setattr(
        "presentation.routers.saved_places.get_saved_place_repository", lambda: repository
    )
    return repository


def test_add_then_list(monkeypatch):
    use_repository(monkeypatch)

    created = client.post("/saved-places", json={"name": "Casa", "lat": 1.21, "lng": -77.28})

    assert created.status_code == 201
    assert created.json() == {
        "id": 1,
        "name": "Casa",
        "lat": 1.21,
        "lng": -77.28,
        "created_at": "2026-10-28T12:00:00",
    }
    assert client.get("/saved-places").json() == [created.json()]


def test_list_empty(monkeypatch):
    use_repository(monkeypatch)
    response = client.get("/saved-places")
    assert response.status_code == 200
    assert response.json() == []


def test_add_without_name_returns_422(monkeypatch):
    use_repository(monkeypatch)
    response = client.post("/saved-places", json={"lat": 1.21, "lng": -77.28})
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"


def test_delete_returns_204_then_404(monkeypatch):
    use_repository(monkeypatch)
    client.post("/saved-places", json={"name": "Casa", "lat": 1.21, "lng": -77.28})

    assert client.delete("/saved-places/1").status_code == 204
    missing = client.delete("/saved-places/1")
    assert missing.status_code == 404
    assert missing.json()["error"]["code"] == "NOT_FOUND"
