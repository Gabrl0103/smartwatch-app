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
| Texto → voz | Gemini TTS desde el backend (`POST /speech`, modelo `gemini-3.8-flash-tts`, capa gratis) | El emulador Wear OS no trae motor TTS; un solo proveedor de IA y una voz igual en cualquier reloj |
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
- `infrastructure`: cliente HTTP al backend, AndroidLocationProvider (FusedLocationProviderClient), AndroidVibrationController (Vibrator API), AndroidAudioRecorder (MediaRecorder, AAC), salida de voz en `infrastructure/tts` (`SpeechOutputImpl` + `AndroidSpeechPlayer` con `POST /speech` y TextToSpeech de respaldo), composition root (DI manual)
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
│   ├── infrastructure/{routing,geocoding,assistant,speech,database,config}/
│   ├── presentation/{routers,dto}/
│   ├── scripts/{migrate.py,try_speech.py}
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
- Voz hablada (TTS): Gemini TTS, consumido solo desde el backend (`POST /speech`).
- Las API keys viven en `backend/.env` (no versionado); se versiona `.env.example`. Variables: `DATABASE_URL`, `GEOAPIFY_API_KEY` (reemplaza a `ORS_API_KEY`), `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_TTS_MODEL` (por defecto `gemini-3.8-flash-tts`; alternativa gratis: `gemini-3.8-flash-lite-tts`).

### Flujo de voz

1. El reloj graba el audio del usuario (`MediaRecorder`, AAC).
2. Lo envía a `POST /voice/interpret`.
3. Gemini transcribe el audio y extrae la intención.
4. El backend responde con el texto y la intención; el reloj confirma o actúa.

### Voz hablada (TTS)

- `POST /speech` recibe `{text}` (máximo 300 caracteres) y responde `audio/wav`.
- El backend llama a `generateContent` del modelo `GEMINI_TTS_MODEL` con `responseModalities: ["AUDIO"]` y la voz prebuilt `Iapetus` (constante `VOICE_NAME`). Se envía solo la frase: los modelos TTS 3.8 leen el texto tal cual, así que cualquier instrucción de estilo en el texto se dice en voz alta. El acento sale de la voz y del idioma del texto; para cambiarlo se elige otra voz (`python scripts/try_speech.py --voices es-US` lista las disponibles).
- Gemini devuelve PCM 16-bit mono (24 kHz por defecto; la frecuencia viene en el `mimeType`) en base64; el backend lo envuelve en WAV con el módulo `wave`.
- Caché en memoria de hasta 100 frases (se descarta la menos usada) para no gastar cuota gratuita en frases repetidas. Los errores no se guardan en caché.
- Errores de Gemini → 502 `EXTERNAL_SERVICE_ERROR`; el 429 (cuota agotada) dice "Se agotó la cuota gratuita de voz de Gemini. Intenta más tarde."
- Prueba real: desde `backend/`, `python scripts/try_speech.py "Llegaste a tu destino." prueba.wav`. Con `--verify` genera 3 frases, las transcribe con `GEMINI_MODEL` y falla si el audio dice algo más que la frase (similitud < 0,85).

### Voz en el reloj

- Puerto `SpeechOutput { speak(text, priority); stop() }` en `domain/port`; prioridades `NORMAL` y `HIGH` (giro, desvío, llegada).
- `SpeechOutputImpl` decide qué suena: una frase nueva cancela la actual, salvo que la nueva sea `NORMAL` y la actual `HIGH` (esa se descarta). Si la voz está apagada no dice nada.
- `AndroidSpeechPlayer` pide el WAV a `POST /speech` (90 s mientras Render despierta; 20 s cuando el backend ya respondió una vez), lo guarda en `cacheDir/speech/v2-<sha256 de "v2:" + texto>.wav` (al cambiar la versión se ignoran y borran los audios anteriores), lo reutiliza si la frase se repite y lo reproduce con `MediaPlayer` (uso "guía de navegación").
- Si falla la red o el servidor, usa `TextToSpeech` de Android en es-CO; si no hay motor o no habla español (el emulador no trae), no dice nada y deja un `Log.w`. La voz nunca bloquea ni detiene la navegación ni las vibraciones.
- Cuándo habla:

