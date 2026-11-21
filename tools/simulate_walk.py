"""Simula a una persona caminando la ruta que Rumbo está siguiendo en el emulador, para la demo.

Lee del reloj la ruta en curso (la que la app guarda para "Continuar ruta anterior"), la recorre
con `adb emu geo fix` a una lectura por segundo y muestra cada cambio de estado de la app. No
llama al backend: camina exactamente la ruta que la app tiene, sea cual sea el destino. Si la
app recalcula (tras un desvío), sigue la ruta nueva desde donde está la persona.

Uso (con Rumbo abierto en el emulador; después toca "Ir" en el reloj):
    python tools/simulate_walk.py                      # camina a 3 m/s hasta el destino
    python tools/simulate_walk.py --desvio --sin-senal # además: sale 45 m de la ruta y pierde el GPS 16 s
    python tools/simulate_walk.py --velocidad 1.4      # al paso de una persona (más lento)

Solo usa la biblioteca estándar. Ctrl+C lo detiene y deja la ubicación encendida.
"""

import argparse
import html
import json
import math
import os
import re
import subprocess
import sys
import time

PACKAGE = "com.rumbo.watchapp"
SERVICE = "NavigationForegroundService"
EARTH_RADIUS_M = 6_371_008.8
MAX_SPEED_MPS = 3.9  # el filtro de GPS de la app descarta saltos de más de 4 m/s
DETOUR_M = 45.0
DETOUR_HOLD_S = 14
NO_SIGNAL_S = 16
HOLD_AT_END_S = 12


def find_adb() -> str:
    sdk = os.environ.get("ANDROID_HOME") or os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")
    adb = os.path.join(sdk, "platform-tools", "adb.exe" if os.name == "nt" else "adb")
    if not os.path.exists(adb):
        sys.exit(f"No encuentro adb en {adb}. ¿Está instalado Android Studio?")
    return adb


ADB = find_adb()


def adb(*args: str) -> str:
    result = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return result.stdout


def fix(lat: float, lng: float) -> None:
    adb("emu", "geo", "fix", f"{lng:.7f}", f"{lat:.7f}")  # primero la longitud


def set_location(enabled: bool) -> None:
    adb("shell", "cmd", "location", "set-location-enabled", "true" if enabled else "false")


def navigating() -> bool:
    return SERVICE in adb("shell", "dumpsys", "activity", "services", PACKAGE)


def active_route() -> dict | None:
    """La ruta en curso que guardó la app (JSON de POST /routes), o None si no hay."""
    xml = adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/rumbo_active_route.xml")
    match = re.search(r'<string name="route">(.*?)</string>', xml, re.S)
    if not match:
        return None
    try:
        route = json.loads(html.unescape(match.group(1)))
    except json.JSONDecodeError:
        return None
    return route if len(route.get("polyline") or []) >= 2 else None


class Plane:
    """Proyección equirectangular local: (x este, y norte) en metros alrededor de un punto."""

    def __init__(self, lat: float, lng: float):
        self.lat0, self.lng0, self.cos = lat, lng, math.cos(math.radians(lat))

    def xy(self, lat: float, lng: float) -> tuple[float, float]:
        return (math.radians(lng - self.lng0) * EARTH_RADIUS_M * self.cos, math.radians(lat - self.lat0) * EARTH_RADIUS_M)

    def latlng(self, x: float, y: float) -> tuple[float, float]:
        return (self.lat0 + math.degrees(y / EARTH_RADIUS_M), self.lng0 + math.degrees(x / (EARTH_RADIUS_M * self.cos)))


def resample(points: list[tuple[float, float]], step: float) -> list[tuple[float, float]]:
    """Un punto cada `step` metros a lo largo de la línea (incluye el final)."""
    out = [points[0]]
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        length = math.hypot(bx - ax, by - ay)
        pieces = max(1, math.ceil(length / step)) if length else 0
        out += [(ax + (bx - ax) * i / pieces, ay + (by - ay) * i / pieces) for i in range(1, pieces + 1)]
    return out


def nearest_index(points: list[tuple[float, float]], p: tuple[float, float]) -> int:
    return min(range(len(points)), key=lambda i: math.hypot(points[i][0] - p[0], points[i][1] - p[1]))


class StateLog:
    """Muestra los cambios de estado que escribe la app en Logcat (etiqueta "Nav")."""

    NAMES = {
        "ON_ROUTE": "En ruta", "TURN_APPROACHING": "Giro próximo", "MANEUVER": "Gira ahora",
        "OFF_ROUTE": "Fuera de ruta", "NO_SIGNAL": "Sin señal GPS", "ARRIVED": "Llegaste",
    }

    def __init__(self):
        adb("logcat", "-c")
        self.seen = 0

    def show(self) -> None:
        lines = [l for l in adb("logcat", "-d", "-s", "Nav").splitlines() if "Nav" in l and ":" in l]
        for line in lines[self.seen:]:
            message = line.split(" Nav ", 1)[-1].split(":", 1)[-1].strip()
            state = re.search(r"Estado \S+ → (\w+)", message)
            if state:
                print(f"  ● {self.NAMES.get(state.group(1), state.group(1))}  ({message.split('(', 1)[-1].rstrip(')')})", flush=True)
            elif "Recalculando" in message or "recalculada" in message or "Navegación" in message:
                print(f"  · {message}", flush=True)
        self.seen = len(lines)


