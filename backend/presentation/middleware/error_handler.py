from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from domain.errors import (
    DomainError,
    ExternalServiceError,
    NotFoundError,
    QuotaExhaustedError,
    ValidationError,
)

# Se toma la primera clase que coincide: las subclases van antes que su padre.
_STATUS = {
    NotFoundError: 404,
    ValidationError: 422,
    QuotaExhaustedError: 429,
    ExternalServiceError: 502,
}


def _body(code: str, message: str) -> dict:
    return {"error": {"code": code, "message": message}}


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(DomainError)
    async def domain_error_handler(_: Request, exc: DomainError):
        status = next((s for cls, s in _STATUS.items() if isinstance(exc, cls)), 400)
        body = _body(exc.code, exc.message)
        retry_after = getattr(exc, "retry_after_s", None)
        if retry_after is None:
            return JSONResponse(status_code=status, content=body)
        body["error"]["retry_after_s"] = retry_after
        return JSONResponse(status_code=status, content=body, headers={"Retry-After": str(retry_after)})

    @app.exception_handler(Exception)
    async def unexpected_error_handler(_: Request, __: Exception):
        return JSONResponse(
            status_code=500,
            content=_body("INTERNAL_ERROR", "Ocurrió un error inesperado."),
        )
