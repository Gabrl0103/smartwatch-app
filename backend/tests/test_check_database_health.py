import pytest

from application.use_cases.check_database_health import check_database_health
from domain.errors import ExternalServiceError


class FakeDatabase:
    def __init__(self, available: bool):
        self.available = available

    def is_available(self) -> bool:
        return self.available


def test_does_nothing_when_database_is_available():
    check_database_health(FakeDatabase(True))


def test_raises_external_service_error_when_unavailable():
    with pytest.raises(ExternalServiceError):
        check_database_health(FakeDatabase(False))