class Ticker:
    """Marca un paso por segundo de reloj real, descontando lo que tardan las llamadas a adb."""

    def __init__(self):
        self.next = time.monotonic()
        self.count = 0

    def wait(self) -> None:
        self.next += 1
        delay = self.next - time.monotonic()
        if delay > 0:
            time.sleep(delay)
        elif delay < -2:
            self.next = time.monotonic()  # muy atrasado (adb lento): no se apura para alcanzar
        self.count += 1


def wait_for_route() -> dict:
    print("Esperando una ruta: en el reloj elige un destino y toca \"Ir\"...", flush=True)
    deadline = time.time() + 15 * 60
    while time.time() < deadline:
        if navigating():
            route = active_route()
            if route:
                return route
        time.sleep(2)
    sys.exit("Pasaron 15 minutos sin que empiece una ruta.")


def walk(args) -> None:
    route = wait_for_route()
    plane = Plane(*route["polyline"][0])
    step = min(args.velocidad, MAX_SPEED_MPS)

    def path_of(r: dict) -> list[tuple[float, float]]:
        return resample([plane.xy(lat, lng) for lat, lng in r["polyline"]], step)

    path = path_of(route)
    print(f"Ruta a «{route['destination_name']}»: {route['distance_m']:.0f} m. "
          f"Caminando a {step:.1f} m/s (unos {len(path) / 60:.0f} min).", flush=True)
    log = StateLog()
    ticker = Ticker()
    walked = 0.0
    detour_done = signal_done = False
    i = 0
    while i < len(path):
        # Consultar el servicio cuesta: se mira cada 5 pasos.
        if ticker.count % 5 == 0 and not navigating():
            print("La navegación terminó en el reloj.", flush=True)
            return
        x, y = path[i]
        done = walked / max(route["distance_m"], 1)
        if args.desvio and not detour_done and done >= 0.4 and i > 0:
            detour_done = True
            print("Desvío: me alejo de la ruta...", flush=True)
            (ax, ay), (bx, by) = path[i - 1], path[i]
            length = math.hypot(bx - ax, by - ay) or 1
            px, py = -(by - ay) / length, (bx - ax) / length
            offsets = [min(k * step, DETOUR_M) for k in range(1, math.ceil(DETOUR_M / step) + 1)]
            for off in offsets + [DETOUR_M] * DETOUR_HOLD_S + offsets[::-1] + [0.0]:
                fix(*plane.latlng(x + px * off, y + py * off))
                ticker.wait()
                log.show()
            # Si la app recalculó, se sigue la ruta nueva desde donde está la persona.
            new_route = active_route()
            if new_route and new_route["polyline"] != route["polyline"]:
                route, path = new_route, path_of(new_route)
                i = nearest_index(path, (x, y))
                walked = route["distance_m"] * i / max(len(path), 1)
                print(f"Sigo la ruta recalculada: {route['distance_m']:.0f} m.", flush=True)
                continue
        if args.sin_senal and not signal_done and done >= 0.7:
            signal_done = True
            print(f"Sin señal: apago la ubicación {NO_SIGNAL_S} s...", flush=True)
            set_location(False)
            for _ in range(NO_SIGNAL_S):
                ticker.wait()
                log.show()
            set_location(True)
        fix(*plane.latlng(x, y))
        ticker.wait()
        if ticker.count % 2 == 0:
            log.show()
        walked += step
        i += 1
    ex, ey = path[-1]
    for _ in range(HOLD_AT_END_S):
        fix(*plane.latlng(ex, ey))
        ticker.wait()
        log.show()
    print("Fin del recorrido.", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="Camina en el emulador la ruta que sigue Rumbo.")
    parser.add_argument("--velocidad", type=float, default=3.0, help="metros por segundo (máx. 3,9; por defecto 3)")
    parser.add_argument("--desvio", action="store_true", help="sale 45 m de la ruta al 40 %% y vuelve")
    parser.add_argument("--sin-senal", action="store_true", help="apaga la ubicación 16 s al 70 %%")
    args = parser.parse_args()
    if "device" not in adb("get-state"):
        sys.exit("No hay un emulador abierto: abre Rumbo primero (acceso directo \"Rumbo\").")
    try:
        walk(args)
    except KeyboardInterrupt:
        print("\nDetenido.", flush=True)
    finally:
        set_location(True)


if __name__ == "__main__":
    main()
