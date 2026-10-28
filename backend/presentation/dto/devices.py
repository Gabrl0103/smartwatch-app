from datetime import datetime

from pydantic import BaseModel

from domain.models.device import WEAR_OS, Device


class DeviceRequest(BaseModel):
    device_id: str | None = None
    type: str | None = WEAR_OS


class DeviceResponse(BaseModel):
    id: str
    type: str
    registered_at: datetime

    @classmethod
    def from_domain(cls, device: Device) -> "DeviceResponse":
        return cls(id=device.id, type=device.type, registered_at=device.registered_at)
