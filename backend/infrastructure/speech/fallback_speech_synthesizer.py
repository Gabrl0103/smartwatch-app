import time
from collections.abc import Callable

from domain.errors import ExternalServiceError, QuotaExhaustedError
from domain.ports.speech_synthesizer import SpeechSynthesizerPort

# Si el 429 no dice cuánto esperar: casi siempre es el límite por minuto de Gemini.
DEFAULT_PAUSE_S = 60


class FallbackSpeechSynthesizer:
    """Pide la frase a [primary] y, si falla, a [fallback]. Tras una cuota agotada no vuelve a
    pedirle a [primary] hasta que pase la espera que indicó, así cada frase no paga un 429.
    Si también falla el respaldo, sube su error."""

    def __init__(
        self,
        primary: SpeechSynthesizerPort,
        fallback: SpeechSynthesizerPort,
        clock: Callable[[], float] = time.monotonic,
    ):
        self._primary = primary
        self._fallback = fallback
        self._clock = clock
        self._primary_paused_until = 0.0

    def synthesize(self, text: str) -> bytes:
        if self._clock() >= self._primary_paused_until:
            try:
                return self._primary.synthesize(text)
            except QuotaExhaustedError as error:
                wait_s = error.retry_after_s if error.retry_after_s is not None else DEFAULT_PAUSE_S
                self._primary_paused_until = self._clock() + wait_s
            except ExternalServiceError:
                pass
        return self._fallback.synthesize(text)
