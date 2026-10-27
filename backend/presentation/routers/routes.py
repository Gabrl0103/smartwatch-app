from fastapi import APIRouter

from application.use_cases.create_route import create_route
from infrastructure.config.container import get_routing_provider
from presentation.dto.routes import RouteRequest, RouteResponse

router = APIRouter(prefix="/routes")


@router.post("", response_model=RouteResponse)
def create(body: RouteRequest):
    route = create_route(
        get_routing_provider(),
        body.origin.to_domain() if body.origin else None,
        body.destination.to_domain() if body.destination else None,
        body.destination_name,
    )
    return RouteResponse.from_domain(route)
