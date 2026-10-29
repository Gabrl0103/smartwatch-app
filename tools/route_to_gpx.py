"""Genera un GPX de demostración a partir de una ruta real del backend de Rumbo.

Pide la ruta a pie a POST /routes, simula a una persona recorriéndola con el GPS de un reloj (una
lectura por segundo) y escribe un GPX 1.1 para cargarlo en el emulador de Wear OS (Extended
controls → Location → Routes) y para los tests del reloj.

Por defecto el recorrido es realista:
- ruido de GPS de σ ≈ 3 m por eje, correlacionado de un segundo al siguiente (como el real);
- velocidad que varía entre 1,0 y 1,6 m/s;
- pausas de 2 a 5 s en cada cruce (cada paso de la ruta);
- precisión informada de 5 a 15 m (en <hdop>: precisión ≈ 5 m × HDOP);
- un dato atípico cada ~200 puntos: un salto de ~40 m que dura una lectura;
- una pérdida breve de señal de 2 a 3 s.

Uso:
    python tools/route_to_gpx.py 1.214628,-77.278301 1.205584,-77.270279 "Parque Bolívar" salida.gpx
    python tools/route_to_gpx.py ... --desvio      # sale ~35 m de la ruta durante ~40 m y vuelve
    python tools/route_to_gpx.py ... --sin-senal   # además, un hueco de 15 s sin puntos
    python tools/route_to_gpx.py ... --perfecto    # sin ruido: 1,25 m/s constantes y sin pausas
    python tools/route_to_gpx.py ... --semilla 11  # el mismo recorrido cada vez
    python tools/route_to_gpx.py ... --ruta ruta.json   # guarda también la ruta del backend

Requiere httpx (ya está en backend/requirements.txt).
"""

import argparse
import json
import math
import random
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from xml.sax.saxutils import escape

import httpx

DEFAULT_BACKEND = "https://smartwatch-app.onrender.com"
TIMEOUT_S = 90  # Render duerme el servicio gratis y tarda en despertar.
EARTH_RADIUS_M = 6_371_008.8
START_TIME = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)

# Recorrido perfecto: la velocidad de caminata con la que el backend calcula la duración.
PERFECT_SPEED_MPS = 1.25
PERFECT_ACCURACY_M = 5.0
# Velocidad realista: arranca entre 1,1 y 1,4 m/s y cambia un poco cada segundo.
SPEED_MIN_MPS, SPEED_MAX_MPS = 1.0, 1.6
SPEED_START_MPS = (1.1, 1.4)
SPEED_CHANGE_MPS = 0.08
# Error del GPS por eje; de un segundo al siguiente se parece al anterior (proceso AR(1)).
NOISE_SIGMA_M = 3.0
NOISE_CORRELATION = 0.7
# Precisión que informa el GPS, cambiando despacio entre 5 y 15 m.
ACCURACY_MIN_M, ACCURACY_MAX_M = 5.0, 15.0
ACCURACY_MEAN_M, ACCURACY_SPREAD_M = 9.0, 3.0
ACCURACY_CORRELATION = 0.9
HDOP_UERE_M = 5.0
PAUSE_S = (2, 5)
OUTLIER_EVERY = (150, 250)
OUTLIER_JUMP_M = (35.0, 45.0)
BRIEF_GAP_S = (2, 3)
NO_SIGNAL_GAP_S = 15
DETOUR_OFFSET_M = 35.0
DETOUR_LENGTH_M = 40.0


def parse_point(text: str) -> tuple[float, float]:
    try:
        lat, lng = (float(part) for part in text.split(","))
    except ValueError:
        raise argparse.ArgumentTypeError(f"Se esperaba lat,lng y llegó «{text}»")
    if not (-90 <= lat <= 90 and -180 <= lng <= 180):
        raise argparse.ArgumentTypeError(f"Coordenada fuera de rango: «{text}»")
    return lat, lng


