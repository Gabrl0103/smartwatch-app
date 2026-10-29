from dataclasses import dataclass
from enum import Enum


class Maneuver(str, Enum):
    LEFT = "left"
    RIGHT = "right"
    STRAIGHT = "straight"
    ARRIVE = "arrive"


class RouteStatus(str, Enum):
    ACTIVE = "active"
    COMPLETED = "completed"
    CANCELLED = "cancelled"


@dataclass(frozen=True)
class Coordinates:
    lat: float
    lng: float


@dataclass(frozen=True)
class Place:
    name: str
    coordinates: Coordinates


@dataclass(frozen=True)
class RouteStep:
    order: int
    maneuver: Maneuver
    instruction: str
    distance_to_next_m: float
    point: Coordinates
    # Calle por la que se sigue después del paso; vacía si el proveedor no la da.
    street: str = ""


@dataclass(frozen=True)
class Route:
    origin: Coordinates
    destination: Coordinates
    destination_name: str
    distance_m: float
    duration_s: float
    steps: list[RouteStep]
    polyline: list[Coordinates]
