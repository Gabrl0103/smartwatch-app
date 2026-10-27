import httpx

from domain.errors import ExternalServiceError, NotFoundError
from domain.models.route import Coordinates, Maneuver, Route, RouteStep

ROUTING_URL = "https://api.geoapify.com/v1/routing"


def to_maneuver(instruction_type: str) -> Maneuver:
    """Reduce los tipos de Geoapify (Right, SlightLeft, StayRight...) a 4 maniobras."""
    if instruction_type.startswith("Destination"):
        return Maneuver.ARRIVE
    if instruction_type.startswith("Start"):
        return Maneuver.STRAIGHT
    if "Right" in instruction_type:
        return Maneuver.RIGHT
    if "Left" in instruction_type:
        return Maneuver.LEFT
    return Maneuver.STRAIGHT


class GeoapifyRouting:
    def __init__(self, api_key: str, client: httpx.Client | None = None):
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=15)

    def get_walking_route(self, origin: Coordinates, destination: Coordinates) -> Route:
        params = {
            "waypoints": f"{origin.lat},{origin.lng}|{destination.lat},{destination.lng}",
            "mode": "walk",
            "lang": "es",
            "details": "instruction_details",
            "apiKey": self._api_key,
        }
        try:
            response = self._client.get(ROUTING_URL, params=params)
        except httpx.HTTPError as error:
            raise ExternalServiceError("No pude conectar con Geoapify.") from error
        if response.status_code != 200:
            raise ExternalServiceError(
                f"Geoapify respondió con error {response.status_code}."
            )
        try:
            features = response.json()["features"]
        except (KeyError, TypeError, ValueError) as error:
            raise ExternalServiceError("Geoapify devolvió una respuesta inesperada.") from error
        if not features:
            raise NotFoundError("No encontré una ruta a pie hasta ese destino.")
        try:
            return self._parse_route(features[0], origin, destination)
        except (KeyError, IndexError, TypeError, ValueError) as error:
            raise ExternalServiceError("Geoapify devolvió una respuesta inesperada.") from error

    @staticmethod
    def _parse_route(feature: dict, origin: Coordinates, destination: Coordinates) -> Route:
        properties = feature["properties"]
        geometry = feature["geometry"]
        # GeoJSON viene en [lon, lat]; MultiLineString trae una línea por tramo (leg).
        lines = geometry["coordinates"]
        if geometry["type"] == "LineString":
            lines = [lines]
        lines = [[Coordinates(lat=lat, lng=lon) for lon, lat in line] for line in lines]

        steps = []
        for leg, line in zip(properties["legs"], lines):
            for step in leg["steps"]:
                instruction = step["instruction"]
                steps.append(
                    RouteStep(
                        order=len(steps) + 1,
                        maneuver=to_maneuver(instruction.get("type", "")),
                        instruction=instruction["text"],
                        distance_to_next_m=float(step["distance"]),
                        point=line[step["from_index"]],
                    )
                )

        return Route(
            origin=origin,
            destination=destination,
            destination_name="",
            distance_m=float(properties["distance"]),
            duration_s=float(properties["time"]),
            steps=steps,
            polyline=[point for line in lines for point in line],
        )
