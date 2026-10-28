from dataclasses import dataclass
from datetime import datetime

WEAR_OS = "wear_os"


@dataclass(frozen=True)
class Device:
    id: str
    type: str
    registered_at: datetime
