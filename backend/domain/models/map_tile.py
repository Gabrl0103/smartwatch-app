from dataclasses import dataclass, field


@dataclass(frozen=True)
class MapFeature:
    """Un elemento del mapa vectorial: calle, edificio, agua, parque...

    `parts` son líneas o anillos en coordenadas del tile (0 a `extent`, y hacia abajo); `kind` es la
    clase de OpenMapTiles (p. ej. "minor", "primary", "park") y `name`, el nombre si lo tiene.
    """

    layer: str
    geometry: str  # "point", "line" o "polygon"
    parts: list[list[tuple[float, float]]]
    kind: str = ""
    name: str = ""


@dataclass(frozen=True)
class VectorTile:
    z: int
    x: int
    y: int
    extent: int
    features: list[MapFeature] = field(default_factory=list)

    def layer(self, name: str) -> list[MapFeature]:
        return [feature for feature in self.features if feature.layer == name]


@dataclass(frozen=True)
class StreetName:
    """Una calle con nombre, en [lat, lng], para que el reloj escriba su nombre derecho."""

    name: str
    line: list[tuple[float, float]]
