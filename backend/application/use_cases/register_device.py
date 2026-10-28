from application.validation import require_text
from domain.errors import ValidationError
from domain.models.device import WEAR_OS, Device
from domain.models.user import DEFAULT_USER_ID
from domain.ports.device_repository import DeviceRepositoryPort

MAX_DEVICE_ID_LENGTH = 64
SUPPORTED_TYPES = {WEAR_OS}


def register_device(
    repository: DeviceRepositoryPort, device_id: str | None, device_type: str | None = WEAR_OS
) -> Device:
    """Registra el reloj. Repetirlo con el mismo id devuelve el registro original."""
    device_id = require_text(device_id, "el id del dispositivo", MAX_DEVICE_ID_LENGTH)
    device_type = (device_type or WEAR_OS).strip().lower()
    if device_type not in SUPPORTED_TYPES:
        raise ValidationError(f"Tipo de dispositivo no soportado: {device_type}.")
    return repository.register(DEFAULT_USER_ID, device_id, device_type)
