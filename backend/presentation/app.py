from fastapi import FastAPI

from presentation.middleware.error_handler import register_error_handlers
from presentation.routers import health


def create_app() -> FastAPI:
    app = FastAPI(title="Rumbo API")
    register_error_handlers(app)
    app.include_router(health.router)
    return app
