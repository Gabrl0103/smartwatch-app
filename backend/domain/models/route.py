from dataclasses import dataclass
from enum import Enum


class Maneuver(str, Enum):
    LEFT = "left"
    RIGHT = "right"
    STRAIGHT = "straight"
    ARRIVE = "arrive"


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


@dataclass(frozen=True)
class Route:
    origin: Coordinates
    destination: Coordinates
    destination_name: str
    distance_m: float
    duration_s: float
    steps: list[RouteStep]
    polyline: list[Coordinates]
