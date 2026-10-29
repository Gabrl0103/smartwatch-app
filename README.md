# Rumbo — navegación por vibraciones para smartwatch

App para Wear OS que guía a personas ciegas o con baja visión con GPS, vibraciones y asistente de voz.
Documento de requisitos vivo: [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md).

## Estructura
- `backend/` — API FastAPI (Clean Architecture: domain / application / infrastructure / presentation)
- `watchapp/` — app de Wear OS (Kotlin + Jetpack Compose)
- `database/` — migraciones
- `docs/` — documentación

## Correr el backend (Windows, PowerShell)
```
cd backend
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn main:app --reload
```
Abrir http://127.0.0.1:8000/health → `{"status":"ok"}` y http://127.0.0.1:8000/docs.

## Tests del backend
```
cd backend
pytest
```

## Tests del reloj
```
cd watchapp
gradlew testDebugUnitTest
```
Incluyen la máquina de estados recorriendo los GPX de `docs/demo`. Pruebas manuales para la demo:
[docs/PRUEBAS.md](docs/PRUEBAS.md).

## Variables de entorno
Copiar `backend/.env.example` a `backend/.env` y rellenar (nunca versionar `.env`).
