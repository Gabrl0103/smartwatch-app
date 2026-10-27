from pydantic import BaseModel

from domain.models.route import Coordinates, Route


class CoordinatesRequest(BaseModel):
    lat: float | None = None
    lng: float | None = None

    def to_domain(self) -> Coordinates:
        return Coordinates(lat=self.lat, lng=self.lng)


class RouteRequest(BaseModel):
    # Opcionales para que el caso de uso valide y responda con el formato de error común.
    origin: CoordinatesRequest | None = None
    destination: CoordinatesRequest | None = None
    destination_name: str = ""


class RouteStepResponse(BaseModel):
    order: int
    maneuver: str
    instruction: str
    distance_m: float
    lat: float
    lng: float


class RouteResponse(BaseModel):
    destination_name: str
    distance_m: float
    duration_s: float
    steps: list[RouteStepResponse]
    polyline: list[list[float]]

    @classmethod
    def from_domain(cls, route: Route) -> "RouteResponse":
        return cls(
            destination_name=route.destination_name,
            distance_m=route.distance_m,
            duration_s=route.duration_s,
            steps=[
                RouteStepResponse(
                    order=step.order,
                    maneuver=step.maneuver.value,
                    instruction=step.instruction,
                    distance_m=step.distance_to_next_m,
                    lat=step.point.lat,
                    lng=step.point.lng,
                )
                for step in route.steps
            ],
            polyline=[[point.lat, point.lng] for point in route.polyline],
        )
