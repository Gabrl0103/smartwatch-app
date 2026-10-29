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
| Texto → voz | Principal: TextToSpeech del reloj en español. Respaldo: Gemini TTS desde el backend (`POST /speech`, modelo `gemini-3.8-flash-tts`, capa gratis) | La voz del reloj no gasta cuota ni red; el emulador Wear OS (API 34) no trae motor TTS, así que ahí habla Gemini |
| Base de datos | PostgreSQL (Render o Supabase, free tier) | Gratis |
| Hosting backend | Render (free tier) | $0; el servidor duerme tras inactividad |
| Tests | pytest (backend) / JUnit (reloj) | domain y application (y el ViewModel del reloj); en el reloj, además, un test instrumentado que genera las capturas |

Costo total: **$0**.

Nota: el servidor demo de OSRM se descartó porque no está confirmado que ofrezca perfil a pie de forma fiable. OpenRouteService se reemplazó por Geoapify porque openrouteservice.org está caído.

## 3. Arquitectura — Clean Architecture (4 capas)

Dependencias apuntan hacia adentro: `domain` no conoce a nadie. Patrón Repository/puertos: los casos de uso dependen de interfaces, las implementaciones se cambian solo en el composition root.

**Reloj (watchapp)**
- `domain`: models (Route, RouteStep, VibrationPattern, VoiceIntent), errors (RouteNotFoundError, GpsUnavailableError, VoiceNotUnderstoodError), ports (RoutingRepository, LocationProvider, VibrationController, SpeechRecorder, SpeechSynthesizer, VoiceAssistantRepository)
- `application`: casos de uso (ProcessVoiceCommand, ConfirmDestination, CalculateRoute, TrackUserPosition, EmitNavigationInstruction, DetectDeviation, DetectArrival, AskAssistant)
- `infrastructure`: cliente HTTP al backend, AndroidLocationProvider (FusedLocationProviderClient), AndroidVibrationController (Vibrator API), AndroidAudioRecorder (MediaRecorder, AAC), salida de voz en `infrastructure/tts` (`SpeechOutputImpl` elige entre `AndroidTextToSpeech`, la voz principal, y `GeminiSpeechPlayer` con `POST /speech` como respaldo), composition root (DI manual)
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
│       ├── presentation/{screens,components,viewmodel,theme,format}/
│       └── di/
├── database/migrations/
├── docs/{REQUIREMENTS.md,capturas/,demo/}
├── tools/{route_to_gpx.py,capture_screens.sh}
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
- Errores de Gemini → 502 `EXTERNAL_SERVICE_ERROR`; la cuota agotada (429 de Gemini) → 429 `QUOTA_EXHAUSTED` con "Se agotó la cuota gratuita de voz de Gemini. Intenta más tarde.", para que el reloj deje de pedir voz un rato.
- Prueba real: desde `backend/`, `python scripts/try_speech.py "Llegaste a tu destino." prueba.wav`. Con `--verify` genera 2 frases, las transcribe con `GEMINI_MODEL` y falla si el audio dice algo más que la frase (similitud < 0,85; compara sin tildes ni signos y con los números en palabras, así "50" cuenta como "cincuenta").

### Voz en el reloj

