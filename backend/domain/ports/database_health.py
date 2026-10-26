from typing import Protocol


class DatabaseHealthPort(Protocol):
    def is_available(self) -> bool: ...
