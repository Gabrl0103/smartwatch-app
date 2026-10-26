# CLAUDE.md — contexto para Claude Code

Proyecto: **Rumbo**, app Wear OS de navegación por vibraciones para personas ciegas/baja visión. Proyecto académico, 1 mes. Lee `docs/REQUIREMENTS.md` antes de cambiar algo y actualízalo (con changelog) en cada sesión.

## Arquitectura
- Clean Architecture, 4 capas: `domain` (modelos, errores, puertos), `application` (casos de uso), `infrastructure` (APIs externas, BD, config, composition root), `presentation` (UI/routers/DTOs).
- Las dependencias apuntan hacia adentro: `domain` no importa nada de otras capas.
- Los casos de uso dependen de puertos (interfaces), nunca de implementaciones. Las implementaciones se eligen solo en `infrastructure/config/container.py`.
- SOLID, DRY, KISS. Soluciones simples; funciones planas antes que jerarquías de clases cuando alcance.
- Errores de dominio propios (`NotFoundError`, `ValidationError`) + middleware global en el backend.
- La navegación crítica (GPS, vibración, giro/desvío/llegada) se calcula en el reloj, sin depender de la IA ni de la red.

## Stack
- Reloj: Kotlin + Jetpack Compose for Wear OS (emulador, formato cuadrado redondeado).
- Backend: FastAPI + SQLAlchemy + PostgreSQL. Rutas a pie y geocodificación: Geoapify (modo walk). Voz/IA: Gemini API. TTS: `TextToSpeech` de Android.
- Todo en capas gratis ($0). Despliegue del backend en Render.

## Convenciones
- Nombres de clases, variables, archivos y commits en inglés. Todo texto visible al usuario en español.
- Secretos solo en `.env` (ignorado por git). Se versiona solo `.env.example`.
- Tests para la lógica de domain y application.
- Commits pequeños (`feat`, `fix`, `docs`, `chore`), push frecuente.

## Diseño
- Tokens: fondo `#0B0E14`, superficie `#151922`, texto `#F5F7FA`, primario `#4F8EFF`. Estados: en ruta `#4F8EFF`, giro próximo `#FFC857`, desviado `#FF8A3D`, sin señal `#8A93A6`, llegada `#3DDC84`. Fuentes: Poppins (títulos), Inter (texto).
- Nunca comunicar algo solo con color. Elementos táctiles ≥ 48 px.

## Comandos (backend, desde `backend/`)
- Correr: `uvicorn main:app --reload`
- Tests: `pytest`

## Estado
Fase 1 en curso: setup de entorno Wear OS + backend base. Ver fases en `docs/REQUIREMENTS.md`.
