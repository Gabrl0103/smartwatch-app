import pytest

from application.use_cases.record_vibration_event import record_vibration_event
from domain.errors import NotFoundError, ValidationError


class FakeEvents:
    def __init__(self):
        self.saved = []

    def save(self, pattern, route_step_id):
        self.saved.append((pattern, route_step_id))
        return len(self.saved)


class FakeRoutes:
    # Ruta 7 con pasos 1 y 2 (ids 70 y 71).
    steps = {(7, 1): 70, (7, 2): 71}

    def find_step_id(self, route_id, step_order):
        return self.steps.get((route_id, step_order))


def test_links_event_to_route_step():
    events = FakeEvents()

    event_id = record_vibration_event(events, FakeRoutes(), " 2 pulsos cortos ", 7, 2)

    assert event_id == 1
    assert events.saved == [("2 pulsos cortos", 71)]


def test_without_route_saves_event_without_step():
    events = FakeEvents()
    record_vibration_event(events, FakeRoutes(), "1 pulso cada 5 s")
    record_vibration_event(events, FakeRoutes(), "5 pulsos crecientes", route_id=7)
    assert events.saved == [("1 pulso cada 5 s", None), ("5 pulsos crecientes", None)]


def test_unknown_step_raises_not_found():
    events = FakeEvents()
    with pytest.raises(NotFoundError):
        record_vibration_event(events, FakeRoutes(), "2 pulsos cortos", 7, 9)
    assert events.saved == []


def test_empty_pattern_fails():
    with pytest.raises(ValidationError):
        record_vibration_event(FakeEvents(), FakeRoutes(), "  ")
