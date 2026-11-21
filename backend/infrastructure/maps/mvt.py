"""Lector mínimo de Mapbox Vector Tiles (protobuf), sin dependencias.

Solo lee lo que usa el mapa: capas, propiedades y geometrías. Especificación:
https://github.com/mapbox/vector-tile-spec/tree/master/2.1
"""

import gzip
import struct

from domain.models.map_tile import MapFeature, VectorTile

_GEOMETRY = {1: "point", 2: "line", 3: "polygon"}
_MOVE_TO, _LINE_TO, _CLOSE_PATH = 1, 2, 7


class MvtError(ValueError):
    pass


def _varint(data: bytes, pos: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        if pos >= len(data):
            raise MvtError("Varint cortado.")
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, pos
        shift += 7


def _fields(data: bytes):
    """Recorre los campos de un mensaje: (número, tipo, valor). Los de largo variable son bytes."""
    pos = 0
    while pos < len(data):
        key, pos = _varint(data, pos)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, pos = _varint(data, pos)
        elif wire == 1:
            value, pos = data[pos : pos + 8], pos + 8
        elif wire == 2:
            length, pos = _varint(data, pos)
            value, pos = data[pos : pos + length], pos + length
        elif wire == 5:
            value, pos = data[pos : pos + 4], pos + 4
        else:
            raise MvtError(f"Tipo de campo {wire} no soportado.")
        yield number, wire, value


def _packed(data: bytes) -> list[int]:
    values, pos = [], 0
    while pos < len(data):
        value, pos = _varint(data, pos)
        values.append(value)
    return values


def _zigzag(n: int) -> int:
    return (n >> 1) ^ -(n & 1)


def _value(data: bytes):
    for number, _, raw in _fields(data):
        if number == 1:
            return raw.decode("utf-8", "replace")
        if number == 2:
            return struct.unpack("<f", raw)[0]
        if number == 3:
            return struct.unpack("<d", raw)[0]
        if number in (4, 5):
            return raw
        if number == 6:
            return _zigzag(raw)
        if number == 7:
            return bool(raw)
    return None


def _geometry(commands: list[int]) -> list[list[tuple[float, float]]]:
    parts: list[list[tuple[float, float]]] = []
    x = y = 0
    i = 0
    while i < len(commands):
        command, count = commands[i] & 7, commands[i] >> 3
        i += 1
        if command == _CLOSE_PATH:
            if parts and parts[-1]:
                parts[-1].append(parts[-1][0])
            continue
        for _ in range(count):
            x += _zigzag(commands[i])
            y += _zigzag(commands[i + 1])
            i += 2
            if command == _MOVE_TO:
                parts.append([])
            if parts:
                parts[-1].append((float(x), float(y)))
    return [part for part in parts if part]


def _layer(data: bytes, wanted: set[str] | None) -> tuple[str, int, list[MapFeature]]:
    name, extent, keys, values, raw_features = "", 4096, [], [], []
    for number, _, raw in _fields(data):
        if number == 1:
            name = raw.decode("utf-8", "replace")
        elif number == 2:
            raw_features.append(raw)
        elif number == 3:
            keys.append(raw.decode("utf-8", "replace"))
        elif number == 4:
            values.append(_value(raw))
        elif number == 5:
            extent = raw
    if wanted is not None and name not in wanted:
        return name, extent, []
    features = []
    for raw in raw_features:
        tags, kind, commands = [], 0, []
        for number, _, value in _fields(raw):
            if number == 2:
                tags = _packed(value)
            elif number == 3:
                kind = value
            elif number == 4:
                commands = _packed(value)
        geometry = _GEOMETRY.get(kind)
        if geometry is None:
            continue
        properties = {keys[tags[i]]: values[tags[i + 1]] for i in range(0, len(tags) - 1, 2)}
        features.append(
            MapFeature(
                layer=name,
                geometry=geometry,
                parts=_geometry(commands),
                kind=str(properties.get("class") or ""),
                name=str(properties.get("name:latin") or properties.get("name") or ""),
            )
        )
    return name, extent, features


def decode_tile(data: bytes, z: int, x: int, y: int, layers: set[str] | None = None) -> VectorTile:
    """Lee un tile MVT (acepta gzip). Con `layers`, solo guarda esas capas."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    extent = 4096
    features: list[MapFeature] = []
    try:
        for number, wire, raw in _fields(data):
            if number == 3 and wire == 2:
                _, layer_extent, layer_features = _layer(raw, layers)
                if layer_features:
                    extent = layer_extent
                    features.extend(layer_features)
    except (IndexError, struct.error, UnicodeDecodeError) as error:
        raise MvtError("Tile vectorial dañado.") from error
    return VectorTile(z=z, x=x, y=y, extent=extent, features=features)
