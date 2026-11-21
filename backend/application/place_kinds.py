"""Reconoce cuándo el destino es un tipo de lugar y no un lugar con nombre."""

import re
import unicodedata

from domain.models.place_kind import PlaceKind

# Palabras de cada tipo (sin tildes, en singular y plural). Varias palabras se buscan juntas.
_KEYWORDS: dict[PlaceKind, tuple[str, ...]] = {
    PlaceKind.PHARMACY: ("drogueria", "droguerias", "farmacia", "farmacias", "botica"),
    PlaceKind.HOSPITAL: ("hospital", "hospitales", "urgencias"),
    PlaceKind.CLINIC: ("clinica", "clinicas", "centro de salud", "consultorio"),
    PlaceKind.ATM: ("cajero", "cajeros", "cajero automatico", "cajeros automaticos"),
    PlaceKind.BANK: ("banco", "bancos"),
    PlaceKind.SUPERMARKET: ("supermercado", "supermercados", "mercado"),
    PlaceKind.STORE: ("tienda", "tiendas"),
    PlaceKind.BAKERY: ("panaderia", "panaderias"),
    PlaceKind.RESTAURANT: ("restaurante", "restaurantes", "lugar para comer"),
    PlaceKind.CAFE: ("cafeteria", "cafeterias", "cafe"),
    PlaceKind.TOILET: ("bano", "banos", "bano publico", "banos publicos"),
    PlaceKind.PARK: ("parque", "parques"),
    PlaceKind.BUS_STOP: ("parada de bus", "paradero", "parada", "parada de buses"),
    PlaceKind.POLICE: ("policia", "estacion de policia", "cai"),
    PlaceKind.GAS_STATION: ("gasolinera", "estacion de gasolina", "bomba de gasolina"),
}

# Lo que acompaña a un tipo sin volverlo un nombre: "la droguería más cercana", "un cajero por aquí".
_FILLER = {
    "a", "al", "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "mas",
    "cercano", "cercana", "cercanos", "cercanas", "cerca", "proximo", "proxima", "alguna",
    "algun", "alguno", "aqui", "por", "aca", "mi", "llevame", "lleveme", "ir", "quiero",
}


def _words(text: str) -> list[str]:
    plain = unicodedata.normalize("NFD", text.lower())
    plain = "".join(c for c in plain if unicodedata.category(c) != "Mn")
    return re.findall(r"[a-z0-9]+", plain)


def place_kind_of(query: str) -> PlaceKind | None:
    """El tipo de lugar si [query] es solo eso ("droguería más cercana", "un cajero"); None si
    lleva un nombre ("Parque Bolívar", "Banco de Bogotá"), que se busca como lugar."""
    words = _words(query)
    for kind, keywords in _KEYWORDS.items():
        for keyword in sorted(keywords, key=len, reverse=True):
            key = keyword.split()
            for start in range(len(words) - len(key) + 1):
                if words[start : start + len(key)] == key:
                    rest = words[:start] + words[start + len(key) :]
                    if all(word in _FILLER for word in rest):
                        return kind
    return None
