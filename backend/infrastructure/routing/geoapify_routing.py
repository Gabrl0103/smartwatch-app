import httpx

from domain.errors import ExternalServiceError, NotFoundError
from domain.models.route import Coordinates, Maneuver, Route, RouteStep

ROUTING_URL = "https://api.geoapify.com/v1/routing"
# Preferencia de ruta de Geoapify: "balanced", "short" o "less_maneuvers". Con las rutas de
# prueba en Pasto, less_maneuvers tiene la mitad de giros por calles más tranquilas a cambio de
# hasta un 9 % más de distancia (ver docs/REQUIREMENTS.md, "Rutas reales").
DEFAULT_ROUTE_TYPE = "less_maneuvers"

_TURNS_BY_SIDE = {
    "Right": (Maneuver.RIGHT, Maneuver.SLIGHT_RIGHT, Maneuver.SHARP_RIGHT, Maneuver.U_TURN_RIGHT),
    "Left": (Maneuver.LEFT, Maneuver.SLIGHT_LEFT, Maneuver.SHARP_LEFT, Maneuver.U_TURN_LEFT),
}


def to_maneuver(instruction_type: str) -> Maneuver:
    """Traduce los tipos de Geoapify (Right, SlightLeft, TurnAroundRight, Roundabout...) a
    maniobras. "Stay" es una bifurcación: se sigue de un lado sin un giro completo."""
    if instruction_type.startswith("Destination"):
        return Maneuver.ARRIVE
    if instruction_type.startswith("Start"):
        return Maneuver.STRAIGHT
    if instruction_type == "Roundabout":
        return Maneuver.ROUNDABOUT
    for side, (plain, slight, sharp, u_turn) in _TURNS_BY_SIDE.items():
        if side in instruction_type:
            if instruction_type.startswith(("Slight", "Stay")):
                return slight
            if instruction_type.startswith("Sharp"):
                return sharp
            if instruction_type.startswith("TurnAround"):
                return u_turn
            return plain
    return Maneuver.STRAIGHT


def destination_side(instruction_type: str) -> str:
    """De qué lado queda el destino según DestinationReachedRight/Left; "" si no se sabe."""
    if not instruction_type.startswith("Destination"):
        return ""
    if instruction_type.endswith("Right"):
        return "right"
    if instruction_type.endswith("Left"):
        return "left"
    return ""


class GeoapifyRouting:
    def __init__(
        self,
        api_key: str,
        client: httpx.Client | None = None,
        route_type: str = DEFAULT_ROUTE_TYPE,
    ):
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=15)
        self._route_type = route_type

    def get_walking_route(self, origin: Coordinates, destination: Coordinates) -> Route:
        params = {
            "waypoints": f"{origin.lat},{origin.lng}|{destination.lat},{destination.lng}",
            "mode": "walk",
            "type": self._route_type,
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
                streets = instruction.get("streets") or [""]
                instruction_type = instruction.get("type", "")
                maneuver = to_maneuver(instruction_type)
                # Una rotonda son dos pasos "Roundabout": entrar, con el número de salida, y salir.
                if maneuver == Maneuver.ROUNDABOUT and steps and steps[-1].maneuver == Maneuver.ROUNDABOUT:
                    maneuver = Maneuver.STRAIGHT
                steps.append(
                    RouteStep(
                        order=len(steps) + 1,
                        maneuver=maneuver,
                        instruction=instruction["text"],
                        distance_to_next_m=float(step["distance"]),
                        point=line[step["from_index"]],
                        street=streets[0],
                        roundabout_exit=instruction.get("roundabout_exit"),
                        destination_side=destination_side(instruction_type),
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
