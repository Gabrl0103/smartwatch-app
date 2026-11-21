import httpx

from domain.errors import ExternalServiceError
from domain.models.map_tile import VectorTile
from infrastructure.maps.mvt import MvtError, decode_tile

VECTOR_TILE_URL = "https://maps.geoapify.com/v1/tile/vector/{z}/{x}/{y}.pbf"
# Los tiles vectoriales de Geoapify (OpenMapTiles) llegan hasta el zoom 14; más cerca se recorta uno de 14.
MAX_VECTOR_ZOOM = 14
# Solo las capas que se dibujan o de las que salen los nombres: el resto se descarta al leer.
MAP_LAYERS = {"water", "waterway", "landcover", "landuse", "park", "building", "transportation", "transportation_name"}


class GeoapifyVectorTiles:
    """Pide a Geoapify los tiles vectoriales y los lee. La clave se queda en el backend."""

    max_zoom = MAX_VECTOR_ZOOM

    def __init__(self, api_key: str, client: httpx.Client | None = None):
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=15)

    def get_vector_tile(self, z: int, x: int, y: int) -> VectorTile:
        url = VECTOR_TILE_URL.format(z=z, x=x, y=y)
        try:
            response = self._client.get(url, params={"apiKey": self._api_key})
        except httpx.HTTPError as error:
            raise ExternalServiceError("No pude conectar con Geoapify.") from error
        if response.status_code != 200:
            raise ExternalServiceError(f"Geoapify respondió con error {response.status_code}.")
        try:
            return decode_tile(response.content, z, x, y, MAP_LAYERS)
        except MvtError as error:
            raise ExternalServiceError("Geoapify devolvió un tile vectorial que no se pudo leer.") from error