- Puerto `SpeechOutput { speak(text, priority); stop() }` en `domain/port`; prioridades `NORMAL` y `HIGH` (giro, desvío, llegada).
- `SpeechOutputImpl` decide qué suena: una frase nueva cancela la actual, salvo que la nueva sea `NORMAL` y la actual `HIGH` (esa se descarta). Si la voz está apagada no dice nada.
- Qué voz usa: 1) TextToSpeech del reloj si está listo (si aún arranca, lo espera hasta 3 s); 2) Gemini por `POST /speech` solo si la voz del reloj no está disponible o falla en esa frase; 3) nada. Si el backend responde `QUOTA_EXHAUSTED`/429, no se vuelve a llamar a Gemini durante 30 minutos.
- `AndroidTextToSpeech` arranca al abrir la app y prueba en orden es-CO, es-419, es-US, es-MX, es-ES y "es"; usa el primero con `isLanguageAvailable >= LANG_AVAILABLE`. Deja en el log (etiqueta `Voz`) el motor, los motores instalados, los idiomas disponibles y el elegido, o el resultado de cada idioma si ninguno sirve. Si falta descargar el español (`LANG_MISSING_DATA`), Inicio muestra el botón "Falta la voz en español del reloj", que abre `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`.
- Verificado en el emulador Wear OS API 34: no hay ningún servicio `TTS_SERVICE` instalado; el log dice `No hay motor TextToSpeech en el reloj (onInit=-1); se usará Gemini` e Inicio muestra "Voz: Gemini".
- `GeminiSpeechPlayer` pide el WAV a `POST /speech` (90 s mientras Render despierta; 20 s cuando el backend ya respondió una vez), lo guarda en `cacheDir/speech/v2-<sha256 de "v2:" + texto>.wav` (al cambiar la versión se ignoran y borran los audios anteriores), lo reutiliza si la frase se repite y lo reproduce con `MediaPlayer` (uso "guía de navegación").
- Si no hay voz del reloj y Gemini falla (red, servidor o cuota), no dice nada y deja un `Log.w` con etiqueta `Voz`. La voz nunca bloquea ni detiene la navegación ni las vibraciones.
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

### Permisos, GPS y red en el reloj

- **Permisos, cada uno cuando hace falta**: micrófono y ubicación precisa al tocar el micrófono, ubicación al elegir un lugar guardado y notificaciones (Android 13+) al tocar "Ir". Con solo la ubicación aproximada cuenta como negado. Si se niega, la pantalla "Permiso necesario" lo explica en texto y voz ("Necesito el micrófono para escucharte", "Necesito tu ubicación para guiarte", "Necesito mostrar una notificación para guiarte con la pantalla apagada") y ofrece "Abrir ajustes" (la página de la app en Ajustes) e "Inicio"; para notificaciones también "Ir sin notificación", porque se navega igual. Al volver de Ajustes con el permiso dado sigue sola: arranca la ruta que esperaba o vuelve a Inicio.
- **Origen de la ruta**: nunca se inventa. Si no hay una lectura con menos de 30 s y precisión < 50 m, Procesando muestra y dice "Buscando señal GPS" (con indicador) y espera hasta 40 s; si no llega, "No encuentro señal GPS. Sal a un lugar abierto e intenta de nuevo". Si el destino encontrado queda a más de 50 km: "Estás muy lejos del destino", sin pedir la ruta (el backend también lo rechaza con 422).
- **Sin internet** (no resuelve ni conecta): "Sin conexión. Revisa el internet del reloj". **Servidor dormido** (timeout antes de su primera respuesta, o 502/503/504 del proxy de Render sin el JSON de error propio): "El servidor está despertando, espera un momento". Ambos se reintentan solos una vez a los 2 s; si vuelve a fallar, la pantalla de error con Reintentar e Inicio. La voz de Gemini no se reintenta (una frase tarde ya no sirve).
- Mientras una petición lleva más de 5 s esperando a un servidor que aún no contestó (o en su reintento), Procesando muestra y dice "El servidor está despertando, espera un momento". Al abrir la app se hace `GET /health` para despertarlo antes de que haga falta.
- Cada petición queda en Logcat con la etiqueta `Red`: método, ruta, código y milisegundos (`Log.i`); fallas, "servidor despertando" y reintentos con `Log.w`.
- Nunca una pantalla en blanco ni un cierre: los errores de las corrutinas de fondo y del servicio de navegación quedan en el log (`Rumbo`, `Nav`), y una ruta sin pasos ni polilínea es "No encontré una ruta a pie hasta ese lugar".

