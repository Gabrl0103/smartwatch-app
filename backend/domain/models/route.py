from dataclasses import dataclass
from enum import Enum


class Maneuver(str, Enum):
    LEFT = "left"
    RIGHT = "right"
    SLIGHT_LEFT = "slight_left"
    SLIGHT_RIGHT = "slight_right"
    SHARP_LEFT = "sharp_left"
    SHARP_RIGHT = "sharp_right"
    U_TURN_LEFT = "u_turn_left"
    U_TURN_RIGHT = "u_turn_right"
    ROUNDABOUT = "roundabout"
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
    # Número de salida al entrar a una rotonda, si el proveedor lo da.
    roundabout_exit: int | None = None
    # Giro unido a este por estar a pocos metros: se avisan juntos ("Luego, gira a la izquierda").
    then_maneuver: Maneuver | None = None


@dataclass(frozen=True)
class Route:
    origin: Coordinates
    destination: Coordinates
    destination_name: str
    distance_m: float
    duration_s: float
    steps: list[RouteStep]
    polyline: list[Coordinates]
