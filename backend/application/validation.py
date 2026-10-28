from domain.errors import ValidationError
from domain.models.route import Coordinates


def require_coordinates(point: Coordinates | None, label: str) -> Coordinates:
    """Exige una coordenada completa y dentro de rango; `label` nombra el punto en el error."""
    if point is None or point.lat is None or point.lng is None:
        raise ValidationError(f"Falta la coordenada de {label}.")
    if not (-90 <= point.lat <= 90 and -180 <= point.lng <= 180):
        raise ValidationError(f"La coordenada de {label} está fuera de rango.")
    return point


def require_text(value: str | None, label: str, max_length: int) -> str:
    """Exige un texto no vacío (sin espacios a los lados) de hasta `max_length` caracteres."""
    value = (value or "").strip()
    if not value:
        raise ValidationError(f"Falta {label}.")
    if len(value) > max_length:
        raise ValidationError(f"{label[0].upper()}{label[1:]} es demasiado largo.")
    return value