### Endpoints del backend

| Método | Ruta | Entrada | Salida |
|---|---|---|---|
| GET | `/health` | — | Estado del servicio |
| GET | `/health/db` | — | Estado de la conexión a la base de datos |
| POST | `/voice/interpret` | Audio crudo en el cuerpo; formato en `Content-Type` (p. ej. `audio/aac`) | `text`, `intent`, `destination`, `question`. Guarda el comando en `voice_commands` (si el guardado falla, se registra en el log y se responde igual) |
| POST | `/speech` | JSON `{text}` (máximo 300 caracteres) | `audio/wav` con la frase hablada (422 si falta el texto o es muy largo; 429 `QUOTA_EXHAUSTED` si se agota la cuota de Gemini; 502 si Gemini falla por otra razón) |
| GET | `/places/search` | `q` (texto); `lat`, `lng` opcionales para priorizar cercanía (si faltan, centro de Pasto) | `name`, `lat`, `lng` (404 si no hay resultados) |
| POST | `/routes` | JSON `{origin: {lat, lng}, destination: {lat, lng}, destination_name}` | `route_id`, `destination_name`, `distance_m`, `duration_s`, `steps: [{order, maneuver (left/right/straight/arrive), instruction, distance_m, lat, lng}]`, `polyline: [[lat, lng], ...]` (422 si faltan coordenadas o están fuera de rango; 422 "Estás muy lejos del destino" si están a más de 50 km en línea recta, sin llamar a Geoapify). Guarda la ruta y sus pasos con estado `active`; si el guardado falla, se registra en el log y `route_id` llega `null` |
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

- NFR1: Accesibilidad — nunca comunicar un estado solo con color; siempre texto grande + vibración/audio. Elementos táctiles ≥ 48 px (los botones miden 52 dp). Pantallas compatibles con TalkBack: descripción en íconos y botones, títulos marcados como encabezado, orden de lectura de arriba abajo (el anillo se lee al final), cada pantalla anuncia su nombre (`paneTitle`) y los cambios de estado e indicación se anuncian solos (`liveRegion`); las distancias no, para no hablar en cada lectura del GPS.
- NFR2: Funcional en emulador Wear OS sin hardware físico.
- NFR3: Navegación (GPS + vibración) funciona sin internet una vez calculada la ruta.
- NFR4: Backend desplegado gratis; se acepta el retraso del primer request por el "sleep" del free tier.
- NFR5: Secretos solo en `.env`; `.env.example` sí se versiona.
- NFR6: Manejo global de errores (middleware backend, errores de dominio propios).
- NFR7: Privacidad — en la capa gratis de Gemini el contenido enviado puede usarse para mejorar productos de Google; no enviar datos personales reales.
- NFR8: Avisar que es un proyecto académico y no debe ser el único medio de orientación en uso real.
- NFR9: Robustez para la demo y para usarla caminando: sin internet, con el servidor dormido, sin GPS o con un permiso negado, la app explica qué pasa en español (texto y voz) y ofrece cómo seguir; nunca queda en blanco ni se cierra.

## 9. Pantallas (reloj)

Diseñadas en Claude Design (lienzo "Rumbo — UI del reloj"). La app se prueba en el emulador **Wear OS Large Round** (454 × 454 px, 320 dpi = 227 dp); capturas de cada pantalla en `docs/capturas/` (se regeneran con `tools/capture_screens.sh`).

**Pantalla redonda.** Todas usan `RumboScreen`: `ScreenScaffold` de Wear (la hora se esconde al desplazar y aparece el indicador de desplazamiento) con márgenes en proporción a la pantalla, 10 % a los lados y 14 % arriba y abajo (en la cuadrada, 5,2 % y 12 %), para que una píldora a lo ancho quepa entera en el círculo en la primera y la última posición y nada quede debajo de la hora. Si el contenido cabe, se centra; si no, se desplaza con el dedo o la corona. Lo que pasa por el borde de arriba o de abajo se achica (hasta 60 %) lo justo para entrar en el círculo y, si se achica más del 75 %, se atenúa, como en las listas de Wear: nada queda cortado. Con anillo, el contenido se aleja también del anillo.

