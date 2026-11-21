import httpx

from domain.errors import ExternalServiceError

TILE_URL = "https://maps.geoapify.com/v1/tile/{style}/{z}/{x}/{y}@2x.png"
# Estilo raster oscuro de Geoapify: su fondo casi negro combina con el de la app (#0B0E14). Se probó
# contra dark-matter-dark-grey y dark-matter-brown en Pasto (ver docs/REQUIREMENTS.md, "Mapa").
DEFAULT_MAP_STYLE = "dark-matter"
PNG_SIGNATURE = b"\x89PNG"


class GeoapifyTiles:
    """Pide a Geoapify los tiles @2x (512 px). La clave se queda en el backend."""

    def __init__(self, api_key: str, client: httpx.Client | None = None, style: str = DEFAULT_MAP_STYLE):
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=10)
        self._style = style

    def get_tile(self, z: int, x: int, y: int) -> bytes:
        url = TILE_URL.format(style=self._style, z=z, x=x, y=y)
        try:
            response = self._client.get(url, params={"apiKey": self._api_key})
        except httpx.HTTPError as error:
            raise ExternalServiceError("No pude conectar con Geoapify.") from error
        if response.status_code != 200:
            raise ExternalServiceError(f"Geoapify respondió con error {response.status_code}.")
        if not response.content.startswith(PNG_SIGNATURE):
            raise ExternalServiceError("Geoapify devolvió un tile que no es PNG.")
        return response.content
