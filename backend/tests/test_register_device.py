from datetime import datetime

import pytest

from application.use_cases.register_device import register_device
from domain.errors import ValidationError
from domain.models.device import Device
from domain.models.user import DEFAULT_USER_ID


class FakeDevices:
    def __init__(self):
        self.calls = []

    def register(self, user_id, device_id, device_type):
        self.calls.append((user_id, device_id, device_type))
        return Device(device_id, device_type, datetime(2026, 10, 28))


def test_registers_trimmed_id_as_wear_os_for_default_user():
    repository = FakeDevices()

    device = register_device(repository, " reloj-1 ", None)

    assert (device.id, device.type) == ("reloj-1", "wear_os")
    assert repository.calls == [(DEFAULT_USER_ID, "reloj-1", "wear_os")]


def test_missing_device_id_fails():
    repository = FakeDevices()
    with pytest.raises(ValidationError):
        register_device(repository, "")
    assert repository.calls == []


def test_too_long_device_id_fails():
    with pytest.raises(ValidationError):
        register_device(FakeDevices(), "x" * 65)


def test_unsupported_type_fails():
    with pytest.raises(ValidationError):
        register_device(FakeDevices(), "reloj-1", "apple_watch")
