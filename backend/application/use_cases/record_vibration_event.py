from application.validation import require_text
from domain.errors import NotFoundError
from domain.ports.route_repository import RouteRepositoryPort
from domain.ports.vibration_event_repository import VibrationEventRepositoryPort

MAX_PATTERN_LENGTH = 100


def record_vibration_event(
    events: VibrationEventRepositoryPort,
    routes: RouteRepositoryPort,
    pattern: str | None,
    route_id: int | None = None,
    step_order: int | None = None,
) -> int:
    """Anota una vibración emitida por el reloj.

    Si llegan route_id y step_order se enlaza a ese paso (404 si no existe); si falta
    alguno, se guarda sin paso (p. ej. vibraciones de "sin señal" antes de tener ruta).
    """
    pattern = require_text(pattern, "el patrón de vibración", MAX_PATTERN_LENGTH)
    step_id = None
    if route_id is not None and step_order is not None:
        step_id = routes.find_step_id(route_id, step_order)
        if step_id is None:
            raise NotFoundError("Ese paso de la ruta no existe.")
    return events.save(pattern, step_id)
