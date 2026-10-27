import httpx
import pytest

from domain.errors import ExternalServiceError
from domain.models.route import Coordinates
from infrastructure.geocoding.geoapify_geocoder import GeoapifyGeocoder


def make_geocoder(handler, key="k"):
    client = httpx.Client(transport=httpx.MockTransport(handler))
    return GeoapifyGeocoder(key, client)


def test_search_returns_first_place_and_sends_params():
    seen = {}

    def handler(request):
        seen["params"] = dict(request.url.params)
        result = {"name": "Parque Bolívar", "formatted": "Parque Bolívar, Pasto", "lat": 1.2, "lon": -77.3}
        return httpx.Response(200, json={"results": [result]})

    place = make_geocoder(handler).search("parque bolívar", Coordinates(1.5, -77.5))
    assert place.name == "Parque Bolívar"
    assert place.coordinates == Coordinates(1.2, -77.3)
    assert seen["params"]["text"] == "parque bolívar"
    assert seen["params"]["limit"] == "1"
    assert seen["params"]["format"] == "json"
    assert seen["params"]["filter"] == "countrycode:co"
    assert seen["params"]["bias"] == "proximity:-77.5,1.5"
    assert seen["params"]["apiKey"] == "k"


def test_search_without_location_biases_to_pasto_center():
    seen = {}

    def handler(request):
        seen["bias"] = request.url.params["bias"]
        return httpx.Response(200, json={"results": []})

    make_geocoder(handler).search("parque")
    assert seen["bias"] == "proximity:-77.2811,1.2136"


def test_search_uses_formatted_when_name_missing():
    result = {"formatted": "Calle 18 #20, Pasto", "lat": 1.0, "lon": 2.0}
    geocoder = make_geocoder(lambda r: httpx.Response(200, json={"results": [result]}))
    assert geocoder.search("calle 18").name == "Calle 18 #20, Pasto"


def test_search_without_results_returns_none():
    geocoder = make_geocoder(lambda r: httpx.Response(200, json={"results": []}))
    assert geocoder.search("lugar inexistente") is None


def test_http_error_raises_external_service_error():
    geocoder = make_geocoder(lambda r: httpx.Response(401, json={"message": "Invalid apiKey"}))
    with pytest.raises(ExternalServiceError):
        geocoder.search("parque")


def test_connection_error_raises_external_service_error():
    def handler(request):
        raise httpx.ConnectError("sin red")

    with pytest.raises(ExternalServiceError):
        make_geocoder(handler).search("parque")


def test_unexpected_body_raises_external_service_error():
    geocoder = make_geocoder(lambda r: httpx.Response(200, json={"otra": 1}))
    with pytest.raises(ExternalServiceError):
        geocoder.search("parque")
