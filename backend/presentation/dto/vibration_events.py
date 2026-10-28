from pydantic import BaseModel


class VibrationEventRequest(BaseModel):
    pattern: str | None = None
    route_id: int | None = None
    step_order: int | None = None


class VibrationEventResponse(BaseModel):
    id: int
