"""Prueba real de la voz de Gemini: genera una frase y la guarda como WAV para escucharla.

Uso, desde backend/: python scripts/try_speech.py "En cincuenta metros, gira a la izquierda." prueba.wav
Lee GEMINI_API_KEY y GEMINI_TTS_MODEL del entorno o de backend/.env. No imprime la clave.
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from domain.errors import ExternalServiceError  # noqa: E402
from infrastructure.config.container import get_speech_synthesizer  # noqa: E402
from infrastructure.config.settings import load_settings  # noqa: E402


def main() -> None:
    text = sys.argv[1] if len(sys.argv) > 1 else "Llegaste a tu destino."
    output = Path(sys.argv[2] if len(sys.argv) > 2 else "prueba_voz.wav")
    print(f"Modelo: {load_settings().gemini_tts_model}")
    try:
        audio = get_speech_synthesizer().synthesize(text)
    except ExternalServiceError as error:
        sys.exit(f"Error: {error.message}")
    output.write_bytes(audio)
    print(f"Audio guardado en {output} ({len(audio)} bytes).")


if __name__ == "__main__":
    main()
