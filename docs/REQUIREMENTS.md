# REQUIREMENTS.md — App de navegación para smartwatch (usuarios ciegos/baja visión)

> Documento vivo. Se actualiza en cada sesión de trabajo. Ver changelog al final.

## 1. Alcance

- Proyecto académico, plazo de 1 mes.
- Objetivo: app standalone para Wear OS que guía a una persona ciega o con baja visión usando GPS + patrones de vibración, con **reconocimiento de voz y un asistente conversacional (IA)** como parte esencial, y audio (TTS) para las respuestas.
- Entregable final: app funcional en **emulador** Wear OS (no se requiere reloj físico) y backend desplegado en servicio gratuito.
- Plataforma: solo Wear OS. Apple Watch queda fuera (requiere Mac/Xcode y reescribir en Swift).
- Sin app companion en celular: todo vive en el reloj.
- Sin autenticación: un único usuario fijo.
- Backend real + API de rutas + base de datos real.
- Requisitos del profesor: pendientes de confirmar.

## 2. Stack y justificación

| Capa | Tecnología | Por qué |
|---|---|---|
| Reloj | Kotlin + Jetpack Compose for Wear OS | Standalone en emulador gratuito de Android Studio |
| Backend | FastAPI (Python) | Curva baja, docs automáticas, fácil de testear |
| Rutas a pie + geocodificación | Geoapify (modo `walk`) | Gratis con registro (3.000 créditos/día); rutas a pie y geocodificación en un solo servicio |
| Voz → texto, intención y asistente | Gemini API (capa gratis, AI Studio) | Recibe el audio grabado en el reloj, lo transcribe, extrae la intención y responde; sin tarjeta de crédito. Necesario porque el emulador Wear OS no trae reconocimiento de voz |
| Texto → voz | Pendiente: Google Text-to-Speech instalado en el emulador o Gemini TTS desde el backend | El emulador Wear OS no trae motor TTS |
| Base de datos | PostgreSQL (Render o Supabase, free tier) | Gratis |
| Hosting backend | Render (free tier) | $0; el servidor duerme tras inactividad |
| Tests | pytest (backend) / JUnit (reloj) | Solo domain y application |

Costo total: **$0**.

Nota: el servidor demo de OSRM se descartó porque no está confirmado que ofrezca perfil a pie de forma fiable. OpenRouteService se reemplazó por Geoapify porque openrouteservice.org está caído.

## 3. Arquitectura — Clean Architecture (4 capas)

Dependencias apuntan hacia adentro: `domain` no conoce a nadie. Patrón Repository/puertos: los casos de uso dependen de interfaces, las implementaciones se cambian solo en el composition root.

**Reloj (watchapp)**
- `domain`: models (Route, RouteStep, VibrationPattern, VoiceIntent), errors (RouteNotFoundError, GpsUnavailableError, VoiceNotUnderstoodError), ports (RoutingRepository, LocationProvider, VibrationController, SpeechRecorder, SpeechSynthesizer, VoiceAssistantRepository)
- `application`: casos de uso (ProcessVoiceCommand, ConfirmDestination, CalculateRoute, TrackUserPosition, EmitNavigationInstruction, DetectDeviation, DetectArrival, AskAssistant)
- `infrastructure`: cliente HTTP al backend, AndroidLocationProvider (FusedLocationProviderClient), AndroidVibrationController (Vibrator API), AndroidAudioRecorder (MediaRecorder, AAC), servicio TTS (pendiente de definir), composition root (DI manual)
- `presentation`: pantallas Compose + ViewModels

**Backend**
- `domain`: models (Route, RouteStep, VoiceCommand), errors, ports (RoutingProviderPort, GeocodingPort, VoiceAssistantPort)
- `application`: casos de uso (InterpretVoiceCommand, GeocodeDestination, CreateRoute, AskAssistant)
- `infrastructure`: adaptador Geoapify, adaptador Gemini, repositorio PostgreSQL (SQLAlchemy), config/composition root
- `presentation`: routers FastAPI, DTOs (Pydantic), middleware global de errores

**Regla de seguridad**: la navegación crítica (GPS, vibraciones, detección de giro/desvío/llegada) se calcula en el reloj con reglas locales, sin depender de la IA ni de la latencia de red. La IA solo interviene para entender el destino y responder preguntas.

## 4. Estructura del repositorio

