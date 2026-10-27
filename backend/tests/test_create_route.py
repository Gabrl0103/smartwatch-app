import pytest

from application.use_cases.create_route import create_route
from domain.errors import NotFoundError, ValidationError
from domain.models.route import Coordinates, Route

ORIGIN = Coordinates(1.2142, -77.2785)
DESTINATION = Coordinates(1.2006, -77.2786)


class FakeRouting:
    def __init__(self, error=None):
        self.error = error
        self.calls = []

    def get_walking_route(self, origin, destination):
        self.calls.append((origin, destination))
        if self.error:
            raise self.error
        return Route(origin, destination, "", 150, 140, [], [origin, destination])


def test_returns_route_with_destination_name():
    routing = FakeRouting()
    route = create_route(routing, ORIGIN, DESTINATION, "  Parque Bolívar ")
    assert route.destination_name == "Parque Bolívar"
    assert route.distance_m == 150
    assert routing.calls == [(ORIGIN, DESTINATION)]


def test_missing_origin_raises_validation_error():
    with pytest.raises(ValidationError):
        create_route(FakeRouting(), None, DESTINATION)


def test_missing_latitude_raises_validation_error():
    with pytest.raises(ValidationError):
        create_route(FakeRouting(), ORIGIN, Coordinates(None, -77.2))


def test_latitude_out_of_range_raises_validation_error():
    with pytest.raises(ValidationError):
        create_route(FakeRouting(), Coordinates(91, 0), DESTINATION)


def test_longitude_out_of_range_raises_validation_error():
    with pytest.raises(ValidationError):
        create_route(FakeRouting(), ORIGIN, Coordinates(0, -181))


def test_invalid_coordinates_do_not_call_provider():
    routing = FakeRouting()
    with pytest.raises(ValidationError):
        create_route(routing, Coordinates(100, 0), DESTINATION)
    assert routing.calls == []


def test_provider_errors_propagate():
    with pytest.raises(NotFoundError):
        create_route(FakeRouting(NotFoundError("sin ruta")), ORIGIN, DESTINATION)
