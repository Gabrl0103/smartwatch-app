import httpx

from domain.errors import ExternalServiceError
from domain.models.place_kind import PLACE_KIND_LABELS, PlaceKind
from domain.models.route import Coordinates, Place
from domain.text import plain_words

SEARCH_URL = "https://api.geoapify.com/v1/geocode/search"
PLACES_URL = "https://api.geoapify.com/v2/places"

# Centro de Pasto: sesga la búsqueda cuando no llega la ubicación del usuario.
PASTO_CENTER = Coordinates(lat=1.2136, lng=-77.2811)

# Con menos confianza que esto, Geoapify puede devolver otro lugar del mismo tipo ("Droguería
# Alemana" → "Droguería Belalcazar"): solo se acepta si su nombre tiene las palabras propias de lo
# pedido. "Parque Nariño" → "Plaza de Nariño" (confianza 0) sí es el mismo lugar.
MIN_CONFIDENCE = 0.5

# Palabras que no distinguen un lugar de otro de su tipo.
_GENERIC_WORDS = {
    "de", "del", "la", "el", "los", "las", "y", "en", "san", "santa",
    "parque", "plaza", "plazoleta", "iglesia", "drogueria", "farmacia", "hospital", "clinica",
    "banco", "universidad", "colegio", "escuela", "tienda", "restaurante", "centro", "comercial",
    "calle", "carrera", "avenida", "barrio", "edificio", "hotel", "supermercado", "panaderia",
    "cafe", "cafeteria", "estacion", "terminal",
}

# Categorías de la Places API de Geoapify (apidocs.geoapify.com/docs/places, "Supported categories").
PLACE_CATEGORIES: dict[PlaceKind, str] = {
    PlaceKind.PHARMACY: "healthcare.pharmacy",
    PlaceKind.HOSPITAL: "healthcare.hospital",
    PlaceKind.CLINIC: "healthcare.clinic_or_praxis",
    PlaceKind.ATM: "service.financial.atm",
    PlaceKind.BANK: "service.financial.bank",
    PlaceKind.SUPERMARKET: "commercial.supermarket",
    PlaceKind.STORE: "commercial.convenience",
    PlaceKind.BAKERY: "commercial.food_and_drink.bakery",
    PlaceKind.RESTAURANT: "catering.restaurant",
    PlaceKind.CAFE: "catering.cafe",
    PlaceKind.TOILET: "amenity.toilet",
    PlaceKind.PARK: "leisure.park",
    PlaceKind.BUS_STOP: "public_transport.bus",
    PlaceKind.POLICE: "service.police",
    PlaceKind.GAS_STATION: "service.vehicle.fuel",
}


class GeoapifyGeocoder:
    def __init__(self, api_key: str, client: httpx.Client | None = None):
        self._api_key = api_key
        self._client = client or httpx.Client(timeout=15)

    def search(self, query: str, near: Coordinates | None = None, within_m: float | None = None) -> Place | None:
        center = near or PASTO_CENTER
        filters = ["countrycode:co"]
        if within_m is not None:
            # Sin esto, "droguería" daba una de Bogotá: el sesgo solo ordena, no descarta.
            filters.append(f"circle:{center.lng},{center.lat},{int(within_m)}")
        params = {
            "text": query,
            "limit": 1,
            "format": "json",
            "filter": "|".join(filters),
            "bias": f"proximity:{center.lng},{center.lat}",
            "apiKey": self._api_key,
        }
        data = self._get(SEARCH_URL, params)
        try:
            results = data["results"]
            if not results:
                return None
            first = results[0]
            name = first.get("name") or first.get("address_line1") or first["formatted"]
            if not _matches(query, name, first.get("rank") or {}):
                return None
            return Place(name=name, coordinates=Coordinates(first["lat"], first["lon"]))
        except (KeyError, TypeError, ValueError) as error:
            raise ExternalServiceError("Geoapify devolvió una respuesta inesperada.") from error

    def nearest(self, kind: PlaceKind, near: Coordinates | None, within_m: float) -> Place | None:
        """Una sola petición a la Places API con limit=1: 1 crédito (cobra 1 por cada 20 lugares)."""
        center = near or PASTO_CENTER
        params = {
            "categories": PLACE_CATEGORIES[kind],
            "filter": f"circle:{center.lng},{center.lat},{int(within_m)}",
            "bias": f"proximity:{center.lng},{center.lat}",
            "limit": 1,
            "apiKey": self._api_key,
        }
        data = self._get(PLACES_URL, params)
        try:
            features = data["features"]
            if not features:
                return None
            properties = features[0]["properties"]
            return Place(
                name=_place_name(kind, properties),
                coordinates=Coordinates(properties["lat"], properties["lon"]),
            )
        except (KeyError, TypeError, ValueError) as error:
            raise ExternalServiceError("Geoapify devolvió una respuesta inesperada.") from error

    def _get(self, url: str, params: dict) -> dict:
        try:
            response = self._client.get(url, params=params)
        except httpx.HTTPError as error:
            raise ExternalServiceError("No pude conectar con Geoapify.") from error
        if response.status_code != 200:
            raise ExternalServiceError(f"Geoapify respondió con error {response.status_code}.")
        try:
            return response.json()
        except ValueError as error:
            raise ExternalServiceError("Geoapify devolvió una respuesta inesperada.") from error


def _matches(query: str, name: str, rank: dict) -> bool:
    """Si el resultado es el lugar pedido: con confianza alta, sí; si no, solo si su nombre tiene
    todas las palabras propias de [query] (las que no son genéricas como "droguería" o "de")."""
    if rank.get("confidence", 1) >= MIN_CONFIDENCE:
        return True
    distinctive = [word for word in plain_words(query) if word not in _GENERIC_WORDS]
    found = set(plain_words(name))
    return bool(distinctive) and all(word in found for word in distinctive)


def _place_name(kind: PlaceKind, properties: dict) -> str:
    """El nombre del lugar; sin nombre, el tipo y su calle ("Droguería en Carrera 26")."""
    if properties.get("name"):
        return properties["name"]
    label = PLACE_KIND_LABELS[kind][1]
    street = properties.get("street")
    return f"{label} en {street}" if street else label