```
smartwatch-nav/
├── backend/
│   ├── domain/{models,errors,ports}/
│   ├── application/use_cases/
│   ├── infrastructure/{routing,geocoding,assistant,database,config}/
│   ├── presentation/{routers,dto}/
│   ├── tests/
│   ├── .env.example
│   └── requirements.txt
├── watchapp/
│   └── app/src/main/java/.../
│       ├── domain/{model,error,port}/
│       ├── application/usecase/
│       ├── infrastructure/{location,vibration,audio,tts,network}/
│       ├── presentation/{screens,viewmodel,theme}/
│       └── di/
├── database/migrations/
├── docs/REQUIREMENTS.md
├── .gitignore
└── README.md
```

## 5. Fuentes de datos

- Ubicación: `FusedLocationProviderClient` (simulada en el emulador con Extended Controls).
- Audio del usuario: micrófono del reloj (en emulador, micrófono del PC), grabado con `MediaRecorder` en AAC y enviado al backend. Prueba del micrófono del emulador superada.
- Rutas a pie y lugares: Geoapify, consumido solo desde el backend.
- Transcripción e interpretación de voz y respuestas del asistente: Gemini API, consumido solo desde el backend.
- Las API keys viven en `backend/.env` (no versionado); se versiona `.env.example`. Variables: `DATABASE_URL`, `GEOAPIFY_API_KEY` (reemplaza a `ORS_API_KEY`), `GEMINI_API_KEY`, `GEMINI_MODEL`.

### Flujo de voz

1. El reloj graba el audio del usuario (`MediaRecorder`, AAC).
2. Lo envía a `POST /voice/interpret`.
3. Gemini transcribe el audio y extrae la intención.
4. El backend responde con el texto y la intención; el reloj confirma o actúa.

### Endpoints del backend

| Método | Ruta | Entrada | Salida |
|---|---|---|---|
| GET | `/health` | — | Estado del servicio |
| GET | `/health/db` | — | Estado de la conexión a la base de datos |
| POST | `/voice/interpret` | Audio crudo en el cuerpo; formato en `Content-Type` (p. ej. `audio/aac`) | `text`, `intent`, `destination`, `question` |

## 6. Modelo de datos

```
User
 - id, nombre (usuario único fijo, sin auth)

Device
 - id, user_id, tipo (wear_os), registrado_en

Route
 - id, user_id, origen (lat/lng), destino (lat/lng), nombre_destino, creado_en,
   estado (activa/completada/cancelada)

RouteStep
 - id, route_id, orden, tipo_maniobra (left/right/straight/arrive),
   distancia_hasta_siguiente, lat/lng del punto

VoiceCommand
 - id, user_id, texto_transcrito, intencion (navigate/ask/cancel/repeat),
   destino_extraido, creado_en

VibrationEvent (log para demo/sustentación)
 - id, route_step_id, patron_emitido, timestamp
```

## 7. Requerimientos funcionales

- RF1: Elegir destino por voz (tocar y hablar) y, como alternativa, desde lugares guardados.
- RF2: Interpretar el comando de voz (intención y destino) y confirmarlo por voz y en pantalla antes de iniciar.
- RF3: Calcular una ruta a pie entre posición actual y destino.
- RF4: Trackear la posición del usuario en tiempo real (GPS).
- RF5: Emitir patrón de vibración según la instrucción (izquierda, derecha, recto, giro próximo, llegada).
- RF6: Detectar desvío de ruta y notificar con patrón distinto.
- RF7: Detectar pérdida de señal GPS y notificar.
- RF8: Detectar llegada y notificar con patrón especial.
- RF9: Asistente conversacional por voz durante la ruta (preguntas sobre el entorno o la ruta), con respuesta hablada.
- RF10: Audio (TTS) opcional para las instrucciones de navegación.

## 8. Requerimientos no funcionales

- NFR1: Accesibilidad — nunca comunicar un estado solo con color; siempre texto grande + vibración/audio. Elementos táctiles ≥ 48 px. Pantallas compatibles con TalkBack.
- NFR2: Funcional en emulador Wear OS sin hardware físico.
- NFR3: Navegación (GPS + vibración) funciona sin internet una vez calculada la ruta.
- NFR4: Backend desplegado gratis; se acepta el retraso del primer request por el "sleep" del free tier.
- NFR5: Secretos solo en `.env`; `.env.example` sí se versiona.
- NFR6: Manejo global de errores (middleware backend, errores de dominio propios).
- NFR7: Privacidad — en la capa gratis de Gemini el contenido enviado puede usarse para mejorar productos de Google; no enviar datos personales reales.
- NFR8: Avisar que es un proyecto académico y no debe ser el único medio de orientación en uso real.

## 9. Pantallas (reloj)

