import httpx

from domain.errors import ExternalServiceError
from domain.models.route import Coordinates, Place

SEARCH_URL = "https://api.geoapify.com/v1/geocode/search"

# Centro de Pasto: sesga la búsqueda cuando no llega la ubicación del usuario.
PASTO_CENTER = Coordinates(lat=1.2136, lng=-77.2811)


class GeoapifyGeocoder:
    def __init__(self, api_key: str, client: httpx.Client | None = None):
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=15)

    def search(self, query: str, near: Coordinates | None = None) -> Place | None:
        bias = near or PASTO_CENTER
        params = {
            "text": query,
            "limit": 1,
            "format": "json",
            "filter": "countrycode:co",
            "bias": f"proximity:{bias.lng},{bias.lat}",
            "apiKey": self._api_key,
        }
        try:
            response = self._client.get(SEARCH_URL, params=params)
        except httpx.HTTPError as error:
            raise ExternalServiceError("No pude conectar con Geoapify.") from error
        if response.status_code != 200:
            raise ExternalServiceError(
                f"Geoapify respondió con error {response.status_code}."
            )
        try:
            results = response.json()["results"]
            if not results:
                return None
            first = results[0]
            name = first.get("name") or first.get("address_line1") or first["formatted"]
            return Place(name=name, coordinates=Coordinates(first["lat"], first["lon"]))
        except (KeyError, TypeError, ValueError) as error:
            raise ExternalServiceError("Geoapify devolvió una respuesta inesperada.") from error
