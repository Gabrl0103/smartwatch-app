from typing import Protocol


class VibrationEventRepositoryPort(Protocol):
    def save(self, pattern: str, route_step_id: int | None) -> int: ...
