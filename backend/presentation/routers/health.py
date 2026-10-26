from fastapi import APIRouter

from application.use_cases.check_database_health import check_database_health
from infrastructure.config.container import get_database_health

router = APIRouter()


@router.get("/health")
def health():
    return {"status": "ok"}


@router.get("/health/db")
def health_db():
    check_database_health(get_database_health())
    return {"status": "ok", "database": "connected"}
