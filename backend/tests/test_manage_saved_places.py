from datetime import datetime

import pytest

from application.use_cases.manage_saved_places import (
    add_saved_place,
    delete_saved_place,
    list_saved_places,
)
from domain.errors import NotFoundError, ValidationError
from domain.models.route import Coordinates
from domain.models.saved_place import SavedPlace
from domain.models.user import DEFAULT_USER_ID

PARK = Coordinates(1.2006, -77.2786)


class FakeSavedPlaces:
    def __init__(self):
        self.places: dict[int, tuple[int, SavedPlace]] = {}

    def list(self, user_id):
        return [place for owner, place in self.places.values() if owner == user_id]

    def add(self, user_id, name, coordinates):
        place = SavedPlace(len(self.places) + 1, name, coordinates, datetime(2026, 10, 28))
        self.places[place.id] = (user_id, place)
        return place

    def delete(self, user_id, place_id):
        if self.places.get(place_id, (None,))[0] != user_id:
            return False
        del self.places[place_id]
        return True


def test_add_trims_name_and_saves_for_default_user():
    repository = FakeSavedPlaces()

    place = add_saved_place(repository, "  Parque Bolívar ", PARK)

    assert place.name == "Parque Bolívar"
    assert place.coordinates == PARK
    assert list_saved_places(repository) == [place]
    assert repository.places[place.id][0] == DEFAULT_USER_ID


def test_add_without_name_fails():
    repository = FakeSavedPlaces()
    with pytest.raises(ValidationError):
        add_saved_place(repository, "   ", PARK)
    assert repository.places == {}


def test_add_with_too_long_name_fails():
    with pytest.raises(ValidationError):
        add_saved_place(FakeSavedPlaces(), "x" * 101, PARK)


def test_add_with_missing_or_invalid_coordinates_fails():
    with pytest.raises(ValidationError):
        add_saved_place(FakeSavedPlaces(), "Casa", Coordinates(None, -77.2))
    with pytest.raises(ValidationError):
        add_saved_place(FakeSavedPlaces(), "Casa", Coordinates(95, 0))


def test_delete_existing_place():
    repository = FakeSavedPlaces()
    place = add_saved_place(repository, "Casa", PARK)

    delete_saved_place(repository, place.id)

    assert list_saved_places(repository) == []


def test_delete_missing_place_raises_not_found():
    with pytest.raises(NotFoundError):
        delete_saved_place(FakeSavedPlaces(), 42)
