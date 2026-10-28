from fastapi import APIRouter, Response

from application.use_cases.manage_saved_places import (
    add_saved_place,
    delete_saved_place,
    list_saved_places,
)
from infrastructure.config.container import get_saved_place_repository
from presentation.dto.saved_places import SavedPlaceRequest, SavedPlaceResponse

router = APIRouter(prefix="/saved-places")


@router.get("", response_model=list[SavedPlaceResponse])
def list_all():
    places = list_saved_places(get_saved_place_repository())
    return [SavedPlaceResponse.from_domain(place) for place in places]


@router.post("", response_model=SavedPlaceResponse, status_code=201)
def add(body: SavedPlaceRequest):
    place = add_saved_place(get_saved_place_repository(), body.name, body.coordinates())
    return SavedPlaceResponse.from_domain(place)


@router.delete("/{place_id}", status_code=204)
def delete(place_id: int):
    delete_saved_place(get_saved_place_repository(), place_id)
    return Response(status_code=204)
