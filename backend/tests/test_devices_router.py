from datetime import datetime

from fastapi.testclient import TestClient

from domain.models.device import Device
from main import app

client = TestClient(app)


class FakeDevices:
    def __init__(self):
        self.devices = {}

    def register(self, user_id, device_id, device_type):
        return self.devices.setdefault(
            device_id, Device(device_id, device_type, datetime(2026, 10, 28, 12, len(self.devices)))
        )


def use_repository(monkeypatch):
    monkeypatch.setattr("presentation.routers.devices.get_device_repository", lambda: FakeDevices())


def test_register_twice_returns_same_device(monkeypatch):
    repository = FakeDevices()
    monkeypatch.setattr("presentation.routers.devices.get_device_repository", lambda: repository)

    first = client.post("/devices", json={"device_id": "reloj-1"})
    second = client.post("/devices", json={"device_id": "reloj-1", "type": "wear_os"})

    assert first.status_code == 200
    assert first.json() == {"id": "reloj-1", "type": "wear_os", "registered_at": "2026-10-28T12:00:00"}
    assert second.json() == first.json()


def test_missing_device_id_returns_422(monkeypatch):
    use_repository(monkeypatch)
    response = client.post("/devices", json={})
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"
