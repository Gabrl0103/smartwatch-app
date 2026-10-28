from typing import Protocol

from domain.models.device import Device


class DeviceRepositoryPort(Protocol):
    def register(self, user_id: int, device_id: str, device_type: str) -> Device:
        """Registra el dispositivo; si ya existe, devuelve el registro original."""
        ...
