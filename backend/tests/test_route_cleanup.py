from dataclasses import replace

import pytest

from application.route_cleanup import (
    WALK_SPEED_MPS,
    drop_redundant_straight_steps,
    merge_short_steps,
    tidy_route,
    walking_speed_mps,
)
from application.validation import distance_m
from domain.models.route import Coordinates, Maneuver, Route, RouteStep

START = Coordinates(1.214628, -77.278301)
END = Coordinates(1.205584, -77.270279)


def step(maneuver, instruction, distance, street="", point=START):
    return RouteStep(0, maneuver, instruction, distance, point, street)


def route(steps, distance=1649.0, duration=1553.5):
    return Route(START, END, "", distance, duration, steps, [START, END])


def instructions(steps):
    return [(s.instruction, s.distance_to_next_m) for s in steps]


ARRIVE = step(Maneuver.ARRIVE, "Su destino está a la derecha.", 0)


def test_short_start_before_a_turn_is_dropped():
    steps = merge_short_steps([
        step(Maneuver.STRAIGHT, "Camine hacia el noroeste por Pasaje Corazón de Jesús.", 4, "Pasaje Corazón de Jesús"),
        step(Maneuver.RIGHT, "Gire a la derecha hacia Carrera 25.", 45, "Carrera 25"),
        ARRIVE,
    ])
    assert instructions(steps) == [("Gire a la derecha hacia Carrera 25.", 45), (ARRIVE.instruction, 0)]


def test_two_turns_closer_than_ten_meters_are_announced_together():
    steps = merge_short_steps([
        step(Maneuver.STRAIGHT, "Camine hacia el sur.", 100),
        step(Maneuver.RIGHT, "Gire a la derecha hacia Calle 21A.", 6, "Calle 21A"),
        step(Maneuver.LEFT, "Gire a la izquierda hacia Carrera 17B.", 80, "Carrera 17B"),
        ARRIVE,
    ])
    assert instructions(steps) == [
        ("Camine hacia el sur.", 100),
        ("Gire a la derecha hacia Calle 21A y luego gire a la izquierda hacia Carrera 17B.", 86),
        (ARRIVE.instruction, 0),
    ]
    assert steps[1].maneuver == Maneuver.RIGHT
    assert steps[1].street == "Carrera 17B"
    assert steps[1].then_maneuver == Maneuver.LEFT


def test_the_arrival_keeps_its_side_when_a_short_step_joins_it():
    arrive = replace(ARRIVE, destination_side="left")
    steps = merge_short_steps([
        step(Maneuver.STRAIGHT, "Camine hacia el sur.", 100),
        step(Maneuver.RIGHT, "Gire a la derecha.", 6),
        arrive,
    ])
    assert steps[-1].destination_side == "left"


def test_turn_followed_by_a_short_continue_has_no_second_turn():
    steps = merge_short_steps([
        step(Maneuver.LEFT, "Gire a la izquierda.", 8, "Carrera 25"),
        step(Maneuver.STRAIGHT, "Continúe.", 50, "Carrera 25"),
        ARRIVE,
    ])
    assert steps[0].then_maneuver is None


def test_turn_followed_by_a_short_continue_keeps_the_turn():
    steps = merge_short_steps([
        step(Maneuver.STRAIGHT, "Camine hacia el sur.", 100, "Calle 18"),
        step(Maneuver.LEFT, "Gire a la izquierda.", 8, "Carrera 25"),
        step(Maneuver.STRAIGHT, "Continúe por Avenida Colombia.", 60, "Avenida Colombia"),
        ARRIVE,
    ])
    assert instructions(steps) == [
        ("Camine hacia el sur.", 100),
        ("Gire a la izquierda.", 68),
        (ARRIVE.instruction, 0),
    ]
    assert steps[1].street == "Avenida Colombia"


def test_short_straight_in_the_middle_gives_its_meters_to_the_previous_step():
    steps = merge_short_steps([
        step(Maneuver.LEFT, "Gire a la izquierda.", 50),
        step(Maneuver.STRAIGHT, "Continúe.", 5),
        step(Maneuver.RIGHT, "Gire a la derecha.", 40),
        ARRIVE,
    ])
    assert instructions(steps) == [
        ("Gire a la izquierda.", 55),
        ("Gire a la derecha.", 40),
        (ARRIVE.instruction, 0),
    ]


