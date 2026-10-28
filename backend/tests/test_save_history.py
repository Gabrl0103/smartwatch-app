import logging

from application.use_cases.save_history import save_active_route, save_voice_command
from domain.errors import ExternalServiceError
from domain.models.route import Coordinates, Route, RouteStatus
from domain.models.user import DEFAULT_USER_ID
from domain.models.voice import Intent, VoiceCommand

POINT = Coordinates(1.2, -77.2)
ROUTE = Route(POINT, POINT, "Parque", 0, 0, [], [POINT])
COMMAND = VoiceCommand("al parque", Intent.NAVIGATE, destination="parque")


class FakeRepository:
    def __init__(self, error=None):
        self.error = error
        self.saved = []

    def save(self, *args):
        if self.error:
            raise self.error
        self.saved.append(args)
        return 5


def test_saves_route_as_active_for_default_user():
    repository = FakeRepository()
    assert save_active_route(lambda: repository, ROUTE) == 5
    assert repository.saved == [(DEFAULT_USER_ID, ROUTE, RouteStatus.ACTIVE)]


def test_route_save_failure_is_logged_and_returns_none(caplog):
    repository = FakeRepository(RuntimeError("conexión perdida"))
    with caplog.at_level(logging.ERROR):
        assert save_active_route(lambda: repository, ROUTE) is None
    assert "No se pudo guardar la ruta" in caplog.text


def test_failure_creating_repository_returns_none():
    def missing_database():
        raise ExternalServiceError("Falta configurar DATABASE_URL.")

    assert save_voice_command(missing_database, COMMAND) is None


def test_saves_voice_command_for_default_user():
    repository = FakeRepository()
    assert save_voice_command(lambda: repository, COMMAND) == 5
    assert repository.saved == [(DEFAULT_USER_ID, COMMAND)]
