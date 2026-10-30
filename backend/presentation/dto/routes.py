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
    street: str = ""
    roundabout_exit: int | None = None
    then_maneuver: str | None = None
    destination_side: str = ""


class RouteResponse(BaseModel):
    # None si no se pudo guardar la ruta; el reloj navega igual.
    route_id: int | None = None
    destination_name: str
    distance_m: float
    duration_s: float
    steps: list[RouteStepResponse]
    polyline: list[list[float]]

    @classmethod
    def from_domain(cls, route: Route, route_id: int | None = None) -> "RouteResponse":
        return cls(
            route_id=route_id,
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
                    street=step.street,
                    roundabout_exit=step.roundabout_exit,
                    then_maneuver=step.then_maneuver.value if step.then_maneuver else None,
                    destination_side=step.destination_side,
                )
                for step in route.steps
            ],
            polyline=[[point.lat, point.lng] for point in route.polyline],
        )