**Gesto de volver.** El "atrás" del sistema (incluido el de TalkBack) y, hasta Android 15, deslizar de izquierda a derecha (`BasicSwipeToDismissBox`; desde Android 16 el sistema convierte ese gesto en "atrás"). Desde Inicio no se intercepta y sale de la app; desde Confirmar, Escuchando, Procesando (cancela la grabación o la carga), Error, Lugares guardados y Vibraciones vuelve a Inicio; desde "¿Borrar?" vuelve a la lista; en ruta pregunta "¿Terminar la ruta?" (Seguir navegando / Terminar), salvo que ya se haya llegado, y volver otra vez equivale a "Seguir navegando".

1. Inicio: badge "Listo para navegar" (y "Sin GPS" si aún no hay permiso de ubicación), "¿A dónde vamos?" (Poppins 700, 32 sp), botón de micrófono de 108 dp ("Hablar destino"), "Toca y habla", botones "Lugares guardados" y "Vibraciones" (Aprende los patrones), interruptor "Voz: sí" / "Voz: no" (el estado va en el texto, no solo en el color; se guarda en SharedPreferences y empieza en sí) con la voz en uso debajo ("Del reloj", "Gemini" o "No disponible") y, si falta el español del motor, el botón "Falta la voz en español del reloj".
   - Lugares guardados: lista de píldoras; tocar un lugar pide la ruta y pasa a Confirmar; mantenerlo pulsado ofrece borrarlo con confirmación ("¿Borrar «X»?" Cancelar / Borrar). Si está vacía: "Aún no tienes lugares guardados".
   - Vibraciones: guía con un botón por patrón (En ruta, Giro próximo, Izquierda, Derecha, Fuera de ruta, Sin señal GPS, Llegada) y su descripción; al tocarlo vibra y debajo se ve el patrón animado.
2. Escuchando: badge "Escuchando", micrófono que late y "Di el nombre del lugar".
3. Procesando: badge "Procesando", indicador de carga y "Buscando ruta..." o "Cargando lugares..."; si tarda, "Buscando señal GPS" o "El servidor está despertando, espera un momento" (también dichos en voz alta).
   - Permiso necesario: ícono, la explicación del permiso negado, "Abrir ajustes", "Ir sin notificación" (solo notificaciones), "Inicio" y la pista "En Ajustes, toca Permisos y actívalo".
4. Confirmar destino: badge, nombre, distancia y tiempo a pie; Ir, Cambiar y "Guardar" ("Guardando..." → "Guardado"; si falla, "No pude guardar el lugar"). Si el destino vino de un lugar guardado ya aparece "Guardado".
5. Error: ícono de advertencia, el mensaje, Reintentar e Inicio.
6. En ruta: anillo de progreso en el borde (se llena hacia el siguiente paso; deja libre el hueco de la hora), badge del estado, ícono de la maniobra siguiente, la indicación (24 sp; 20 sp si pasa de 16 caracteres; con más de 30 el ícono baja de 72 a 60 dp para que se lea sin desplazar), "En X m" y "Faltan X", indicador de vibración y botones "Repetir" (vuelve a vibrar el último patrón y dice la indicación completa) y "Terminar". Estados, cada uno con su texto, color e ícono:

| Estado | Badge | Color | Ícono | Texto principal |
|---|---|---|---|---|
| En ruta | En ruta | `#4F8EFF` | maniobra siguiente (recto, izquierda o derecha) | indicación |
| Giro próximo | Giro próximo | `#FFC857` | izquierda / derecha | indicación |
| Maniobra | Gira ahora | `#FFC857` | izquierda / derecha | indicación |
| Desviado | Fuera de ruta | `#FF8A3D` | advertencia | "Te saliste de la ruta" (+ "Recalculando ruta...") |
| Sin señal | Sin señal GPS | `#8A93A6` | ubicación tachada | "Buscando señal GPS" |
| Llegada | Llegaste | `#3DDC84` | check | "Llegaste a (destino)", con el anillo lleno |

