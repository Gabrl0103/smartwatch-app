"""Ajusta la ruta del proveedor para guiar a pie con vibraciones: pasos que se puedan avisar
uno por uno, un primer paso si el origen está lejos de la calle y una duración realista."""

from dataclasses import replace

from application.validation import distance_m
from domain.models.route import Coordinates, Maneuver, Route, RouteStep

# Dos pasos a menos de esto no dan tiempo a avisar uno y luego el otro: se unen.
MIN_STEP_M = 10.0
# Un "siga recto" por la misma calle y más corto que esto solo agrega un aviso sin información.
MIN_STRAIGHT_STEP_M = 30.0
# Si el origen queda más lejos que esto del inicio de la ruta, primero hay que llegar a la calle.
MAX_ORIGIN_GAP_M = 50.0
# Velocidad de caminata de referencia (4,5 km/h). Si la del proveedor se aleja más de
# SPEED_TOLERANCE de ella, la duración se recalcula con esta.
WALK_SPEED_MPS = 1.25
SPEED_TOLERANCE = 0.10


def tidy_route(route: Route, origin: Coordinates) -> Route:
    """Une pasos cortos, quita "rectos" sin cambio de calle, agrega "Dirígete a <calle>" si el
    origen está lejos y calcula la duración a una velocidad de caminata creíble."""
    steps = drop_redundant_straight_steps(merge_short_steps(route.steps))
    polyline = list(route.polyline)
    distance = route.distance_m
    start = polyline[0] if polyline else (steps[0].point if steps else origin)
    gap = distance_m(origin, start)
    if gap > MAX_ORIGIN_GAP_M:
        steps = [approach_step(origin, gap, steps)] + steps
        polyline = [origin] + polyline
        distance += gap
    steps = [replace(step, order=i + 1) for i, step in enumerate(steps)]
    speed = walking_speed_mps(route.distance_m, route.duration_s)
    return replace(route, steps=steps, polyline=polyline, distance_m=distance, duration_s=distance / speed)


def walking_speed_mps(distance: float, duration: float) -> float:
    """La velocidad del proveedor si es creíble (±10 % de 1,25 m/s); si no, 1,25 m/s."""
    if duration <= 0:
        return WALK_SPEED_MPS
    provider = distance / duration
    if abs(provider - WALK_SPEED_MPS) <= SPEED_TOLERANCE * WALK_SPEED_MPS:
        return provider
    return WALK_SPEED_MPS


def merge_short_steps(steps: list[RouteStep]) -> list[RouteStep]:
    """Une cada paso de menos de MIN_STEP_M con el siguiente. No toca la llegada ni los pasos
    desconocidos: "Tome las escaleras." puede medir 5 m y es justo lo que no se puede perder."""
    out = list(steps)
    i = 0
    while i < len(out) - 1:
        unknown = Maneuver.UNKNOWN in (out[i].maneuver, out[i + 1].maneuver)
        if out[i].distance_to_next_m >= MIN_STEP_M or unknown:
            i += 1
            continue
        merged, carried_m = _merge(out[i], out[i + 1])
        out[i:i + 2] = [merged]
        if carried_m and i > 0:
            previous = out[i - 1]
            out[i - 1] = replace(previous, distance_to_next_m=previous.distance_to_next_m + carried_m)
    return out


def _merge(short: RouteStep, following: RouteStep) -> tuple[RouteStep, float]:
    """El paso que queda al unir `short` con `following`, y los metros que pasan al anterior."""
    total = short.distance_to_next_m + following.distance_to_next_m
    # Un "recto" corto, o un giro justo antes de llegar, no se avisa: queda el siguiente.
    if short.maneuver == Maneuver.STRAIGHT or following.maneuver == Maneuver.ARRIVE:
        return following, short.distance_to_next_m
    if following.maneuver == Maneuver.STRAIGHT:
        return replace(short, distance_to_next_m=total, street=following.street or short.street), 0.0
    # Dos giros seguidos: se avisan juntos donde empieza el primero.
    instruction = f"{short.instruction.rstrip('.')} y luego {_lower_first(following.instruction)}"
    return replace(
        short,
        instruction=instruction,
        distance_to_next_m=total,
        street=following.street or short.street,
        then_maneuver=short.then_maneuver or following.maneuver,
    ), 0.0


def drop_redundant_straight_steps(steps: list[RouteStep]) -> list[RouteStep]:
    """Quita los "recto" de menos de MIN_STRAIGHT_STEP_M que siguen por la misma calle. El primer
    paso y la llegada se quedan siempre."""
    if len(steps) < 3:
        return list(steps)
    out = [steps[0]]
    for step in steps[1:-1]:
        previous = out[-1]
        same_street = not step.street or step.street == previous.street
        if step.maneuver == Maneuver.STRAIGHT and step.distance_to_next_m < MIN_STRAIGHT_STEP_M and same_street:
            out[-1] = replace(previous, distance_to_next_m=previous.distance_to_next_m + step.distance_to_next_m)
        else:
            out.append(step)
    out.append(steps[-1])
    return out


def approach_step(origin: Coordinates, gap_m: float, steps: list[RouteStep]) -> RouteStep:
    """Primer paso cuando el origen está lejos de la ruta: ir hasta la calle donde empieza."""
    street = next((step.street for step in steps if step.street), "")
    instruction = f"Dirígete a {street}." if street else "Dirígete al inicio de la ruta."
    return RouteStep(
        order=0,
        maneuver=Maneuver.STRAIGHT,
        instruction=instruction,
        distance_to_next_m=gap_m,
        point=origin,
        street=street,
    )


def _lower_first(text: str) -> str:
    return text[:1].lower() + text[1:]
