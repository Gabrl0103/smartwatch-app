from fastapi import FastAPI

from presentation.middleware.error_handler import register_error_handlers
from presentation.routers import (
    devices,
    health,
    places,
    routes,
    saved_places,
    speech,
    vibration_events,
    voice,
)


def create_app() -> FastAPI:
    app = FastAPI(title="Rumbo API")
    register_error_handlers(app)
    app.include_router(health.router)
    app.include_router(voice.router)
    app.include_router(places.router)
    app.include_router(routes.router)
    app.include_router(saved_places.router)
    app.include_router(devices.router)
    app.include_router(vibration_events.router)
    app.include_router(speech.router)
    return app
