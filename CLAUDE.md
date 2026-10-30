# CLAUDE.md — contexto para Claude Code

Proyecto: **Rumbo**, app Wear OS de navegación por vibraciones para personas ciegas/baja visión. Proyecto académico, 1 mes. Lee `docs/REQUIREMENTS.md` antes de cambiar algo y actualízalo (con changelog) en cada sesión.

## Arquitectura
- Clean Architecture, 4 capas: `domain` (modelos, errores, puertos), `application` (casos de uso), `infrastructure` (APIs externas, BD, config, composition root), `presentation` (UI/routers/DTOs).
- Las dependencias apuntan hacia adentro: `domain` no importa nada de otras capas.
- Los casos de uso dependen de puertos (interfaces), nunca de implementaciones. Las implementaciones se eligen solo en el composition root: `backend/infrastructure/config/container.py` y, en el reloj, `di/AppContainer.kt` (uno por proceso, compartido por la actividad y el servicio de navegación).
- SOLID, DRY, KISS. Soluciones simples; funciones planas antes que jerarquías de clases cuando alcance.
- Errores de dominio propios (`NotFoundError`, `ValidationError`) + middleware global en el backend; en el reloj, `AppError` y sus subclases llevan el mensaje en español listo para mostrar y decir.
- La navegación crítica (GPS, vibración, giro/desvío/llegada) se calcula en el reloj, sin depender de la IA ni de la red. `domain` y `application` del reloj son Kotlin puro (sin `android.*`, sin `android.util.Log`: se usa el puerto `Logger`), así se prueban con JUnit.

## Stack
- Reloj: Kotlin + Jetpack Compose for Wear OS (emulador Wear OS Large Round, pantalla redonda de 454 px, API 34).
- Backend: FastAPI + SQLAlchemy + PostgreSQL (Neon). Rutas a pie y geocodificación: Geoapify (modo walk). Voz/IA: Gemini API. Desplegado en Render (`https://smartwatch-app.onrender.com`, plan gratis: duerme tras ~15 min sin uso).
- Voz → texto: el reloj graba audio AAC y lo envía a `POST /voice/interpret`, donde Gemini lo transcribe e interpreta (el emulador no trae reconocimiento de voz).
- Texto → voz: TextToSpeech del reloj (principal) y Gemini TTS por `POST /speech` (respaldo). El emulador API 34 no trae motor TTS, así que ahí habla Gemini; si responde 429 (cuota gratis agotada) la app sigue solo con vibraciones.
- Todo en capas gratis ($0).

## Convenciones
- Nombres de clases, variables y archivos en inglés. Todo texto visible o hablado al usuario en español.
- Secretos solo en `.env` (ignorado por git). Se versiona solo `.env.example`.
- Tests para la lógica de domain y application (y el ViewModel del reloj).
- Commits en español, sin prefijos (`feat`, `fix`, `docs`), muy pequeños salvo que se pida otra cosa; meta: 100+ commits. Push frecuente.

## Diseño
- Tokens: fondo `#0B0E14`, superficie `#151922`, texto `#F5F7FA`, primario `#4F8EFF`. Estados: en ruta `#4F8EFF`, giro próximo `#FFC857`, desviado `#FF8A3D`, sin señal `#8A93A6`, llegada `#3DDC84`. Fuentes: Poppins (títulos), Inter (texto).
- Nunca comunicar algo solo con color. Elementos táctiles ≥ 48 px (los botones miden 52 dp). Todas las pantallas usan `RumboScreen`.

