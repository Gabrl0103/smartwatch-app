from datetime import datetime

import pytest
from sqlalchemy import create_engine, insert, select
from sqlalchemy.pool import StaticPool

from domain.models.device import WEAR_OS
from domain.models.route import Coordinates, Maneuver, Route, RouteStatus, RouteStep
from domain.models.voice import Intent, VoiceCommand
from infrastructure.database.sql_device_repository import SqlDeviceRepository
from infrastructure.database.sql_route_repository import SqlRouteRepository
from infrastructure.database.sql_saved_place_repository import SqlSavedPlaceRepository
from infrastructure.database.sql_vibration_event_repository import SqlVibrationEventRepository
from infrastructure.database.sql_voice_command_repository import SqlVoiceCommandRepository
from infrastructure.database.tables import (
    metadata,
    route_steps,
    routes,
    users,
    vibration_events,
    voice_commands,
)

USER_ID = 1
OTHER_USER_ID = 2
PARK = Coordinates(1.2006, -77.2786)
CENTER = Coordinates(1.2136, -77.2811)


@pytest.fixture
def engine():
    engine = create_engine("sqlite://", poolclass=StaticPool)
    metadata.create_all(engine)
    with engine.begin() as connection:
        connection.execute(insert(users), [{"id": USER_ID, "name": "Usuario"}, {"id": OTHER_USER_ID, "name": "Otro"}])
    return engine


def rows(engine, table):
    with engine.connect() as connection:
        return connection.execute(select(table).order_by(table.c.id)).all()


def sample_route():
    steps = [
        RouteStep(1, Maneuver.RIGHT, "Gire a la derecha.", 120, CENTER),
        RouteStep(2, Maneuver.ARRIVE, "Ha llegado.", 0, PARK),
    ]
    return Route(CENTER, PARK, "Parque Bolívar", 120, 90, steps, [CENTER, PARK])


def test_saved_places_add_and_list_by_name(engine):
    repository = SqlSavedPlaceRepository(engine)
    repository.add(USER_ID, "Parque", PARK)
    home = repository.add(USER_ID, "Casa", CENTER)
    repository.add(OTHER_USER_ID, "Ajeno", PARK)

    places = repository.list(USER_ID)

    assert [place.name for place in places] == ["Casa", "Parque"]
    assert places[0] == home
    assert home.coordinates == CENTER
    assert isinstance(home.created_at, datetime)


def test_saved_places_delete_only_own_place(engine):
    repository = SqlSavedPlaceRepository(engine)
    mine = repository.add(USER_ID, "Casa", CENTER)
    other = repository.add(OTHER_USER_ID, "Ajeno", PARK)

    assert repository.delete(USER_ID, other.id) is False
    assert repository.delete(USER_ID, mine.id) is True
    assert repository.delete(USER_ID, mine.id) is False
    assert repository.list(USER_ID) == []
    assert len(repository.list(OTHER_USER_ID)) == 1


def test_route_saved_with_steps(engine):
    repository = SqlRouteRepository(engine)

    route_id = repository.save(USER_ID, sample_route(), RouteStatus.ACTIVE)

    saved = rows(engine, routes)[0]
    assert saved.id == route_id
    assert (saved.user_id, saved.destination_name, saved.status) == (USER_ID, "Parque Bolívar", "active")
    assert (saved.origin_lat, saved.destination_lng) == (CENTER.lat, PARK.lng)
    steps = rows(engine, route_steps)
    assert [(s.route_id, s.step_order, s.maneuver, s.distance_to_next_m) for s in steps] == [
        (route_id, 1, "right", 120),
        (route_id, 2, "arrive", 0),
    ]


def test_find_step_id_by_route_and_order(engine):
    repository = SqlRouteRepository(engine)
    route_id = repository.save(USER_ID, sample_route(), RouteStatus.ACTIVE)
    second_step = rows(engine, route_steps)[1]

    assert repository.find_step_id(route_id, 2) == second_step.id
    assert repository.find_step_id(route_id, 9) is None
    assert repository.find_step_id(route_id + 1, 1) is None


def test_voice_command_saved(engine):
    command = VoiceCommand("llévame al parque", Intent.NAVIGATE, destination="parque")

    command_id = SqlVoiceCommandRepository(engine).save(USER_ID, command)

    saved = rows(engine, voice_commands)[0]
    assert saved.id == command_id
    assert (saved.transcribed_text, saved.intent, saved.extracted_destination) == (
        "llévame al parque",
        "navigate",
        "parque",
    )


def test_vibration_event_saved_with_and_without_step(engine):
    route_id = SqlRouteRepository(engine).save(USER_ID, sample_route(), RouteStatus.ACTIVE)
    step_id = SqlRouteRepository(engine).find_step_id(route_id, 1)
    repository = SqlVibrationEventRepository(engine)

    repository.save("2 pulsos cortos", step_id)
    repository.save("1 pulso cada 5 s", None)

    saved = rows(engine, vibration_events)
    assert [(e.pattern, e.route_step_id) for e in saved] == [("2 pulsos cortos", step_id), ("1 pulso cada 5 s", None)]
    assert all(isinstance(e.emitted_at, datetime) for e in saved)


def test_device_register_is_idempotent(engine):
    repository = SqlDeviceRepository(engine)

    first = repository.register(USER_ID, "reloj-123", WEAR_OS)
    second = repository.register(USER_ID, "reloj-123", WEAR_OS)

    assert first == second
    assert (first.id, first.type) == ("reloj-123", "wear_os")
    assert isinstance(first.registered_at, datetime)
