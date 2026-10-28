from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


class FakeEvents:
    def __init__(self):
        self.saved = []

    def save(self, pattern, route_step_id):
        self.saved.append((pattern, route_step_id))
        return len(self.saved)


class FakeRoutes:
    def find_step_id(self, route_id, step_order):
        return 30 if (route_id, step_order) == (3, 1) else None


def use_repositories(monkeypatch):
    events = FakeEvents()
    monkeypatch.setattr("presentation.routers.vibration_events.get_vibration_event_repository", lambda: events)
    monkeypatch.setattr("presentation.routers.vibration_events.get_route_repository", lambda: FakeRoutes())
    return events


def test_record_linked_to_step(monkeypatch):
    events = use_repositories(monkeypatch)

    response = client.post(
        "/vibration-events", json={"pattern": "2 pulsos cortos", "route_id": 3, "step_order": 1}
    )

    assert response.status_code == 201
    assert response.json() == {"id": 1}
    assert events.saved == [("2 pulsos cortos", 30)]


def test_record_without_route(monkeypatch):
    events = use_repositories(monkeypatch)
    response = client.post("/vibration-events", json={"pattern": "1 pulso cada 5 s"})
    assert response.status_code == 201
    assert events.saved == [("1 pulso cada 5 s", None)]


def test_unknown_step_returns_404(monkeypatch):
    use_repositories(monkeypatch)
    response = client.post("/vibration-events", json={"pattern": "x", "route_id": 3, "step_order": 8})
    assert response.status_code == 404


def test_missing_pattern_returns_422(monkeypatch):
    use_repositories(monkeypatch)
    assert client.post("/vibration-events", json={}).status_code == 422