| Momento | Frase | Prioridad |
|---|---|---|
| Confirmar | "<destino>, a <distancia>, <minutos> minutos a pie. ¿Vamos?" | normal |
| Tocar "Ir" | "Iniciando ruta. <primera instrucción>" | normal |
| Giro próximo | "En <distancia>, <instrucción>" (p. ej. "En treinta metros, gire a la izquierda hacia la Calle 18.") | alta |
| Maniobra | "Gira a la izquierda." / "Gira a la derecha." | alta |
| Desvío | "Te saliste de la ruta. Recalculando." | alta |
| Sin señal | "Perdí la señal de GPS." | normal |
| Señal recuperada | "Señal recuperada." | normal |
| Llegada | "Llegaste a tu destino." | alta |
| Error en pantalla | El mismo mensaje (incluido "No pude guardar el lugar") | normal |

- Números en palabras hasta 999 ("veintiún minutos", "ciento un metros") y distancias redondeadas para que suenen naturales: de 10 en 10 metros bajo 100 m, de 50 en 50 bajo 1 km y en décimas de kilómetro después ("un kilómetro y medio", "uno coma seis kilómetros"). Los minutos se redondean hacia arriba, igual que en pantalla.
- Si en una misma lectura hay varios eventos, solo se dice el más importante (llegada > desvío > maniobra > giro próximo > sin señal > señal recuperada).

### Endpoints del backend

| Método | Ruta | Entrada | Salida |
|---|---|---|---|
| GET | `/health` | — | Estado del servicio |
| GET | `/health/db` | — | Estado de la conexión a la base de datos |
| POST | `/voice/interpret` | Audio crudo en el cuerpo; formato en `Content-Type` (p. ej. `audio/aac`) | `text`, `intent`, `destination`, `question`. Guarda el comando en `voice_commands` (si el guardado falla, se registra en el log y se responde igual) |
| POST | `/speech` | JSON `{text}` (máximo 300 caracteres) | `audio/wav` con la frase hablada (422 si falta el texto o es muy largo; 502 si Gemini falla o se agota la cuota) |
| GET | `/places/search` | `q` (texto); `lat`, `lng` opcionales para priorizar cercanía (si faltan, centro de Pasto) | `name`, `lat`, `lng` (404 si no hay resultados) |
| POST | `/routes` | JSON `{origin: {lat, lng}, destination: {lat, lng}, destination_name}` | `route_id`, `destination_name`, `distance_m`, `duration_s`, `steps: [{order, maneuver (left/right/straight/arrive), instruction, distance_m, lat, lng}]`, `polyline: [[lat, lng], ...]` (422 si faltan coordenadas o están fuera de rango). Guarda la ruta y sus pasos con estado `active`; si el guardado falla, se registra en el log y `route_id` llega `null` |
| GET | `/saved-places` | — | Lista `[{id, name, lat, lng, created_at}]` ordenada por nombre |
| POST | `/saved-places` | JSON `{name, lat, lng}` | 201 con `{id, name, lat, lng, created_at}` (422 si falta el nombre, pasa de 100 caracteres o las coordenadas no son válidas) |
| DELETE | `/saved-places/{id}` | — | 204 (404 si no existe) |
| POST | `/devices` | JSON `{device_id, type}` (`type` opcional, solo `wear_os`) | `{id, type, registered_at}`. Idempotente: el mismo `device_id` devuelve el registro original |
| POST | `/vibration-events` | JSON `{pattern, route_id?, step_order?}` | 201 con `{id}`. Con `route_id` y `step_order` se enlaza al paso (404 si no existe); si falta alguno, se guarda sin paso |

## 6. Modelo de datos

PostgreSQL (Neon). El esquema se crea con migraciones SQL versionadas en `database/migrations/` (`001_create_tables.sql`, `002_seed_default_user.sql`, ...) que aplica `backend/scripts/migrate.py`. Nombres de tablas y columnas en inglés:

```
users            (usuario único fijo, sin auth; semilla id 1, "Usuario")
 - id, name

devices          (id = UUID generado por el reloj, así registrarlo dos veces no duplica)
 - id, user_id → users, type (wear_os), registered_at

saved_places
 - id, user_id → users, name, lat, lng, created_at

routes
 - id, user_id → users, origin_lat, origin_lng, destination_lat, destination_lng,
   destination_name, created_at, status (active/completed/cancelled)

route_steps      (único por route_id + step_order)
 - id, route_id → routes, step_order, maneuver (left/right/straight/arrive),
   distance_to_next_m, lat, lng

voice_commands
 - id, user_id → users, transcribed_text, intent (navigate/ask/cancel/repeat),
   extracted_destination, created_at

vibration_events (log para demo/sustentación)
 - id, route_step_id → route_steps (nullable), pattern, emitted_at
```

Índices por `user_id` en `devices`, `saved_places`, `routes` y `voice_commands`; por `route_id` en `route_steps` (vía la restricción única) y por `route_step_id` en `vibration_events`.

### Migraciones

