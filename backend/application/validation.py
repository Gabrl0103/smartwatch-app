import math

from domain.errors import ValidationError
from domain.models.route import Coordinates

EARTH_RADIUS_M = 6_371_008.8
# Más allá de esto no se pide una ruta a pie: el lugar encontrado casi seguro es otro.
MAX_WALKING_DISTANCE_M = 50_000
TOO_FAR_MESSAGE = "Estás muy lejos del destino"


def require_coordinates(point: Coordinates | None, label: str) -> Coordinates:
    """Exige una coordenada completa y dentro de rango; `label` nombra el punto en el error."""
    if point is None or point.lat is None or point.lng is None:
        raise ValidationError(f"Falta la coordenada de {label}.")
    if not (-90 <= point.lat <= 90 and -180 <= point.lng <= 180):
        raise ValidationError(f"La coordenada de {label} está fuera de rango.")
    return point


def distance_m(a: Coordinates, b: Coordinates) -> float:
    """Distancia en línea recta (haversine) entre dos puntos, en metros."""
    lat1, lat2 = math.radians(a.lat), math.radians(b.lat)
    dlat = lat2 - lat1
    dlng = math.radians(b.lng - a.lng)
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlng / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


def require_walkable(origin: Coordinates, destination: Coordinates) -> None:
    """Rechaza destinos a más de MAX_WALKING_DISTANCE_M del origen."""
    if distance_m(origin, destination) > MAX_WALKING_DISTANCE_M:
        raise ValidationError(TOO_FAR_MESSAGE)


def require_text(value: str | None, label: str, max_length: int) -> str:
    """Exige un texto no vacío (sin espacios a los lados) de hasta `max_length` caracteres."""
    value = (value or "").strip()
    if not value:
        raise ValidationError(f"Falta {label}.")
    if len(value) > max_length:
        raise ValidationError(f"{label[0].upper()}{label[1:]} es demasiado largo.")
    return value
