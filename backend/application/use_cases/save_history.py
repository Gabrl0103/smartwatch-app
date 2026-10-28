"""Guarda el historial (rutas y comandos de voz) sin poner en riesgo la respuesta principal.

Reciben una función que entrega el repositorio, así también queda contenido un fallo al
crearlo (p. ej. falta DATABASE_URL). Si algo falla, se registra y se devuelve None.
"""

import logging
from collections.abc import Callable
from typing import TypeVar

from domain.models.route import Route, RouteStatus
from domain.models.user import DEFAULT_USER_ID
from domain.models.voice import VoiceCommand
from domain.ports.route_repository import RouteRepositoryPort
from domain.ports.voice_command_repository import VoiceCommandRepositoryPort

logger = logging.getLogger(__name__)

T = TypeVar("T")


def _best_effort(description: str, action: Callable[[], T]) -> T | None:
    try:
        return action()
    except Exception:
        logger.exception("No se pudo guardar %s", description)
        return None


def save_active_route(get_repository: Callable[[], RouteRepositoryPort], route: Route) -> int | None:
    """Guarda la ruta recién calculada como activa y devuelve su id (None si falló)."""
    return _best_effort(
        "la ruta", lambda: get_repository().save(DEFAULT_USER_ID, route, RouteStatus.ACTIVE)
    )


def save_voice_command(
    get_repository: Callable[[], VoiceCommandRepositoryPort], command: VoiceCommand
) -> int | None:
    return _best_effort(
        "el comando de voz", lambda: get_repository().save(DEFAULT_USER_ID, command)
    )