- Desde `backend/`: `python scripts/migrate.py` (usa `DATABASE_URL` del entorno o de `backend/.env`).
- Cada archivo `.sql` corre en su propia transacción y queda anotado en `schema_migrations`; correr el script otra vez solo aplica las nuevas. Si una falla, se detiene sin anotarla.
- Los archivos no deben usar `;` ni `--` dentro de textos (el script separa sentencias por `;` y quita comentarios `--`).
- Para agregar un cambio de esquema, crear el siguiente archivo numerado (`003_...sql`) y actualizar `infrastructure/database/tables.py`, que debe coincidir con las migraciones.

### Persistencia en el backend

- Puertos en `domain/ports/`: `SavedPlaceRepositoryPort`, `RouteRepositoryPort`, `VoiceCommandRepositoryPort`, `VibrationEventRepositoryPort`, `DeviceRepositoryPort`.
- Implementaciones con SQLAlchemy Core en `infrastructure/database/` (`SqlSavedPlaceRepository`, `SqlRouteRepository`, `SqlVoiceCommandRepository`, `SqlVibrationEventRepository`, `SqlDeviceRepository`), probadas con SQLite en memoria.

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
- RF10: Audio (TTS) opcional para las instrucciones de navegación: interruptor "Voz: sí/no" en Inicio; con "no" solo vibra.

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

1. Inicio (voz): botón grande de micrófono, "¿A dónde vamos?" y, debajo, botón "Lugares guardados" e interruptor "Voz: sí" / "Voz: no" (el estado va en el texto, no solo en el color; se guarda en SharedPreferences y empieza en sí).
   - Lugares guardados: lista (`TransformingLazyColumn`); tocar un lugar pide la ruta y pasa a Confirmar; mantenerlo pulsado ofrece borrarlo con confirmación ("¿Borrar «X»?" Cancelar / Borrar). Si está vacía: "Aún no tienes lugares guardados".
2. Escuchando destino.
3. Confirmar destino: nombre, distancia y tiempo a pie; Ir / Cambiar y botón secundario "Guardar" ("Guardando..." → "Guardado"; si falla, "No pude guardar el lugar"). Si el destino vino de un lugar guardado ya aparece "Guardado".
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
- Crear cuentas gratuitas: Geoapify (API key) y Google AI Studio (API key de Gemini); confirmar el nombre del modelo de Gemini disponible en la capa gratis.

## 13. Changelog

