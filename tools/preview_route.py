"""Muestra en un mapa la ruta a pie que devuelve el backend de Rumbo, para compararla con Google Maps.

Pide la ruta a POST /routes (Render o local) y escribe un HTML con un mapa Leaflet
(OpenStreetMap) con la polilínea y los pasos numerados con su instrucción y su calle. En la
terminal imprime distancia, duración, número de pasos, velocidad media y el cociente entre la
ruta y la línea recta (avisa si pasa de 1,6: la ruta da demasiada vuelta).

Uso:
    python tools/preview_route.py 1.214628,-77.278301 1.205584,-77.270279 --nombre "Parque Bolívar"
    python tools/preview_route.py --demo                # las 5 rutas de prueba en docs/demo/previews/
    python tools/preview_route.py ... --backend http://127.0.0.1:8000

Requiere httpx (ya está en backend/requirements.txt).
"""

import argparse
import html
import json
import math
import sys
from pathlib import Path

from route_to_gpx import DEFAULT_BACKEND, fetch_route, parse_point

EARTH_RADIUS_M = 6_371_008.8
# Una ruta a pie en una ciudad con cuadrícula rara vez pasa de 1,3 veces la línea recta.
MAX_DETOUR_RATIO = 1.6
PREVIEWS_DIR = Path(__file__).resolve().parent.parent / "docs" / "demo" / "previews"

# Rutas de prueba en Pasto, todas desde la Plaza (Parque) de Nariño. Coordenadas del geocodificador
# de Geoapify (OpenStreetMap).
DEMO_ORIGIN = (1.214628, -77.278301)
DEMO_ROUTES = [
    ("01_parque_bolivar", "Parque Bolívar", (1.205584, -77.270279)),
    ("02_plaza_del_carnaval", "Plaza del Carnaval", (1.210561, -77.276554)),
    ("03_terminal_de_transportes", "Terminal de Transportes", (1.198398, -77.269267)),
    ("04_universidad_de_narino", "Universidad de Nariño", (1.232534, -77.293389)),
    ("05_hospital_departamental", "Hospital Departamental de Nariño", (1.205241, -77.267899)),
]

LEAFLET = "https://unpkg.com/leaflet@1.9.4/dist"


def straight_line_m(a: tuple[float, float], b: tuple[float, float]) -> float:
    """Distancia en línea recta (haversine) en metros."""
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    dlat, dlng = lat2 - lat1, math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlng / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


def summarize(route: dict, origin, destination) -> dict:
    distance = route["distance_m"]
    duration = route["duration_s"]
    straight = straight_line_m(origin, destination)
    return {
        "distance_m": distance,
        "duration_min": duration / 60,
        "steps": len(route["steps"]),
        "speed_mps": distance / duration if duration else 0.0,
        "straight_m": straight,
        "ratio": distance / straight if straight else 0.0,
    }


def describe(name: str, summary: dict) -> str:
    text = (
        f"{name}: {summary['distance_m']:.0f} m, {summary['duration_min']:.1f} min, "
        f"{summary['steps']} pasos, {summary['speed_mps']:.2f} m/s ({summary['speed_mps'] * 3.6:.1f} km/h), "
        f"ruta/línea recta {summary['ratio']:.2f} ({summary['straight_m']:.0f} m en recta)"
    )
    if summary["ratio"] > MAX_DETOUR_RATIO:
        text += f"\n  ¡Atención! La ruta mide más de {MAX_DETOUR_RATIO} veces la línea recta: revisa si da demasiada vuelta."
    return text


def google_maps_url(origin, destination) -> str:
    return (
        "https://www.google.com/maps/dir/?api=1&travelmode=walking"
        f"&origin={origin[0]},{origin[1]}&destination={destination[0]},{destination[1]}"
    )


