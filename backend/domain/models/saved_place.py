from dataclasses import dataclass
from datetime import datetime

from domain.models.route import Coordinates


@dataclass(frozen=True)
class SavedPlace:
    id: int
    name: str
    coordinates: Coordinates
    created_at: datetime
