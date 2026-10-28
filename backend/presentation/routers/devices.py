from fastapi import APIRouter

from application.use_cases.register_device import register_device
from infrastructure.config.container import get_device_repository
from presentation.dto.devices import DeviceRequest, DeviceResponse

router = APIRouter(prefix="/devices")


@router.post("", response_model=DeviceResponse)
def register(body: DeviceRequest):
    """Idempotente: registrar dos veces el mismo device_id devuelve el registro original."""
    device = register_device(get_device_repository(), body.device_id, body.type)
    return DeviceResponse.from_domain(device)