- **v0.15** (2026-10-28): Corrige que la voz leyera en voz alta la instrucción de estilo antes de cada frase. Según la guía de Gemini, los modelos TTS 3.8 tratan el texto como transcripción literal, así que el backend ahora envía solo la frase (sin `STYLE_PROMPT` ni system instruction); el acento depende de la voz (`VOICE_NAME`). El cache de audio del reloj pasa a la versión `v2` (`speechCacheFileName`) y borra los WAV viejos. `scripts/try_speech.py --verify` comprueba con 3 frases, transcribiendo con Gemini, que el audio contiene solo la frase; `--voices` lista voces por idioma.
- **v0.14** (2026-10-28): Voz hablada en el reloj. Puerto `SpeechOutput` (prioridad normal/alta) y `VoiceSettings`; `SpeechOutputImpl` cancela la frase anterior al llegar otra, salvo que la nueva sea normal y la actual alta. `AndroidSpeechPlayer` usa `POST /speech` (`SpeechApiClient`, timeout de 20 s cuando el backend ya despertó), guarda el WAV en el cache del reloj por hash del texto y lo reproduce con `MediaPlayer`; si falla, `TextToSpeech` en es-CO, y si tampoco hay, solo un `Log.w`. `BackendHttp` lee el cuerpo como bytes y acepta timeout por petición. Frases en `application/speech` (números en palabras, distancias redondeadas, minutos) para Confirmar, Ir, giro próximo, maniobra, desvío, sin señal, señal recuperada, llegada y errores en pantalla. `TrackNavigation` habla sin esperar y si la voz falla sigue vibrando. Interruptor "Voz: sí/no" en Inicio. Tests JUnit de frases y de la cancelación por prioridad con un reproductor falso.
- **v0.13** (2026-10-28): Voz hablada con Gemini TTS desde el backend. Nuevo endpoint `POST /speech` (`{text}` → `audio/wav`), puerto `SpeechSynthesizerPort`, adaptador `GeminiSpeechSynthesizer` (httpx; `generateContent` con `responseModalities: ["AUDIO"]`, voz prebuilt `Iapetus`; convierte el PCM 16-bit mono a WAV) y caso de uso `synthesize_speech` (texto no vacío de hasta 300 caracteres; caché en memoria de 100 frases). Errores de Gemini, incluido el 429 por cuota, salen como `ExternalServiceError` con mensaje claro. Nueva variable `GEMINI_TTS_MODEL` (por defecto `gemini-3.8-flash-tts`, gratis según la página de precios de Gemini) y script `scripts/try_speech.py` para probar la voz real. Queda resuelta la pregunta abierta sobre el TTS.
- **v0.12** (2026-10-28): Lugares guardados y registro en el reloj. Clientes HTTP `SavedPlacesApiClient`, `DevicesApiClient` y `VibrationEventsApiClient` (HttpURLConnection + org.json); `requireOk` acepta cualquier 2xx y `RoutesApiClient` lee `route_id` (nuevo `Route.id`). Caso de uso `ManageSavedPlaces` (listar, ruta hasta un lugar, guardar el destino con su nombre en el punto final de la ruta, borrar) y `RegisterDevice`: al primer inicio genera un UUID, hace `POST /devices` y guarda en SharedPreferences que ya se registró (si falla, reintenta en el próximo inicio). `TrackNavigation` envía en segundo plano un `POST /vibration-events` por cada vibración (patrón, `route_id` y orden del siguiente paso); si falla, se ignora y la navegación sigue. Pantallas: botón "Lugares guardados" en Inicio, lista con borrado por pulsación larga y confirmación, "Guardar" en Confirmar, botón "Inicio" en la pantalla de error y "Reintentar" que vuelve a la lista cuando el error vino de ahí. Las pantallas centradas se desplazan si el contenido no cabe. Tests JUnit de `ManageSavedPlaces`, `RegisterDevice` y del log de vibraciones.
- **v0.11** (2026-10-28): Endpoints de persistencia: `GET/POST /saved-places`, `DELETE /saved-places/{id}`, `POST /devices` (idempotente) y `POST /vibration-events`. `POST /routes` guarda la ruta y sus pasos (estado `active`) y devuelve `route_id`; `POST /voice/interpret` guarda el comando. Ese historial se guarda con `save_history` (mejor esfuerzo): si falla la base o falta `DATABASE_URL`, se registra el error y la respuesta principal sale igual. Casos de uso `manage_saved_places`, `register_device`, `record_vibration_event`; validación común en `application/validation.py`. Migraciones aplicadas en Neon y verificadas (7 tablas + `schema_migrations`, usuario semilla, `GET /health/db` ok).
- **v0.10** (2026-10-28): Base de datos. Migraciones SQL versionadas en `database/migrations/` (`001_create_tables.sql` con las 7 tablas, claves foráneas e índices; `002_seed_default_user.sql` con el usuario fijo id 1 "Usuario") y `backend/scripts/migrate.py`, que las aplica en orden, cada una en su transacción, y anota las corridas en `schema_migrations` (idempotente). Columnas en inglés (p. ej. `status` active/completed/cancelled, `step_order`, `emitted_at`); el id de `devices` lo genera el reloj. Nuevos modelos `SavedPlace`, `Device`, `RouteStatus` y `DEFAULT_USER_ID`, cinco puertos de repositorio y sus implementaciones SQLAlchemy Core (`infrastructure/database/tables.py` + `Sql*Repository`), con tests sobre SQLite en memoria.
- **v0.9** (2026-10-26): GPX de demostración en `docs/demo/` generados con `tools/route_to_gpx.py` a partir de una ruta real de `POST /routes` (centro de Pasto → Parque Bolívar, 1 970 m, 16 pasos; un punto cada ~5 m a 1,4 m/s): recorrido normal, con desvío de 35 m durante ~40 m y con un hueco de ~15 s sin señal, más instrucciones para el emulador y Logcat. La navegación ya no se corta con la pantalla apagada: la pantalla "En ruta" mantiene `FLAG_KEEP_SCREEN_ON` y un servicio en primer plano de tipo `location` (notificación "Rumbo: navegando") corre `TrackNavigation` desde que se toca "Ir" hasta "Terminar". Nuevos permisos `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` y `POST_NOTIFICATIONS` (pedido al tocar "Ir" en Android 13+). Un solo `AppContainer` por proceso (`RumboApplication`) compartido por actividad y servicio; puerto `NavigationRunner` para que el ViewModel no dependa del servicio.
- **v0.8** (2026-10-26): Motor de navegación en el reloj, en Kotlin puro (domain/application, sin `android.*`) con pruebas JUnit. GPS real con Fused Location (alta precisión, 1 s / mínimo 500 ms); se piden `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` junto con el micrófono (Android 12+ exige pedirlas juntas). Sin permiso se usa el centro de Pasto y se muestra "Sin GPS". El origen de `POST /routes` es la última lectura con menos de 30 s y precisión < 50 m. Geometría: haversine, rumbo, proyección equirectangular local, distancia punto-segmento, snap a la polilínea (con ventana alrededor del progreso) y distancias a lo largo de la ruta. Filtro: descarta precisión > 25 m o antigüedad > 5 s, mediana de 3 lecturas y velocidad/rumbo calculados si faltan. `NavigationEngine` recibe el tiempo en cada llamada; estados en ruta, giro próximo, maniobra, desviado, sin señal y llegada; progreso que solo avanza; umbrales en `NavigationThresholds`; histéresis por avisos únicos por paso y desvío 25 m (3 lecturas) / regreso 15 m. Patrones de vibración como datos (`VibrationPatterns`) y `AndroidVibrationController` (registra cada patrón con `Log.d("Vibracion")`). `TrackNavigation` une GPS, motor y vibración y recalcula la ruta tras 10 s fuera de ella. Nueva pantalla "En ruta" sin mapa con estado, siguiente paso, distancia restante e indicador del patrón; "Terminar" apaga GPS y vibración.
- **v0.7** (2026-10-26): El reloj arranca en el flujo "pedir destino por voz → confirmar" y se elimina la pantalla de prueba de voz. Dominio del reloj: `Coordinates`, `Place`, `Route`/`RouteStep` (`Maneuver` left/right/straight/arrive), `AppError` y puertos `VoiceRepository`, `PlacesRepository`, `RoutesRepository` y `AudioRecorder`. Caso de uso `PlanRouteFromVoice`: interpreta el audio, exige intención `navigate` con destino, busca el lugar y pide la ruta desde un origen fijo (centro de Pasto, 1.2136, -77.2811; pendiente reemplazar por GPS). Clientes HTTP sin dependencias nuevas (HttpURLConnection + org.json, timeouts de 90 s) que convierten `{"error":{"message"}}` y fallas de red en mensajes en español. Pantallas por estado (Inicio, Escuchando, Buscando ruta, Confirmar, En ruta provisional, Error) con los colores de Rumbo; el texto sobre el primario va oscuro por contraste. JUnit para el caso de uso.
- **v0.6** (2026-10-26): Búsqueda de lugares y ruta a pie con Geoapify (`GET /places/search`, `POST /routes`). Geocodificación filtrada a Colombia y sesgada a la ubicación del usuario o al centro de Pasto. El ruteo pide `details=instruction_details` (sin eso Geoapify no devuelve el tipo de maniobra) y reduce sus tipos a left/right/straight/arrive. `RouteStep` gana `instruction` y `Route` gana `polyline`. Modo `walk` confirmado en el plan gratis (Plaza de Nariño → Parque Bolívar: 1.649 m, 12 pasos).
- **v0.5** (2026-10-26): El reloj se conecta al backend en Render (`https://smartwatch-app.onrender.com`): permiso `INTERNET`, `VoiceApiClient` (HttpURLConnection, sin dependencias nuevas, timeouts de 90 s por el arranque en frío de Render) y modelo `VoiceResult` en el dominio. La pantalla "Prueba de voz" envía la grabación a `POST /voice/interpret` y muestra texto, intención y destino.
- **v0.4** (2026-10-26): Ruteo y geocodificación cambian de OpenRouteService a Geoapify (modo `walk`, 3.000 créditos/día) porque openrouteservice.org está caído; `GEOAPIFY_API_KEY` reemplaza a `ORS_API_KEY`. El emulador Wear OS no trae reconocimiento de voz ni motor TTS: el reloj graba audio (MediaRecorder, AAC) y lo envía a `POST /voice/interpret`, donde Gemini lo transcribe e interpreta; prueba del micrófono del emulador superada. Nuevo endpoint `POST /voice/interpret` (audio crudo, formato en `Content-Type`; devuelve `text`, `intent`, `destination`, `question`). TTS pendiente de definir entre Google Text-to-Speech en el emulador o Gemini TTS desde el backend.
- **v0.2** (2026-10-05): Voz y asistente conversacional como funcionalidad esencial; routing cambia de OSRM a OpenRouteService; se agrega Gemini API (voz/IA) y TTS de Android; nuevos puertos, casos de uso y entidad VoiceCommand; pantallas y design tokens definidos (formato cuadrado redondeado); fases reordenadas; landing page excluida del repositorio documentado.
- **v0.1** (2026-10-03): Alcance, stack, arquitectura Clean Architecture, estructura de repo, modelo de datos, requerimientos, pantallas y fases iniciales.