Diseñadas en Claude Design (lienzo "Rumbo — UI del reloj"), en formato cuadrado redondeado (Wear OS Square) y una versión redonda de referencia.

1. Inicio (voz): botón grande de micrófono, "¿A dónde vamos?", acceso a lugares guardados.
2. Escuchando destino.
3. Confirmar destino: nombre, distancia y tiempo a pie; Ir / Cambiar.
4. En ruta.
5. Giro próximo.
6. Desviado.
7. Sin señal GPS.
8. Llegada.
9. Asistente de voz: pregunta del usuario, respuesta hablada y escrita, botón para volver a preguntar.

## 10. Estilo visual — Design tokens

- Tipografía: títulos Poppins (600/700), texto Inter (400/500/700). Escala del reloj: 16 / 20 / 24 / 32 / 40 sp.
- Colores base: fondo `#0B0E14`, superficie `#151922`, texto `#F5F7FA`, texto secundario `#A0A8B8`, primario `#4F8EFF`, secundario `#7C5CFC` (asistente; el chip usa `#9B82FF` para contraste), acento `#FFC857`, borde `#2A2F3A`.
- Escala semántica de estados (siempre con texto): en ruta `#4F8EFF`, giro próximo `#FFC857`, desviado `#FF8A3D`, sin señal GPS `#8A93A6`, llegada `#3DDC84`.
- Formas: radio de pantalla 76 px (cuadrada), botones en píldora, borde fino de 1 px, sin sombras.
- Elementos: anillo de progreso en el borde con la distancia al siguiente giro; indicador del patrón de vibración en cada estado.
- Reglas: contraste mínimo 4.5:1 en texto, un estado por pantalla, poca información por vista.

## 11. Fases

1. **Semana 1 — Setup y prueba de voz**: proyecto Wear OS + emulador, backend "hello world" desplegado en Render + Postgres conectado, y prueba rápida de grabar audio y reproducir TTS en el emulador.
2. **Semana 2 — Voz, destino y ruta**: endpoint de voz (Gemini), geocodificación y ruta a pie (Geoapify), pantallas de inicio, escuchando y confirmar.
3. **Semana 3 — Navegación**: GPS en emulador, motor de vibraciones, detección de giro próximo, desvío, sin señal y llegada.
4. **Semana 4 — Asistente y cierre**: asistente conversacional, audio opcional, pruebas de accesibilidad, documentación, video de sustentación.

## 12. Preguntas abiertas

- Requisitos exactos del profesor (rúbrica, formato de entrega, sustentación).
- TTS: Google Text-to-Speech instalado en el emulador o Gemini TTS desde el backend (el emulador no trae motor TTS).
- Crear cuentas gratuitas: Geoapify (API key) y Google AI Studio (API key de Gemini); confirmar el nombre del modelo de Gemini disponible en la capa gratis.

## 13. Changelog

- **v0.5** (2026-10-26): El reloj se conecta al backend en Render (`https://smartwatch-app.onrender.com`): permiso `INTERNET`, `VoiceApiClient` (HttpURLConnection, sin dependencias nuevas, timeouts de 90 s por el arranque en frío de Render) y modelo `VoiceResult` en el dominio. La pantalla "Prueba de voz" envía la grabación a `POST /voice/interpret` y muestra texto, intención y destino.
- **v0.4** (2026-10-26): Ruteo y geocodificación cambian de OpenRouteService a Geoapify (modo `walk`, 3.000 créditos/día) porque openrouteservice.org está caído; `GEOAPIFY_API_KEY` reemplaza a `ORS_API_KEY`. El emulador Wear OS no trae reconocimiento de voz ni motor TTS: el reloj graba audio (MediaRecorder, AAC) y lo envía a `POST /voice/interpret`, donde Gemini lo transcribe e interpreta; prueba del micrófono del emulador superada. Nuevo endpoint `POST /voice/interpret` (audio crudo, formato en `Content-Type`; devuelve `text`, `intent`, `destination`, `question`). TTS pendiente de definir entre Google Text-to-Speech en el emulador o Gemini TTS desde el backend.
- **v0.2** (2026-10-05): Voz y asistente conversacional como funcionalidad esencial; routing cambia de OSRM a OpenRouteService; se agrega Gemini API (voz/IA) y TTS de Android; nuevos puertos, casos de uso y entidad VoiceCommand; pantallas y design tokens definidos (formato cuadrado redondeado); fases reordenadas; landing page excluida del repositorio documentado.
- **v0.1** (2026-10-03): Alcance, stack, arquitectura Clean Architecture, estructura de repo, modelo de datos, requerimientos, pantallas y fases iniciales.
