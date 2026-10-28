from fastapi import APIRouter

from application.use_cases.record_vibration_event import record_vibration_event
from infrastructure.config.container import get_route_repository, get_vibration_event_repository
from presentation.dto.vibration_events import VibrationEventRequest, VibrationEventResponse

router = APIRouter(prefix="/vibration-events")


@router.post("", response_model=VibrationEventResponse, status_code=201)
def record(body: VibrationEventRequest):
    event_id = record_vibration_event(
        get_vibration_event_repository(),
        get_route_repository(),
        body.pattern,
        body.route_id,
        body.step_order,
    )
    return VibrationEventResponse(id=event_id)