## Comandos
- Backend (desde `backend/`, con `venv`): correr `uvicorn main:app --reload`; tests `pytest`; migraciones `python scripts/migrate.py`.
- Reloj (desde `watchapp/`): tests `gradlew testDebugUnitTest`; instalar `gradlew installDebug`; lint `gradlew lintDebug`; instrumentados en emulador `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.rumbo.watchapp.presentation`. Si `gradlew clean` falla por archivos de lint bloqueados (Windows), usar `cleanTestDebugUnitTest` o `gradlew --stop`.
- Probar la voz en el emulador: su micrófono no capta los parlantes del PC. Para pruebas, un parche temporal (sin commit) en `MediaAudioRecorder.stop()` que devuelva un WAV puesto con `adb push` en `files/`; Gemini acepta WAV aunque llegue como `audio/aac`. Frases en español: `POST /speech` (gasta cuota de Gemini TTS).
- Capturas (emulador abierto, desde la raíz): `bash tools/capture_screens.sh`. `adb` no está en el PATH: `ADB=$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe bash tools/capture_screens.sh`. El script regenera todas las PNG: las que solo cambian por la hora se restauran con `git checkout`.
- Logcat: `adb logcat -s Nav Vibracion Red` (estados de navegación, patrones de vibración y peticiones al backend).
- Demo con GPX (`docs/demo`): poner la posición antes de pedir la ruta (`adb emu geo fix -77.278419 1.214997`, primero la longitud; para repetirla, reabrir la app antes); la app ya no inventa el origen. "Sin señal" en el emulador: apagar la ubicación 15 s (`adb shell cmd location set-location-enabled false` y luego `true`); el hueco del GPX no basta.

## Estado (2026-10-30, REQUIREMENTS v0.43)
Fases 1–3 completas; fase 4 (asistente y cierre) en curso.
- Hecho: voz → destino → confirmar (con lugares guardados), ruta a pie, navegación con GPS y vibraciones (giro próximo y maniobra en dos etapas, desvío con recálculo, sin señal, llegada) que se distinguen solo por ritmo en relojes sin control de intensidad, pantalla "Vibraciones" para probarlas (secuencia de demostración y ajustes de intensidad y duración), voz hablada (con barras animadas mientras habla), guía por etapas para personas ciegas (lejos, preparación y ahora, con calle, giros suaves y cerrados, media vuelta, rotonda con salida, "Luego," y aviso del destino con su lado; audio de la siguiente etapa precargado), pasos que Geoapify no clasifica ("Tome las escaleras.": nunca se omiten, se dicen con su texto y "Atención:" si hay peligro, con vibración propia), repetir con doble toque en cualquier parte de En ruta, pantalla Escuchando con barras que siguen la voz real y corte automático al terminar de hablar (la voz de la app espera mientras graba), asistente conversacional (RF9: `POST /voice/ask` con el contexto de la ruta; pantalla Asistente y botón "Preguntar" en ruta), órdenes de voz (RF13: `ExecuteVoiceCommand`, micrófono en Inicio, Confirmar y en ruta; guardar destino, repetir, cancelar, cuánto falta y si está guardado, con vibración "Hecho" o "Error" y la voz diciendo el resultado; "Guardado ✓" en la ruta y aviso al iniciar), UI redonda con TalkBack, servicio en primer plano (sigue con la pantalla apagada), permisos pedidos al necesitarlos con pantalla de explicación, errores de red/GPS en español con un reintento, "Continuar ruta anterior" si el proceso muere, base de datos con historial, rutas verificadas en mapa (`tools/preview_route.py`) con pasos limpios y simulación realista del GPS (`tools/route_to_gpx.py`).
- Pruebas: 186 pytest, 389 JUnit (incluye la máquina de estados con la ruta real y los tres GPX realistas de `docs/demo`, y 30 recorridos con ruido) y 16 instrumentadas (doble toque, paso desconocido y órdenes de voz); pruebas manuales en `docs/PRUEBAS.md`.
- Pendiente: redesplegar Render (reloj primero: uno viejo no entiende las maniobras ni las intenciones nuevas), probar las órdenes de voz de punta a punta (C1–C9), probar en un reloj real la voz de las respuestas y la pausa al grabar (A3, A6; en el emulador no hay voz propia y la cuota de Gemini TTS se agota), pruebas de accesibilidad en reloj real (incluye el doble toque con TalkBack, R14), video de sustentación y confirmar los requisitos del profesor.