def test_turn_right_before_arriving_is_not_announced():
    steps = merge_short_steps([step(Maneuver.STRAIGHT, "Camine.", 200), step(Maneuver.RIGHT, "Gire.", 7), ARRIVE])
    assert instructions(steps) == [("Camine.", 207), (ARRIVE.instruction, 0)]


def test_several_short_steps_in_a_row_end_in_one():
    steps = merge_short_steps([
        step(Maneuver.STRAIGHT, "Camine.", 3),
        step(Maneuver.STRAIGHT, "Continúe.", 4),
        step(Maneuver.LEFT, "Gire a la izquierda.", 120),
        ARRIVE,
    ])
    assert instructions(steps) == [("Gire a la izquierda.", 120), (ARRIVE.instruction, 0)]
    assert all(s.distance_to_next_m >= 10 for s in steps[:-1])


def test_short_straight_on_the_same_street_is_removed():
    steps = drop_redundant_straight_steps([
        step(Maneuver.STRAIGHT, "Camine por Calle 19.", 300, "Calle 19"),
        step(Maneuver.STRAIGHT, "Continúe por Calle 19.", 25, "Calle 19"),
        step(Maneuver.STRAIGHT, "Continúe.", 20),
        step(Maneuver.RIGHT, "Gire a la derecha.", 100, "Avenida Bolívar"),
        ARRIVE,
    ])
    assert instructions(steps) == [
        ("Camine por Calle 19.", 345),
        ("Gire a la derecha.", 100),
        (ARRIVE.instruction, 0),
    ]


def test_straight_steps_that_change_street_or_are_long_stay():
    steps = [
        step(Maneuver.STRAIGHT, "Camine por Calle 19.", 300, "Calle 19"),
        step(Maneuver.STRAIGHT, "Continúe por Avenida Colombia.", 20, "Avenida Colombia"),
        step(Maneuver.STRAIGHT, "Continúe por Avenida Colombia.", 30, "Avenida Colombia"),
        ARRIVE,
    ]
    assert drop_redundant_straight_steps(steps) == steps


def test_origin_far_from_the_route_adds_a_first_step_to_the_street():
    far_origin = Coordinates(START.lat + 0.0007, START.lng)  # ~78 m al norte
    steps = [step(Maneuver.STRAIGHT, "Camine hacia el sur por Carrera 25.", 300, "Carrera 25"), ARRIVE]

    tidy = tidy_route(route(steps, distance=300, duration=240), far_origin)

    gap = distance_m(far_origin, START)
    assert gap > 50
    assert tidy.steps[0].instruction == "Dirígete a Carrera 25."
    assert tidy.steps[0].maneuver == Maneuver.STRAIGHT
    assert tidy.steps[0].point == far_origin
    assert tidy.steps[0].distance_to_next_m == pytest.approx(gap)
    assert [s.order for s in tidy.steps] == [1, 2, 3]
    assert tidy.polyline[0] == far_origin
    assert tidy.distance_m == pytest.approx(300 + gap)


def test_origin_near_the_route_adds_nothing():
    near_origin = Coordinates(START.lat + 0.0003, START.lng)  # ~33 m
    steps = [step(Maneuver.STRAIGHT, "Camine.", 300, "Carrera 25"), ARRIVE]
    tidy = tidy_route(route(steps, distance=300, duration=240), near_origin)
    assert len(tidy.steps) == 2
    assert tidy.distance_m == 300


def test_first_step_without_street_name_still_says_where_to_go():
    far_origin = Coordinates(START.lat + 0.001, START.lng)
    tidy = tidy_route(route([step(Maneuver.STRAIGHT, "Camine.", 300), ARRIVE]), far_origin)
    assert tidy.steps[0].instruction == "Dirígete al inicio de la ruta."


def test_duration_uses_walking_speed_when_provider_is_far_from_it():
    # Geoapify da ~1,06 m/s en Pasto: más de un 10 % por debajo de 1,25 m/s.
    tidy = tidy_route(route([ARRIVE], distance=1649, duration=1553.5), START)
    assert tidy.duration_s == pytest.approx(1649 / WALK_SPEED_MPS)


def test_duration_keeps_provider_speed_when_close():
    assert walking_speed_mps(1000, 1000 / 1.2) == pytest.approx(1.2)
    tidy = tidy_route(route([ARRIVE], distance=1000, duration=800), START)
    assert tidy.duration_s == 800


def test_duration_without_provider_time_uses_walking_speed():
    assert walking_speed_mps(100, 0) == WALK_SPEED_MPS
