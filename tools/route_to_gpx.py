"""Genera un GPX de demostración a partir de una ruta real del backend de Rumbo.

Pide la ruta a pie a POST /routes, la recorre cada ~5 m a 1,4 m/s y escribe un GPX 1.1
para cargarlo en el emulador de Wear OS (Extended controls → Location → Routes).

Uso:
    python tools/route_to_gpx.py 1.2136,-77.2811 1.2056,-77.2703 "Parque Bolívar, Pasto" salida.gpx
    python tools/route_to_gpx.py ... --desvio      # sale ~35 m de la ruta durante ~40 m y vuelve
    python tools/route_to_gpx.py ... --sin-senal   # deja un hueco de 15 s sin puntos

Requiere httpx (ya está en backend/requirements.txt).
"""

import argparse
import math
import sys
from datetime import datetime, timedelta, timezone
from xml.sax.saxutils import escape

import httpx

DEFAULT_BACKEND = "https://smartwatch-app.onrender.com"
TIMEOUT_S = 90  # Render duerme el servicio gratis y tarda en despertar.
EARTH_RADIUS_M = 6_371_008.8
STEP_M = 5.0
WALK_SPEED_MPS = 1.4
START_TIME = datetime(2026, 1, 1, 12, 0, 0, tzinfo=timezone.utc)
DETOUR_OFFSET_M = 35.0
DETOUR_LENGTH_M = 40.0
NO_SIGNAL_GAP_S = 15


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


def resample(points: list[tuple[float, float]], step: float = STEP_M) -> list[tuple[float, float]]:
    """Recorre la línea y deja un punto cada ~step metros (siempre incluye el final)."""
    out = [points[0]]
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        length = math.hypot(bx - ax, by - ay)
        if length == 0:
            continue
        pieces = max(1, math.ceil(length / step))
        out += [(ax + (bx - ax) * i / pieces, ay + (by - ay) * i / pieces) for i in range(1, pieces + 1)]
    return out


def along(points: list[tuple[float, float]]) -> list[float]:
    total, out = 0.0, [0.0]
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        total += math.hypot(bx - ax, by - ay)
        out.append(total)
    return out


def add_detour(points: list[tuple[float, float]]) -> list[tuple[float, float]]:
    """A mitad de ruta se aleja DETOUR_OFFSET_M hacia la izquierda, camina DETOUR_LENGTH_M y vuelve."""
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


def timestamps(points: list[tuple[float, float]]) -> list[datetime]:
    """Tiempos a velocidad de caminata, en segundos enteros y siempre crecientes."""
    times, last = [], -1
    for s in along(points):
        second = max(round(s / WALK_SPEED_MPS), last + 1)
        times.append(START_TIME + timedelta(seconds=second))
        last = second
    return times


def drop_signal(track: list[tuple[tuple[float, float], datetime]], at_fraction: float):
    """Quita los puntos de NO_SIGNAL_GAP_S segundos: la persona sigue caminando pero no hay GPS."""
    gap_start = track[int(len(track) * at_fraction)][1]
    gap_end = gap_start + timedelta(seconds=NO_SIGNAL_GAP_S)
    return [(p, t) for p, t in track if not (gap_start < t < gap_end)]


def to_gpx(name: str, track) -> str:
    def iso(t: datetime) -> str:
        return t.strftime("%Y-%m-%dT%H:%M:%SZ")

    points = "\n".join(
        f'      <trkpt lat="{lat:.7f}" lon="{lng:.7f}"><time>{iso(t)}</time></trkpt>'
        for (lat, lng), t in track
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


def main() -> None:
    parser = argparse.ArgumentParser(description="Convierte una ruta de Rumbo en un GPX de demostración.")
    parser.add_argument("origen", type=parse_point, help="lat,lng del origen")
    parser.add_argument("destino", type=parse_point, help="lat,lng del destino")
    parser.add_argument("nombre", help="nombre del destino")
    parser.add_argument("salida", help="archivo GPX a escribir")
    parser.add_argument("--backend", default=DEFAULT_BACKEND, help=f"URL del backend (por defecto {DEFAULT_BACKEND})")
    parser.add_argument("--desvio", action="store_true", help="agrega un desvío de ~35 m a mitad de ruta")
    parser.add_argument("--sin-senal", action="store_true", help=f"deja un hueco de {NO_SIGNAL_GAP_S} s sin puntos")
    args = parser.parse_args()

    route = fetch_route(args.backend, args.origen, args.destino, args.nombre)
    polyline = route.get("polyline") or []
    if len(polyline) < 2:
        sys.exit("El backend devolvió una ruta sin polilínea.")

    plane = LocalPlane(*polyline[0])
    points = resample([plane.to_xy(lat, lng) for lat, lng in polyline])
    if args.desvio:
        points = add_detour(points)
    track = list(zip((plane.to_latlng(x, y) for x, y in points), timestamps(points)))
    if args.sin_senal:
        # Si también hay desvío (a la mitad), el hueco va a un tercio para no taparlo.
        track = drop_signal(track, 1 / 3 if args.desvio else 1 / 2)

    with open(args.salida, "w", encoding="utf-8", newline="\n") as file:
        file.write(to_gpx(args.nombre, track))

    seconds = (track[-1][1] - track[0][1]).total_seconds()
    print(
        f"{args.salida}: {len(track)} puntos, ruta de {route['distance_m']:.0f} m "
        f"y {len(route.get('steps', []))} pasos, recorrido de {seconds / 60:.1f} min."
    )


if __name__ == "__main__":
    main()
