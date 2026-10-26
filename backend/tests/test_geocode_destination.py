import pytest

from application.use_cases.geocode_destination import geocode_destination
from domain.errors import NotFoundError, ValidationError
from domain.models.route import Coordinates, Place


class FakeGeocoder:
    def __init__(self, result):
        self.result = result

    def search(self, query, near=None):
        return self.result


def test_returns_place_when_found():
    place = Place("Parque Principal", Coordinates(1.0, 2.0))
    assert geocode_destination(FakeGeocoder(place), "parque") == place


def test_empty_query_raises_validation_error():
    with pytest.raises(ValidationError):
        geocode_destination(FakeGeocoder(None), "   ")


def test_not_found_raises_not_found_error():
    with pytest.raises(NotFoundError):
        geocode_destination(FakeGeocoder(None), "lugar inexistente")
