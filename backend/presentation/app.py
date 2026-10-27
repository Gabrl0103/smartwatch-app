from fastapi import FastAPI

from presentation.middleware.error_handler import register_error_handlers
from presentation.routers import health, places, routes, voice


def create_app() -> FastAPI:
    app = FastAPI(title="Rumbo API")
    register_error_handlers(app)
    app.include_router(health.router)
    app.include_router(voice.router)
    app.include_router(places.router)
    app.include_router(routes.router)
    return app