7. Asistente de voz: pregunta del usuario, respuesta hablada y escrita, botón para volver a preguntar (pendiente).

## 10. Estilo visual — Design tokens

- Tipografía: títulos Poppins (600/700), texto Inter (400/500/700), empaquetadas como TTF en `res/font` (descargadas de Google Fonts, licencia OFL en `assets/licenses`; sin proveedor de fuentes en línea). Escala del reloj: 16 / 20 / 24 / 32 / 40 sp con interlineado 1.25; ningún texto baja de 16 sp (`presentation/theme/Type.kt`).
- Colores base (`presentation/theme/Color.kt` y `Theme.kt`): fondo `#0B0E14`, superficie `#151922`, texto `#F5F7FA`, texto secundario `#A0A8B8`, primario `#4F8EFF`, secundario `#7C5CFC` (asistente; el chip usa `#9B82FF` para contraste), acento `#FFC857`, borde `#2A2F3A`. Sobre botones y fondos de color claro el texto va oscuro (`#0B0E14`): sobre `#4F8EFF` da 6.1:1 (el blanco daba 3.2:1) y sobre los demás estados pasa de 6:1.
- Escala semántica de estados (siempre con texto): en ruta `#4F8EFF`, giro próximo `#FFC857`, desviado `#FF8A3D`, sin señal GPS `#8A93A6`, llegada `#3DDC84`.
- Formas: botones en píldora de 52 dp de alto como mínimo, borde fino de 1 dp en `#2A2F3A`, sin sombras.
- Componentes (`presentation/components`):
  - `StateBadge`: píldora con el nombre del estado escrito en su color sobre un tinte del 16 % del mismo color y borde de 1 dp; puede anunciarse con TalkBack.
  - `ProgressRing`: anillo de 6 dp en el borde con hueco arriba para la hora; se llena con la distancia restante al siguiente paso normalizada por el largo del tramo (`TrackingState.stepProgress`).
  - `StateIcon`: íconos de trazo de 72 dp: micrófono, izquierda, derecha, recto, vuelta en U, rotonda, advertencia, sin señal GPS y llegada, cada uno con su descripción. El backend reduce las maniobras a izquierda/derecha/recto/llegada, así que vuelta en U y rotonda aún no se muestran en ruta.
  - `VibrationIndicator`: una barra en píldora de 22 × 8 dp por pulso que se enciende al ritmo real del patrón (más opaca cuanto más fuerte) y el nombre del patrón en texto ("2 pulsos cortos").
  - `PillButton` (primario, secundario y peligro), `MicButton` (108 dp) y `PillSwitch`.
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

