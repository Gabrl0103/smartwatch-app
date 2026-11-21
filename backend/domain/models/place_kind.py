from enum import Enum


class PlaceKind(str, Enum):
    """Tipos de lugar que se piden sin nombre ("llévame a la droguería más cercana")."""

    PHARMACY = "pharmacy"
    HOSPITAL = "hospital"
    CLINIC = "clinic"
    ATM = "atm"
    BANK = "bank"
    SUPERMARKET = "supermarket"
    STORE = "store"
    BAKERY = "bakery"
    RESTAURANT = "restaurant"
    CAFE = "cafe"
    TOILET = "toilet"
    PARK = "park"
    BUS_STOP = "bus_stop"
    POLICE = "police"
    GAS_STATION = "gas_station"


# Cómo se dice cada uno en una frase ("No encontré una droguería cerca") y como nombre del destino.
PLACE_KIND_LABELS: dict[PlaceKind, tuple[str, str]] = {
    PlaceKind.PHARMACY: ("una droguería", "Droguería"),
    PlaceKind.HOSPITAL: ("un hospital", "Hospital"),
    PlaceKind.CLINIC: ("una clínica", "Clínica"),
    PlaceKind.ATM: ("un cajero", "Cajero"),
    PlaceKind.BANK: ("un banco", "Banco"),
    PlaceKind.SUPERMARKET: ("un supermercado", "Supermercado"),
    PlaceKind.STORE: ("una tienda", "Tienda"),
    PlaceKind.BAKERY: ("una panadería", "Panadería"),
    PlaceKind.RESTAURANT: ("un restaurante", "Restaurante"),
    PlaceKind.CAFE: ("una cafetería", "Cafetería"),
    PlaceKind.TOILET: ("un baño", "Baño"),
    PlaceKind.PARK: ("un parque", "Parque"),
    PlaceKind.BUS_STOP: ("una parada de bus", "Parada de bus"),
    PlaceKind.POLICE: ("una estación de policía", "Policía"),
    PlaceKind.GAS_STATION: ("una gasolinera", "Gasolinera"),
}
