from application.validation import require_coordinates, require_text
from domain.errors import NotFoundError
from domain.models.route import Coordinates
from domain.models.saved_place import SavedPlace
from domain.models.user import DEFAULT_USER_ID
from domain.ports.saved_place_repository import SavedPlaceRepositoryPort

MAX_NAME_LENGTH = 100


def list_saved_places(repository: SavedPlaceRepositoryPort) -> list[SavedPlace]:
    return repository.list(DEFAULT_USER_ID)


def add_saved_place(
    repository: SavedPlaceRepositoryPort, name: str | None, coordinates: Coordinates | None
) -> SavedPlace:
    """Guarda un lugar para elegirlo después como destino sin hablar."""
    name = require_text(name, "el nombre del lugar", MAX_NAME_LENGTH)
    coordinates = require_coordinates(coordinates, "el lugar")
    return repository.add(DEFAULT_USER_ID, name, coordinates)


def delete_saved_place(repository: SavedPlaceRepositoryPort, place_id: int) -> None:
    if not repository.delete(DEFAULT_USER_ID, place_id):
        raise NotFoundError("Ese lugar guardado no existe.")
