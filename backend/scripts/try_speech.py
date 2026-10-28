"""Prueba real de la voz de Gemini. No imprime la clave.

Uso, desde backend/:
  python scripts/try_speech.py "Llegaste a tu destino." prueba.wav
      Genera la frase y la guarda como WAV para escucharla.
  python scripts/try_speech.py --verify
      Genera 2 frases, transcribe cada WAV con GEMINI_MODEL y comprueba que el audio
      contiene solo la frase (sin instrucciones leídas). Sale con código 1 si alguna falla.
  python scripts/try_speech.py --voices es-US
      Lista las voces prebuilt de Gemini para ese idioma (para elegir VOICE_NAME).

Lee GEMINI_API_KEY, GEMINI_MODEL y GEMINI_TTS_MODEL del entorno o de backend/.env.
"""

import difflib
import re
import sys
import unicodedata
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from domain.errors import ExternalServiceError  # noqa: E402
from infrastructure.config.container import (  # noqa: E402
    get_speech_synthesizer,
    get_voice_assistant,
)
from infrastructure.config.settings import load_settings  # noqa: E402

VERIFY_PHRASES = [
    "En cincuenta metros, gira a la izquierda.",
    "Te saliste de la ruta. Recalculando.",
]
# Por debajo de esta similitud el audio dice algo más (o algo distinto) que la frase.
MIN_SIMILARITY = 0.85
VOICES_URL = "https://generativelanguage.googleapis.com/v1beta/voices"

_UNITS = (
    "cero uno dos tres cuatro cinco seis siete ocho nueve diez once doce trece catorce quince "
    "dieciseis diecisiete dieciocho diecinueve veinte veintiuno veintidos veintitres veinticuatro "
    "veinticinco veintiseis veintisiete veintiocho veintinueve"
).split()
_TENS = "_ _ _ treinta cuarenta cincuenta sesenta setenta ochenta noventa".split()
_HUNDREDS = (
    "_ ciento doscientos trescientos cuatrocientos quinientos seiscientos setecientos "
    "ochocientos novecientos"
).split()


def number_in_words(n: int) -> str:
    """0..999 en palabras sin tildes ("cincuenta", "ciento cinco"); si no, en cifras."""
    if not 0 <= n <= 999:
        return str(n)
    if n < 30:
        return _UNITS[n]
    if n < 100:
        return _TENS[n // 10] + ("" if n % 10 == 0 else f" y {_UNITS[n % 10]}")
    if n == 100:
        return "cien"
    return _HUNDREDS[n // 100] + ("" if n % 100 == 0 else f" {number_in_words(n % 100)}")


def normalize(text: str) -> str:
    """Minúsculas, sin tildes ni signos y con los números en palabras ("50" = "cincuenta"),
    para comparar lo dicho con lo esperado aunque la transcripción use cifras."""
    text = unicodedata.normalize("NFKD", text.lower())
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = re.sub(r"(\d+)[.,](\d+)", r"\1 coma \2", text)
    text = re.sub(r"\d+", lambda m: f" {number_in_words(int(m.group()))} ", text)
    return " ".join(re.sub(r"[^a-z ]", " ", text).split())


def similarity(expected: str, heard: str) -> float:
    return difflib.SequenceMatcher(None, normalize(expected), normalize(heard)).ratio()


def generate(text: str, output: Path) -> None:
    print(f"Modelo: {load_settings().gemini_tts_model}")
    output.write_bytes(get_speech_synthesizer().synthesize(text))
    print(f"Audio guardado en {output} ({output.stat().st_size} bytes).")


def verify() -> bool:
    print(f"Voz: {load_settings().gemini_tts_model}; transcribe: {load_settings().gemini_model}")
    all_ok = True
    for phrase in VERIFY_PHRASES:
        audio = get_speech_synthesizer().synthesize(phrase)
        heard = get_voice_assistant().interpret_audio(audio, "audio/wav").text
        score = similarity(phrase, heard)
        ok = score >= MIN_SIMILARITY
        all_ok = all_ok and ok
        print(f"[{'OK' if ok else 'FALLA'}] {score:.2f}\n  esperado: {phrase}\n  oído:     {heard}")
    return all_ok


def list_voices(language_code: str) -> None:
    response = httpx.get(
        VOICES_URL,
        params={"language_code": language_code, "type": "prebuilt", "page_size": 50},
        headers={"x-goog-api-key": load_settings().gemini_api_key},
        timeout=30,
    )
    if response.status_code != 200:
        sys.exit(f"Error: Gemini respondió {response.status_code} al listar voces.")
    for voice in response.json().get("voices", []):
        print({key: value for key, value in voice.items() if key != "sampleAudioUri"})


def main() -> None:
    args = sys.argv[1:]
    try:
        if args[:1] == ["--verify"]:
            sys.exit(0 if verify() else 1)
        if args[:1] == ["--voices"]:
            list_voices(args[1] if len(args) > 1 else "es-US")
            return
        text = args[0] if args else "Llegaste a tu destino."
        generate(text, Path(args[1] if len(args) > 1 else "prueba_voz.wav"))
    except ExternalServiceError as error:
        sys.exit(f"Error: {error.message}")


if __name__ == "__main__":
    main()