def to_html(name: str, route: dict, summary: dict, origin, destination) -> str:
    data = {
        "polyline": route["polyline"],
        "steps": route["steps"],
        "origin": origin,
        "destination": destination,
    }
    # Dentro de <script>, "</" cerraría la etiqueta antes de tiempo.
    data_json = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")
    warning = ""
    if summary["ratio"] > MAX_DETOUR_RATIO:
        warning = f'<p class="warning">La ruta mide más de {MAX_DETOUR_RATIO} veces la línea recta.</p>'
    steps = "\n".join(
        f'<li><strong>{html.escape(step["instruction"])}</strong>'
        f'<span>{html.escape(step.get("street") or "sin nombre de calle")} · {step["distance_m"]:.0f} m</span></li>'
        for step in route["steps"]
    )
    title = html.escape(f"Ruta a {name}")
    return f"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<link rel="stylesheet" href="{LEAFLET}/leaflet.css"
      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin="">
<script src="{LEAFLET}/leaflet.js"
        integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
<style>
  body {{ margin: 0; font: 15px/1.4 system-ui, sans-serif; color: #1b1f27; background: #f6f7f9;
         display: grid; grid-template-columns: 360px 1fr; height: 100vh; }}
  aside {{ overflow-y: auto; padding: 16px; background: #fff; border-right: 1px solid #dde1e7; }}
  h1 {{ font-size: 20px; margin: 0 0 8px; }}
  dl {{ display: grid; grid-template-columns: auto 1fr; gap: 2px 12px; margin: 0 0 12px; }}
  dt {{ color: #5b6372; }} dd {{ margin: 0; font-weight: 600; }}
  ol {{ padding-left: 24px; }} li {{ margin-bottom: 8px; }}
  li span {{ display: block; color: #5b6372; font-size: 13px; }}
  .warning {{ background: #fff1e6; border: 1px solid #ff8a3d; padding: 8px; border-radius: 6px; }}
  #map {{ height: 100vh; }}
  .step-number {{ background: #4f8eff; color: #fff; border: 2px solid #fff; border-radius: 50%;
                  width: 24px; height: 24px; display: grid; place-items: center; font-weight: 700;
                  box-shadow: 0 1px 3px rgba(0, 0, 0, .4); font-size: 12px; }}
  @media (max-width: 700px) {{ body {{ grid-template-columns: 1fr; height: auto; }} #map {{ height: 70vh; }} }}
</style>
</head>
<body>
<aside>
  <h1>{title}</h1>
  <dl>
    <dt>Distancia</dt><dd>{summary['distance_m']:.0f} m</dd>
    <dt>Duración</dt><dd>{summary['duration_min']:.1f} min</dd>
    <dt>Pasos</dt><dd>{summary['steps']}</dd>
    <dt>Velocidad media</dt><dd>{summary['speed_mps']:.2f} m/s ({summary['speed_mps'] * 3.6:.1f} km/h)</dd>
    <dt>Línea recta</dt><dd>{summary['straight_m']:.0f} m</dd>
    <dt>Ruta / recta</dt><dd>{summary['ratio']:.2f}</dd>
  </dl>
  {warning}
  <p><a href="{google_maps_url(origin, destination)}" target="_blank" rel="noopener">Abrir la misma ruta a pie en Google Maps</a></p>
  <ol>
{steps}
  </ol>
</aside>
<div id="map"></div>
<script>
  const data = {data_json};
  const map = L.map("map");
  L.tileLayer("https://tile.openstreetmap.org/{{z}}/{{x}}/{{y}}.png", {{
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }}).addTo(map);
  const line = L.polyline(data.polyline, {{ color: "#4f8eff", weight: 5 }}).addTo(map);
  L.circleMarker(data.origin, {{ radius: 7, color: "#1b1f27", fillColor: "#fff", fillOpacity: 1 }})
    .bindTooltip("Origen").addTo(map);
  L.circleMarker(data.destination, {{ radius: 7, color: "#1b1f27", fillColor: "#3ddc84", fillOpacity: 1 }})
    .bindTooltip("Destino").addTo(map);
  data.steps.forEach((step) => {{
    const icon = L.divIcon({{ className: "", html: `<div class="step-number">${{step.order}}</div>`, iconSize: [24, 24] }});
    const popup = document.createElement("div");
    const text = document.createElement("strong");
    text.textContent = `${{step.order}}. ${{step.instruction}}`;
    const street = document.createElement("div");
    street.textContent = `${{step.street || "sin nombre de calle"}} · ${{Math.round(step.distance_m)}} m`;
    popup.append(text, street);
    L.marker([step.lat, step.lng], {{ icon }}).bindPopup(popup).addTo(map);
  }});
  map.fitBounds(line.getBounds(), {{ padding: [24, 24] }});
</script>
</body>
</html>
"""


def preview(backend: str, origin, destination, name: str, output: Path) -> dict:
    route = fetch_route(backend, origin, destination, name)
    summary = summarize(route, origin, destination)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(to_html(name, route, summary, origin, destination), encoding="utf-8", newline="\n")
    print(describe(name, summary))
    print(f"  → {output}")
    return summary


def index_html(rows: list[tuple[str, str, tuple[float, float], dict]]) -> str:
    lines = "\n".join(
        f'<tr><td><a href="{slug}.html">{html.escape(name)}</a></td><td>{s["distance_m"]:.0f} m</td>'
        f'<td>{s["duration_min"]:.1f} min</td><td>{s["steps"]}</td><td>{s["speed_mps"]:.2f} m/s</td>'
        f'<td>{s["ratio"]:.2f}{" ⚠" if s["ratio"] > MAX_DETOUR_RATIO else ""}</td>'
        f'<td><a href="{google_maps_url(DEMO_ORIGIN, destination)}" target="_blank" rel="noopener">Google Maps</a></td></tr>'
        for slug, name, destination, s in rows
    )
    return f"""<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Rutas de prueba en Pasto</title>
<style>
  body {{ font: 15px/1.4 system-ui, sans-serif; margin: 24px; color: #1b1f27; }}
  table {{ border-collapse: collapse; }} th, td {{ padding: 6px 12px; border-bottom: 1px solid #dde1e7; text-align: left; }}
  td:nth-child(n+2):nth-child(-n+6) {{ text-align: right; font-variant-numeric: tabular-nums; }}
</style>
</head>
<body>
<h1>Rutas de prueba en Pasto</h1>
<p>Todas desde la Plaza de Nariño ({DEMO_ORIGIN[0]}, {DEMO_ORIGIN[1]}), calculadas por POST /routes.
Compara cada una con Google Maps: ⚠ marca las que miden más de {MAX_DETOUR_RATIO} veces la línea recta.</p>
<table>
<thead><tr><th>Destino</th><th>Distancia</th><th>Duración</th><th>Pasos</th><th>Velocidad</th><th>Ruta/recta</th><th></th></tr></thead>
<tbody>
{lines}
</tbody>
</table>
</body>
</html>
"""


def main() -> None:
    parser = argparse.ArgumentParser(description="Dibuja en un mapa la ruta a pie del backend de Rumbo.")
    parser.add_argument("origen", nargs="?", type=parse_point, help="lat,lng del origen")
    parser.add_argument("destino", nargs="?", type=parse_point, help="lat,lng del destino")
    parser.add_argument("--nombre", default="Destino", help="nombre del destino")
    parser.add_argument("--salida", type=Path, help="archivo HTML (por defecto docs/demo/previews/ruta.html)")
    parser.add_argument("--backend", default=DEFAULT_BACKEND, help=f"URL del backend (por defecto {DEFAULT_BACKEND})")
    parser.add_argument("--demo", action="store_true", help="genera las 5 rutas de prueba de Pasto en docs/demo/previews/")
    args = parser.parse_args()

    if args.demo:
        rows = []
        for slug, name, destination in DEMO_ROUTES:
            summary = preview(args.backend, DEMO_ORIGIN, destination, name, PREVIEWS_DIR / f"{slug}.html")
            rows.append((slug, name, destination, summary))
        (PREVIEWS_DIR / "index.html").write_text(index_html(rows), encoding="utf-8", newline="\n")
        print(f"Resumen: {PREVIEWS_DIR / 'index.html'}")
        return
    if args.origen is None or args.destino is None:
        parser.error("indica origen y destino (lat,lng), o usa --demo")
    preview(args.backend, args.origen, args.destino, args.nombre, args.salida or PREVIEWS_DIR / "ruta.html")


if __name__ == "__main__":
    sys.exit(main())