def fetch_route(backend: str, origin, destination, name: str) -> dict:
    body = {
        "origin": {"lat": origin[0], "lng": origin[1]},
        "destination": {"lat": destination[0], "lng": destination[1]},
        "destination_name": name,
    }
    try:
        response = httpx.post(f"{backend.rstrip('/')}/routes", json=body, timeout=TIMEOUT_S)
    except httpx.HTTPError as error:
        sys.exit(f"No pude conectar con el backend ({backend}): {error}")
    if response.status_code != 200:
        sys.exit(f"El backend respondió {response.status_code}: {response.text[:300]}")
    return response.json()


class LocalPlane:
    """Proyección equirectangular alrededor de un punto: (x este, y norte) en metros."""

    def __init__(self, lat: float, lng: float):
        self.lat0, self.lng0 = lat, lng
        self.cos_lat = math.cos(math.radians(lat))

    def to_xy(self, lat: float, lng: float) -> tuple[float, float]:
        x = math.radians(lng - self.lng0) * EARTH_RADIUS_M * self.cos_lat
        y = math.radians(lat - self.lat0) * EARTH_RADIUS_M
        return x, y

    def to_latlng(self, x: float, y: float) -> tuple[float, float]:
        lat = self.lat0 + math.degrees(y / EARTH_RADIUS_M)
        lng = self.lng0 + math.degrees(x / (EARTH_RADIUS_M * self.cos_lat))
        return lat, lng


def along(points: list[tuple[float, float]]) -> list[float]:
    total, out = 0.0, [0.0]
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        total += math.hypot(bx - ax, by - ay)
        out.append(total)
    return out


def point_at(points, distances, s: float) -> tuple[float, float]:
    """El punto a s metros del inicio de la línea."""
    s = min(max(s, 0.0), distances[-1])
    i = next((k for k in range(1, len(distances)) if distances[k] >= s), len(distances) - 1)
    length = distances[i] - distances[i - 1]
    f = 0.0 if length == 0 else (s - distances[i - 1]) / length
    (ax, ay), (bx, by) = points[i - 1], points[i]
    return ax + (bx - ax) * f, ay + (by - ay) * f


def project(points, distances, p: tuple[float, float]) -> float:
    """Distancia a lo largo de la línea del punto de ella más cercano a p."""
    best_d, best_s = math.inf, 0.0
    for i in range(1, len(points)):
        (ax, ay), (bx, by) = points[i - 1], points[i]
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        f = 0.0 if length2 == 0 else max(0.0, min(1.0, ((p[0] - ax) * dx + (p[1] - ay) * dy) / length2))
        d = math.hypot(ax + dx * f - p[0], ay + dy * f - p[1])
        if d < best_d:
            best_d, best_s = d, distances[i - 1] + f * math.sqrt(length2)
    return best_s


def resample(points: list[tuple[float, float]], step: float = 5.0) -> list[tuple[float, float]]:
    """Recorre la línea y deja un punto cada ~step metros (siempre incluye el final)."""
    out = [points[0]]
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        length = math.hypot(bx - ax, by - ay)
        if length == 0:
            continue
        pieces = max(1, math.ceil(length / step))
        out += [(ax + (bx - ax) * i / pieces, ay + (by - ay) * i / pieces) for i in range(1, pieces + 1)]
    return out