- **v0.20** (2026-10-28): Permisos y errores robustos para la demo. Cada permiso se pide al necesitarlo (micrófono + ubicación precisa al tocar el micrófono, ubicación al elegir un lugar guardado, notificaciones al tocar "Ir") y, si se niega, nueva pantalla "Permiso necesario" (`PermissionScreen`, `PlanState.PermissionNeeded`) con la explicación en texto y voz, "Abrir ajustes" e "Inicio" ("Ir sin notificación" para notificaciones); al volver de Ajustes con el permiso sigue sola. Se elimina el origen de respaldo en el centro de Pasto: `LocationTracker.awaitOrigin` espera hasta 40 s una lectura buena mostrando "Buscando señal GPS" y si no llega falla con `GpsUnavailableError`; destinos a más de 50 km dan "Estás muy lejos del destino" (`requireWalkable` en el reloj y `require_walkable` en `POST /routes`). Red: `NoConnectionError` ("Sin conexión...") y `ServerWakingError` ("El servidor está despertando, espera un momento") con un reintento automático (`withOneRetry`), aviso en Procesando cuando una petición espera más de 5 s a Render (`BackendServerStatus`, puerto `ServerStatus`), `GET /health` al abrir la app para despertarlo y log `Red` de cada petición. Errores de corrutinas de fondo y del servicio van al log sin cerrar la app; rutas vacías dan un error claro. Tests JUnit de `LocationTracker`, `BackendHttp` (clasificación y reintento), casos de uso (sin GPS, demasiado lejos) y ViewModel (permisos, esperas); pytest del límite de 50 km. Capturas nuevas `20_permiso_ubicacion` y `21_buscando_gps`.
- **v0.19** (2026-10-28): Rediseño de la interfaz del reloj según el diseño de Rumbo, probado en el emulador Wear OS Large Round (454 px). Tema de Compose completo con los tokens (texto oscuro sobre colores claros) y Poppins e Inter empaquetadas en `res/font` con su licencia OFL; escala 16/20/24/32/40 sp. Componentes `StateBadge`, `ProgressRing`, `StateIcon`, `VibrationIndicator` (barras de 22 × 8 dp al ritmo del patrón) y botones en píldora con borde de 1 dp. Todas las pantallas usan `RumboScreen`: márgenes en porcentaje para pantalla redonda, desplazamiento con dedo o corona, la hora se esconde al desplazar y lo que pasa por el borde se achica para no quedar cortado. Pantallas rediseñadas (Inicio, Escuchando, Procesando, Confirmar, Error, En ruta con sus seis estados, Lugares guardados, Borrar) y nueva pantalla "Vibraciones" para aprender los patrones. En ruta: botón "Repetir" (`TrackNavigation.repeat` y `repeatPhrase`) y "¿Terminar la ruta?" al volver. Gesto de volver en toda la app; volver cancela la grabación o la carga en curso. TalkBack: descripciones, encabezados, `paneTitle` y anuncios de estado. El motor expone el largo del tramo (`stepLengthM`) para el anillo. Tests JUnit nuevos del ViewModel (volver, Repetir, Vibraciones), de `repeatPhrase`, `repeat`, `stepProgress` y `VibrationPattern.pulses`; capturas de 19 pantallas en `docs/capturas/` con el test instrumentado `ScreenshotsTest`.
- **v0.18** (2026-10-28): La voz del reloj (TextToSpeech) pasa a ser la principal y Gemini queda de respaldo. `AndroidTextToSpeech` arranca al abrir la app, prueba es-CO, es-419, es-US, es-MX, es-ES y "es", y deja en el log `Voz` el motor, los idiomas y el elegido o el motivo. `SpeechOutputImpl` usa la voz del reloj si está lista, Gemini (`GeminiSpeechPlayer`, antes `AndroidSpeechPlayer`) solo si no, y deja de llamar a Gemini 30 minutos tras un `QUOTA_EXHAUSTED` (nuevo `QuotaExhaustedError`). Inicio muestra qué voz se usa y, si falta el español del motor, un botón que abre la instalación de voces. El manifiesto ya tenía la consulta `TTS_SERVICE`. En el emulador Wear OS API 34 no hay motor TTS: habla Gemini. Tests de prioridad entre voces y de la pausa por cuota.
- **v0.17** (2026-10-28): `scripts/try_speech.py --verify` pasa a 2 frases y normaliza números antes de comparar ("50" = "cincuenta", "1,6" = "uno coma seis"), para no dar falsos fallos cuando la transcripción usa cifras.
- **v0.16** (2026-10-28): Nuevo error de dominio `QuotaExhaustedError` (código `QUOTA_EXHAUSTED`, HTTP 429). `POST /speech` lo devuelve cuando Gemini responde 429, en vez del 502 genérico.
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
