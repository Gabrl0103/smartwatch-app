"""Dibuja los tiles del mapa del reloj a partir de los vectoriales, sin nombres de calle.

Los nombres los escribe el reloj, siempre derechos: en un tile ya dibujado girarían con el mapa y,
al caminar hacia el sur, se leerían al revés. Colores oscuros a juego con el fondo de la app
(#0B0E14) y calles en gris para que la ruta azul resalte.
"""

import io

from PIL import Image, ImageDraw

from domain.models.map_tile import MapFeature, VectorTile

TILE_PX = 512  # como los @2x: 256 px de mundo con el doble de detalle
_SUPERSAMPLE = 2  # se dibuja al doble y se reduce: bordes suaves

_BACKGROUND = (11, 14, 20)
_GREEN = (17, 26, 24)
_WATER = (18, 27, 42)
_BUILDING = (18, 21, 29)
_BUILDING_EDGE = (31, 35, 46)
_ROAD_EDGE = (58, 63, 77)
_ROAD = (34, 38, 49)
_MAJOR_ROAD = (44, 49, 62)
_PATH = (52, 57, 71)

_GREEN_KINDS = {"park", "grass", "wood", "forest", "pitch", "playground", "garden", "meadow", "cemetery", "golf_course"}
_MAJOR_KINDS = {"motorway", "trunk", "primary", "secondary", "tertiary"}
_MINOR_KINDS = {"minor", "service", "track", "raceway"}

# Ancho de cada calle a zoom 17, en píxeles del tile de 512 (borde, relleno).
_ROAD_WIDTHS = {"major": (22, 18), "minor": (15, 11), "service": (10, 7)}
_PATH_WIDTH = 4


def _road_style(kind: str) -> str | None:
    if kind in _MAJOR_KINDS:
        return "major"
    if kind == "service":
        return "service"
    if kind in _MINOR_KINDS:
        return "minor"
    return None


class PillowTileRenderer:
    def render(self, tile: VectorTile, z: int, x: int, y: int) -> bytes:
        scale = 2 ** (z - tile.z)
        span = tile.extent / scale
        origin_x = (x - tile.x * scale) * span
        origin_y = (y - tile.y * scale) * span
        size = TILE_PX * _SUPERSAMPLE
        factor = size / span
        # Lo que se sale un poco del tile también se dibuja: una calle ancha cruza el borde.
        margin = span * 0.1
        bounds = (origin_x - margin, origin_y - margin, origin_x + span + margin, origin_y + span + margin)
        # A otros zooms las calles se afinan o engordan, sin desaparecer.
        width_scale = _SUPERSAMPLE * 2 ** ((z - 17) * 0.75)

        def project(part):
            return [((u - origin_x) * factor, (v - origin_y) * factor) for u, v in part]

        def visible(feature: MapFeature):
            return [part for part in feature.parts if _touches(part, bounds)]

        image = Image.new("RGB", (size, size), _BACKGROUND)
        draw = ImageDraw.Draw(image)

        for feature in tile.layer("landcover") + tile.layer("landuse") + tile.layer("park"):
            if feature.geometry == "polygon" and (feature.kind in _GREEN_KINDS or feature.layer == "park"):
                for part in visible(feature):
                    _polygon(draw, project(part), _GREEN)
        for feature in tile.layer("water"):
            for part in visible(feature):
                _polygon(draw, project(part), _WATER)
        for feature in tile.layer("waterway"):
            for part in visible(feature):
                _line(draw, project(part), _WATER, 6 * width_scale)
        for feature in tile.layer("building"):
            for part in visible(feature):
                _polygon(draw, project(part), _BUILDING, _BUILDING_EDGE)

        roads = [(feature, _road_style(feature.kind)) for feature in tile.layer("transportation") if feature.geometry == "line"]
        for feature in tile.layer("transportation"):
            if feature.kind == "path" and feature.geometry == "line":
                for part in visible(feature):
                    _line(draw, project(part), _PATH, _PATH_WIDTH * width_scale)
        # Primero todos los bordes y luego los rellenos: los cruces quedan limpios.
        for style_name in ("service", "minor", "major"):
            for feature, style in roads:
                if style == style_name:
                    for part in visible(feature):
                        _line(draw, project(part), _ROAD_EDGE, _ROAD_WIDTHS[style][0] * width_scale)
        for style_name in ("service", "minor", "major"):
            color = _MAJOR_ROAD if style_name == "major" else _ROAD
            for feature, style in roads:
                if style == style_name:
                    for part in visible(feature):
                        _line(draw, project(part), color, _ROAD_WIDTHS[style][1] * width_scale)

        output = io.BytesIO()
        image.resize((TILE_PX, TILE_PX), Image.Resampling.LANCZOS).save(output, format="PNG", optimize=True)
        return output.getvalue()


def _touches(part, bounds) -> bool:
    left, top, right, bottom = bounds
    xs = [p[0] for p in part]
    ys = [p[1] for p in part]
    return min(xs) <= right and max(xs) >= left and min(ys) <= bottom and max(ys) >= top


def _polygon(draw: ImageDraw.ImageDraw, points, fill, outline=None) -> None:
    if len(points) >= 3:
        draw.polygon(points, fill=fill, outline=outline, width=2 if outline else 0)


def _line(draw: ImageDraw.ImageDraw, points, color, width: float) -> None:
    if len(points) < 2:
        return
    w = max(1, round(width))
    draw.line(points, fill=color, width=w, joint="curve")
    # Puntas redondas: sin ellas cada tramo termina en un corte recto.
    r = w / 2
    for px, py in (points[0], points[-1]):
        draw.ellipse((px - r, py - r, px + r, py + r), fill=color)
