"""Errores de dominio propios. Los captura el middleware global."""


class DomainError(Exception):
    code = "DOMAIN_ERROR"

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


class NotFoundError(DomainError):
    code = "NOT_FOUND"


class ValidationError(DomainError):
    code = "VALIDATION_ERROR"


class ExternalServiceError(DomainError):
    code = "EXTERNAL_SERVICE_ERROR"


class QuotaExhaustedError(ExternalServiceError):
    """El servicio externo rechazó la petición por cuota agotada (p. ej. 429 de Gemini).
    `retry_after_s` es cuánto esperar antes de volver a pedir, si se sabe."""

    code = "QUOTA_EXHAUSTED"

    def __init__(self, message: str, retry_after_s: int | None = None):
        super().__init__(message)
        self.retry_after_s = retry_after_s
