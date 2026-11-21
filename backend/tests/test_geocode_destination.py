import pytest

from application.place_kinds import place_kind_of
from application.use_cases.geocode_destination import NEAREST_RADIUS_M, geocode_destination
from application.validation import MAX_WALKING_DISTANCE_M
from domain.errors import NotFoundError, ValidationError
from domain.models.place_kind import PlaceKind
from domain.models.route import Coordinates, Place

HERE = Coordinates(1.215, -77.278)


class FakeGeocoder:
    def __init__(self, result):
        self.result = result
        self.calls = []

    def search(self, query, near=None, within_m=None):
        self.calls.append(("search", query, within_m))
        return self.result

    def nearest(self, kind, near, within_m):
        self.calls.append(("nearest", kind, within_m))
        return self.result


def test_returns_place_when_found():
    place = Place("Parque Bolívar", Coordinates(1.0, 2.0))
    assert geocode_destination(FakeGeocoder(place), "parque bolívar") == place


def test_a_named_place_is_only_searched_within_walking_distance():
    geocoder = FakeGeocoder(Place("Parque Bolívar", HERE))

    geocode_destination(geocoder, "Parque Bolívar", HERE)

    assert geocoder.calls == [("search", "Parque Bolívar", MAX_WALKING_DISTANCE_M)]


def test_a_kind_of_place_gives_the_nearest_one():
    geocoder = FakeGeocoder(Place("Droguería La Economía", HERE))

    place = geocode_destination(geocoder, "droguería más cercana", HERE)

    assert place.name == "Droguería La Economía"
    assert geocoder.calls == [("nearest", PlaceKind.PHARMACY, NEAREST_RADIUS_M)]


def test_no_place_of_that_kind_nearby_says_so():
    with pytest.raises(NotFoundError, match="No encontré una droguería cerca"):
        geocode_destination(FakeGeocoder(None), "una farmacia", HERE)


@pytest.mark.parametrize(
    "query, kind",
    [
        ("droguería más cercana", PlaceKind.PHARMACY),
        ("Farmacia", PlaceKind.PHARMACY),
        ("un cajero automático por aquí", PlaceKind.ATM),
        ("el baño más cercano", PlaceKind.TOILET),
        ("parada de bus", PlaceKind.BUS_STOP),
        ("Parque Bolívar", None),
        ("Banco de Bogotá", None),
        ("Plazoleta de comidas Unicentro", None),
        ("Droguería Alemana", None),
        ("Drogería", PlaceKind.PHARMACY),
    ],
)
def test_kinds_are_recognized_only_without_a_name(query, kind):
    assert place_kind_of(query) == kind


def test_empty_query_raises_validation_error():
    with pytest.raises(ValidationError):
        geocode_destination(FakeGeocoder(None), "   ")


def test_not_found_raises_not_found_error():
    with pytest.raises(NotFoundError):
        geocode_destination(FakeGeocoder(None), "lugar inexistente")