def add_detour(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    """A mitad de ruta se aleja DETOUR_OFFSET_M hacia la izquierda, camina DETOUR_LENGTH_M y vuelve."""
    points = resample(points)
    distances = along(points)
    total = distances[-1]
    start_s = total / 2
    end_s = min(total, start_s + DETOUR_LENGTH_M)
    i = next(k for k, s in enumerate(distances) if s >= start_s)
    j = next(k for k, s in enumerate(distances) if s >= end_s)
    if j <= i:
        return points
    (ax, ay), (bx, by) = points[i], points[j]
    length = math.hypot(bx - ax, by - ay) or 1.0
    nx, ny = -(by - ay) / length, (bx - ax) / length  # normal a la izquierda
    out_a = (ax + nx * DETOUR_OFFSET_M, ay + ny * DETOUR_OFFSET_M)
    out_b = (bx + nx * DETOUR_OFFSET_M, by + ny * DETOUR_OFFSET_M)
    detour = resample([points[i], out_a, out_b, points[j]])
    return points[:i] + detour + points[j + 1:]


@dataclass
class Reading:
    x: float
    y: float
    second: int
    accuracy_m: float


def walk_positions(points, crossings: list[float], rng: random.Random, perfect: bool) -> list[tuple[float, int]]:
    """(metros recorridos, segundo) cada segundo hasta el final, con pausas en los cruces."""
    distances = along(points)
    total = distances[-1]
    pending = sorted(s for s in crossings if 0 < s < total)
    speed = PERFECT_SPEED_MPS if perfect else rng.uniform(*SPEED_START_MPS)
    s, second, pause = 0.0, 0, 0
    out = [(s, second)]
    while s < total:
        second += 1
        if pause > 0:
            pause -= 1
        else:
            if not perfect:
                speed = min(SPEED_MAX_MPS, max(SPEED_MIN_MPS, speed + rng.gauss(0, SPEED_CHANGE_MPS)))
            s = min(total, s + speed)
            if pending and s >= pending[0]:
                # Llega a la esquina y espera para cruzar antes de seguir.
                s = pending.pop(0)
                if not perfect:
                    pause = rng.randint(*PAUSE_S)
                while pending and pending[0] <= s:
                    pending.pop(0)
        out.append((s, second))
    return out


def simulate(points, crossings: list[float], rng: random.Random, perfect: bool) -> list[Reading]:
    """Lecturas del GPS, una por segundo, de alguien que camina por `points` (metros, plano local)."""
    distances = along(points)
    readings = []
    noise_x = noise_y = accuracy_z = 0.0
    keep = math.sqrt(1 - NOISE_CORRELATION ** 2)
    keep_accuracy = math.sqrt(1 - ACCURACY_CORRELATION ** 2)
    for s, second in walk_positions(points, crossings, rng, perfect):
        x, y = point_at(points, distances, s)
        accuracy = PERFECT_ACCURACY_M
        if not perfect:
            noise_x = NOISE_CORRELATION * noise_x + keep * rng.gauss(0, NOISE_SIGMA_M)
            noise_y = NOISE_CORRELATION * noise_y + keep * rng.gauss(0, NOISE_SIGMA_M)
            accuracy_z = ACCURACY_CORRELATION * accuracy_z + keep_accuracy * rng.gauss(0, 1)
            x, y = x + noise_x, y + noise_y
            accuracy = min(ACCURACY_MAX_M, max(ACCURACY_MIN_M, ACCURACY_MEAN_M + ACCURACY_SPREAD_M * accuracy_z))
        readings.append(Reading(x, y, second, accuracy))
    if not perfect:
        add_outliers(readings, rng)
    return readings


def add_outliers(readings: list[Reading], rng: random.Random) -> None:
    """Cada ~200 lecturas una salta ~40 m hacia cualquier lado (multitrayecto entre edificios)."""
    i = rng.randint(*OUTLIER_EVERY)
    while i < len(readings) - 1:
        angle = rng.uniform(0, 2 * math.pi)
        jump = rng.uniform(*OUTLIER_JUMP_M)
        readings[i].x += jump * math.cos(angle)
        readings[i].y += jump * math.sin(angle)
        i += rng.randint(*OUTLIER_EVERY)


def drop_seconds(readings: list[Reading], start: int, seconds: int) -> list[Reading]:
    """Quita `seconds` segundos de lecturas desde `start`: la persona sigue caminando sin GPS."""
    first = readings[start].second
    return [r for r in readings if not (first <= r.second < first + seconds)]


def to_gpx(name: str, track: list[tuple[float, float, int, float]]) -> str:
    def iso(t: datetime) -> str:
        return t.strftime("%Y-%m-%dT%H:%M:%SZ")

    points = "\n".join(
        f'      <trkpt lat="{lat:.7f}" lon="{lng:.7f}">'
        f"<time>{iso(START_TIME + timedelta(seconds=second))}</time>"
        f"<hdop>{accuracy / HDOP_UERE_M:.1f}</hdop></trkpt>"
        for lat, lng, second, accuracy in track
    )
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Rumbo route_to_gpx"
     xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>{escape(name)}</name>
    <time>{iso(START_TIME)}</time>
  </metadata>
  <trk>
    <name>{escape(name)}</name>
    <trkseg>
{points}
    </trkseg>
  </trk>
</gpx>
"""


def build_track(route: dict, args, rng: random.Random) -> list[tuple[float, float, int, float]]:
    """Las lecturas del recorrido como (lat, lng, segundo, precisión en m)."""
    polyline = route["polyline"]
    plane = LocalPlane(*polyline[0])
    points = [plane.to_xy(lat, lng) for lat, lng in polyline]
    if args.desvio:
        points = add_detour(points)
    distances = along(points)
    # Los cruces son los pasos intermedios de la ruta (ni el inicio ni la llegada).
    crossings = [
        project(points, distances, plane.to_xy(step["lat"], step["lng"]))
        for step in route.get("steps", [])
        if step.get("maneuver") != "arrive"
    ]
    readings = simulate(points, crossings, rng, args.perfecto)
    if not args.perfecto:
        # La pérdida breve va después de la mitad para no taparse con el desvío ni el hueco largo.
        brief_start = int(len(readings) * rng.uniform(0.6, 0.8))
        readings = drop_seconds(readings, brief_start, rng.randint(*BRIEF_GAP_S))
    if args.sin_senal:
        # Si también hay desvío (a la mitad), el hueco va a un tercio para no taparlo.
        readings = drop_seconds(readings, int(len(readings) * (1 / 3 if args.desvio else 1 / 2)), NO_SIGNAL_GAP_S)
    return [(*plane.to_latlng(r.x, r.y), r.second, r.accuracy_m) for r in readings]


def main() -> None:
    parser = argparse.ArgumentParser(description="Convierte una ruta de Rumbo en un GPX de demostración.")
    parser.add_argument("origen", type=parse_point, help="lat,lng del origen")
    parser.add_argument("destino", type=parse_point, help="lat,lng del destino")
    parser.add_argument("nombre", help="nombre del destino")
    parser.add_argument("salida", help="archivo GPX a escribir")
    parser.add_argument("--backend", default=DEFAULT_BACKEND, help=f"URL del backend (por defecto {DEFAULT_BACKEND})")
    parser.add_argument("--desvio", action="store_true", help="agrega un desvío de ~35 m a mitad de ruta")
    parser.add_argument("--sin-senal", action="store_true", help=f"deja un hueco de {NO_SIGNAL_GAP_S} s sin puntos")
    parser.add_argument("--perfecto", action="store_true", help="sin ruido, pausas ni saltos, a 1,25 m/s")
    parser.add_argument("--semilla", type=int, help="semilla del azar para repetir el mismo recorrido")
    parser.add_argument("--ruta", help="guarda también la ruta del backend (JSON de POST /routes)")
    args = parser.parse_args()

    route = fetch_route(args.backend, args.origen, args.destino, args.nombre)
    if len(route.get("polyline") or []) < 2:
        sys.exit("El backend devolvió una ruta sin polilínea.")
    if args.ruta:
        with open(args.ruta, "w", encoding="utf-8", newline="\n") as file:
            json.dump(route, file, ensure_ascii=False, indent=1)
            file.write("\n")

    seed = args.semilla if args.semilla is not None else random.randrange(1_000_000)
    track = build_track(route, args, random.Random(seed))
    with open(args.salida, "w", encoding="utf-8", newline="\n") as file:
        file.write(to_gpx(args.nombre, track))

    minutes = track[-1][2] / 60
    kind = "perfecto" if args.perfecto else f"realista, semilla {seed}"
    print(
        f"{args.salida}: {len(track)} puntos ({kind}), ruta de {route['distance_m']:.0f} m "
        f"y {len(route.get('steps', []))} pasos, recorrido de {minutes:.1f} min."
    )


if __name__ == "__main__":
    main()
