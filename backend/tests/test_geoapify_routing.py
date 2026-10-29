import httpx
import pytest

from domain.errors import ExternalServiceError, NotFoundError
from domain.models.route import Coordinates, Maneuver
from infrastructure.routing.geoapify_routing import GeoapifyRouting, to_maneuver

ORIGIN = Coordinates(1.2142, -77.2785)
DESTINATION = Coordinates(1.2006, -77.2786)


def make_routing(handler, key="k"):
    client = httpx.Client(transport=httpx.MockTransport(handler))
    return GeoapifyRouting(key, client)


def step(type_, text, distance, from_index, streets=None):
    instruction = {"type": type_, "text": text}
    if streets is not None:
        instruction["streets"] = streets
    return {"instruction": instruction, "distance": distance, "from_index": from_index}


def route_reply():
    """Respuesta mínima de Geoapify: GeoJSON en [lon, lat], un tramo con 3 pasos."""
    steps = [
        step("StartAtLeft", "Camine hacia el sur.", 100, 0),
        step("Right", "Gire a la derecha hacia Carrera 25.", 50, 1, ["Carrera 25"]),
        step("DestinationReached", "Ha llegado.", 0, 2),
    ]
    feature = {
        "geometry": {
            "type": "MultiLineString",
            "coordinates": [[[-77.2785, 1.2142], [-77.2786, 1.2100], [-77.2786, 1.2006]]],
        },
        "properties": {"distance": 150, "time": 140.5, "legs": [{"steps": steps}]},
    }
    return {"type": "FeatureCollection", "features": [feature]}


def test_route_is_parsed_and_params_are_sent():
    seen = {}

    def handler(request):
        seen["params"] = dict(request.url.params)
        return httpx.Response(200, json=route_reply())

    route = make_routing(handler).get_walking_route(ORIGIN, DESTINATION)

    assert seen["params"]["waypoints"] == "1.2142,-77.2785|1.2006,-77.2786"
    assert seen["params"]["mode"] == "walk"
    assert seen["params"]["type"] == "less_maneuvers"
    assert seen["params"]["lang"] == "es"
    assert seen["params"]["details"] == "instruction_details"
    assert seen["params"]["apiKey"] == "k"
    assert route.distance_m == 150
    assert route.duration_s == 140.5
    assert [s.maneuver for s in route.steps] == [Maneuver.STRAIGHT, Maneuver.RIGHT, Maneuver.ARRIVE]
    assert [s.order for s in route.steps] == [1, 2, 3]
    assert route.steps[1].instruction == "Gire a la derecha hacia Carrera 25."
    assert [s.street for s in route.steps] == ["", "Carrera 25", ""]
    assert route.steps[1].distance_to_next_m == 50
    assert route.steps[1].point == Coordinates(1.2100, -77.2786)
    assert route.polyline == [ORIGIN, Coordinates(1.2100, -77.2786), DESTINATION]


def test_to_maneuver_maps_geoapify_types():
    assert to_maneuver("Left") == Maneuver.LEFT
    assert to_maneuver("SlightLeft") == Maneuver.LEFT
    assert to_maneuver("StayRight") == Maneuver.RIGHT
    assert to_maneuver("SharpRight") == Maneuver.RIGHT
    assert to_maneuver("Continue") == Maneuver.STRAIGHT
    assert to_maneuver("StartAtRight") == Maneuver.STRAIGHT
    assert to_maneuver("DestinationLeft") == Maneuver.ARRIVE
    assert to_maneuver("DestinationReached") == Maneuver.ARRIVE


def test_no_features_raises_not_found_error():
    routing = make_routing(lambda r: httpx.Response(200, json={"features": []}))
    with pytest.raises(NotFoundError):
        routing.get_walking_route(ORIGIN, DESTINATION)


def test_http_error_raises_external_service_error():
    routing = make_routing(lambda r: httpx.Response(400, json={"message": "Bad Request"}))
    with pytest.raises(ExternalServiceError):
        routing.get_walking_route(ORIGIN, DESTINATION)


def test_connection_error_raises_external_service_error():
    def handler(request):
        raise httpx.ConnectError("sin red")

    with pytest.raises(ExternalServiceError):
        make_routing(handler).get_walking_route(ORIGIN, DESTINATION)


def test_malformed_feature_raises_external_service_error():
    reply = {"features": [{"geometry": {"type": "MultiLineString", "coordinates": []}}]}
    routing = make_routing(lambda r: httpx.Response(200, json=reply))
    with pytest.raises(ExternalServiceError):
        routing.get_walking_route(ORIGIN, DESTINATION)


def test_route_type_can_be_changed():
    seen = {}

    def handler(request):
        seen["type"] = request.url.params["type"]
        return httpx.Response(200, json=route_reply())

    client = httpx.Client(transport=httpx.MockTransport(handler))
    GeoapifyRouting("k", client, route_type="short").get_walking_route(ORIGIN, DESTINATION)
    assert seen["type"] == "short"
