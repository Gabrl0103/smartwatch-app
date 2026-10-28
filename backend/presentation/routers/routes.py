from fastapi import APIRouter

from application.use_cases.create_route import create_route
from application.use_cases.save_history import save_active_route
from infrastructure.config.container import get_route_repository, get_routing_provider
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
    route_id = save_active_route(get_route_repository, route)
    return RouteResponse.from_domain(route, route_id)
