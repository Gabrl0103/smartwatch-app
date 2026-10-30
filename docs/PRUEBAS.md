# Pruebas de Rumbo

Dos partes: las **automáticas** (se corren en cada cambio) y la **lista de pruebas manuales** para
la demo, en el emulador Wear OS Large Round o en un reloj. Cada prueba manual dice cómo hacerla y
qué se espera; si algo no coincide, es un error.

## 1. Pruebas automáticas

| Qué | Comando | Qué cubren |
|---|---|---|
| Backend | `cd backend` y `pytest` | Casos de uso, routers, repositorios (SQLite en memoria), Gemini y Geoapify con dobles, límite de 50 km en `POST /routes` |
| Reloj (JUnit) | `cd watchapp` y `gradlew testDebugUnitTest` | Geometría, filtro GPS, motor de navegación, vibraciones, frases, voz (prioridades y pausa al grabar), órdenes de voz y contexto del asistente, mapa (proyección, recorte, giro por rumbo, cuadros y ritmo de redibujo), casos de uso, red (reintento y mensajes), ViewModel |
| Recorridos GPX | incluidas en el comando anterior | `DemoGpxNavigationTest` y `DemoGpxTrackingTest` recorren los tres GPX de `docs/demo` segundo a segundo |
| Instrumentados | `gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.rumbo.watchapp.presentation` (emulador abierto) | `RouteDoubleTapTest`: toques reales sobre "En ruta", repite sin importar dónde caiga, nunca termina la ruta por un doble toque, y la acción que usa TalkBack. `UnknownStepScreenTest`: "Atención" en En ruta y en Probar vibraciones. `VoiceCommandScreenTest`: micrófono en Confirmar, "Guardado ✓" en la ruta y las vibraciones "Hecho" y "Error". `RouteMapScreenTest`: "Ver mapa", etiquetas de cada estado, descripción para TalkBack, "Atrás", alternar la vista y botones de 48 dp o más. También corre `ScreenshotsTest` |
| Capturas | `bash tools/capture_screens.sh` (emulador abierto) | Dibuja todas las pantallas en `docs/capturas` |

Las pruebas con GPX usan la ruta real del backend (`docs/demo/ruta_parque_narino_a_parque_bolivar.json`,
Plaza de Nariño → Parque Bolívar, 1 791 m, 5 giros), leída con el mismo código que el reloj, y
recorridos realistas (una lectura por segundo con ruido de GPS de σ ≈ 3 m, velocidad de 1,0 a
1,6 m/s, pausas en los cruces, precisión de 5 a 15 m, saltos de ~40 m y cortes de 2–3 s; ver
`docs/demo/README.md`). Comprueban que:

- **Normal**: cada giro avisa una vez en sus dos etapas (Giro próximo → Gira ahora; vibra su lado
  una vez) y termina en Llegaste, sin salirse nunca pese al ruido y los saltos.
- **Guía completa**: la voz de toda la ruta, frase por frase y en orden; todas salvo la de inicio
  estaban precargadas antes de sonar y no se precargó ninguna que no sonara.
- **Ruido con 30 semillas**: el mismo modelo generado en el test (`testing/RealisticWalk.kt`) con 30
  semillas distintas: ningún desvío ni giro falso, ningún aviso de más ni de menos (tampoco el
  lejano ni el del destino) y siempre llega.
- **Con desvío**: un solo tramo Fuera de ruta, pide recalcular y se recupera; sin internet sigue
  con la ruta anterior, vibra "fuera de ruta" en cada intento (como mucho 5) y llega igual.
- **Sin señal**: un solo tramo Sin señal GPS, "Perdí la señal de GPS." / "Señal recuperada." y
  llega sin falsos desvíos (el corte breve de 2–3 s no dispara nada).
- **Sin parpadeos**: ningún estado aparece menos de 3 s entre dos iguales (p. ej. En ruta → Fuera
  de ruta → En ruta).
- **Los GPX parecen reales**: una lectura por segundo con algún corte breve, precisión entre 5 y
  15 m, mediana de 1–4 m a la ruta, algún salto de más de 30 m y paradas en los cruces.

## 2. Preparación para las pruebas manuales

- Emulador **Wear OS Large Round** (API 34) con la app instalada (`gradlew installDebug`).
- Ruta de demostración: en **Extended controls → Location → Routes** importa un GPX de
  `docs/demo` y antes de pedir la ruta pon el reloj en el inicio:
  `adb emu geo fix -77.278419 1.214997` (primero la longitud).
- Log en una terminal aparte: `adb logcat -s Nav Vibracion Red`.
- El emulador no vibra: cada vibración se ve en Logcat (`Vibracion`) y en el indicador de "En ruta".
- El emulador no trae voz propia: habla Gemini por el backend, que en el plan gratis da 3 frases
  nuevas por minuto y 10 por día. Pasado el límite la app sigue sin voz, solo con vibraciones, hasta
  que se libere (segundos el de minuto; el diario, hasta que se renueve), así que conviene no gastar
  esas 10 antes de una demo. En un reloj con TextToSpeech habla el reloj y nada de esto aplica.
- Para volver a ver los diálogos de permisos:
  `adb shell pm revoke com.rumbo.watchapp android.permission.RECORD_AUDIO` (o
  `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`) y
  `adb shell pm clear-permission-flags com.rumbo.watchapp android.permission.RECORD_AUDIO user-set user-fixed`.

## 3. Lista de pruebas manuales

### Voz

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| V1 | Pedir destino por voz | Inicio → tocar el micrófono → decir "llévame al Parque Bolívar" | "Escuchando" con las barras moviéndose con la voz; al callarse, ~1,2 s después pasa solo a "Procesando" ("Buscando ruta..."), luego "Confirmar destino" con el nombre, la distancia y los minutos a pie; la voz dice "Parque Bolívar, a ..., ... minutos a pie. ¿Vamos?" |
| V2 | Orden sin sentido | Tocar el micrófono y decir algo que no sea un destino ni una pregunta (p. ej. toser o "eh...") | Error "No te entendí, intenta de nuevo" (en pantalla y en voz) con Reintentar e Inicio |
| V3 | Lugar inexistente | Decir "llévame a Xyzzy" | Error "No encontré «Xyzzy», intenta con otro nombre" |
| V4 | Voz apagada | Inicio → "Voz: sí" → pasa a "Voz: no" → hacer V1 e Ir | No se oye nada; las vibraciones siguen. El estado queda guardado al reabrir la app |
| V5 | Volver mientras escucha | Tocar el micrófono y hacer el gesto de volver (o tocar "Cancelar") | Vuelve a donde estaba (Inicio, Confirmar o la ruta), la grabación se cancela y no se pide nada |
| V6 | No decir nada | Tocar el micrófono y quedarse callado | A los 8 s deja de grabar solo y avisa que no entendió |

### Asistente

Requiere el backend con `POST /voice/ask` desplegado en Render.

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| A1 | Pregunta sin ruta | Inicio → micrófono → "¿qué puedes hacer?" | "Procesando" → "Pensando la respuesta..." → pantalla Asistente con «¿qué puedes hacer?» y la respuesta (máximo 2 frases), que también se dice en voz alta; mientras suena, el badge se vuelve barras. "Inicio" vuelve a Inicio |
| A2 | Pregunta en ruta | En ruta (R1) → "Preguntar" → "¿qué sigue?" | Escuchando dice "Pregunta o da una orden" y Procesando "Entendiendo..."; la respuesta usa los metros que faltan de verdad. Las vibraciones y los avisos de la ruta siguen (Logcat `Nav`, `Vibracion`). "Volver a la ruta" regresa a "En ruta" |
| A3 | Preguntar otra vez | En Asistente, tocar "Preguntar otra vez" mientras aún habla | La voz se calla al empezar a grabar (no se cuela en la pregunta nueva) y se puede preguntar de nuevo |
| A4 | "Repite" y "cancela" | En ruta → "Preguntar" → "repite"; luego "Preguntar" → "termina la ruta" | "Repite" vuelve a la ruta, vibra y dice la indicación; "termina" pregunta "¿Terminar la ruta?" en pantalla y en voz, sin terminarla |
| A5 | Otro destino en ruta | En ruta → "Preguntar" → "llévame a la Universidad" | Sigue en la misma ruta y dice "Ya vas hacia Parque Bolívar. Para ir a otro lugar, termina esta ruta." |
| A6 | Aviso mientras graba | Con el GPX corriendo, tocar "Preguntar" justo antes de un giro y hablar | La voz no dice el aviso mientras graba (la vibración sí suena); al terminar de grabar se dice el aviso más importante que llegó |
| A8 | Lo que Rumbo no hace | Preguntar "¿qué puedes hacer?" y "¿hay obstáculos adelante?" | Describe solo lo que hace (ruta a pie, avisos por vibración y voz, repetir, preguntas sobre la ruta) y dice que no detecta obstáculos; nunca promete ver el entorno |
| A7 | Cuota agotada | Con la cuota gratis de Gemini agotada, hacer una pregunta | Error "Se agotó la cuota gratuita del asistente de Gemini. Intenta más tarde."; en ruta, "Volver a la ruta" sigue navegando |

### Órdenes de voz

Cada orden termina con "Hecho" (2 pulsos cortos) o "Error" (1 pulso extra largo) en Logcat `Vibracion`, y la voz dice el resultado. Probadas el 2026-10-30 con Render y parche del micrófono: C1, C2 (ya guardado), C4 en pantalla, C5, C6, C7 y C8. Faltan C3, C9 y oír cada resultado en voz.

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| C1 | Micrófono en Confirmar | Confirmar → "Hablar" | Escuchando dice "Di guardar, repetir o cancelar"; "Cancelar" vuelve a Confirmar con la misma ruta y sin llamar a `/voice/interpret` |
| C2 | Guardar en Confirmar | Confirmar → "Hablar" → "guarda este lugar" | "Destino guardado.", vibra "Hecho" y el botón dice "Guardado ✓". Otra vez: "Este destino ya estaba guardado." y no se duplica en la lista |
| C3 | Guardar en ruta | En ruta hacia un destino sin guardar → "Preguntar" → "guarda este lugar" | "Destino guardado." y "En ruta" muestra "Guardado ✓"; sigue tras un recálculo y tras `am force-stop` y "Continuar ruta anterior" |
| C4 | Aviso al iniciar | Elegir un lugar guardado → "Ir" | La frase de inicio termina con "Este destino está guardado." y "En ruta" muestra "Guardado ✓" |
| C5 | Cuánto falta | En ruta → "¿cuánto falta?"; en Confirmar → lo mismo | "Faltan ..., ... a pie." sin Gemini (Logcat `Red` solo muestra `/voice/interpret`); en Confirmar, "Son ..., ... a pie." |
| C6 | Está guardado | "¿este lugar está guardado?" en Confirmar y en ruta | "Este destino está guardado." o "Este destino no está guardado.", según corresponda |
| C7 | Cancelar en Confirmar | Confirmar → "Hablar" → "cancela" | "Destino cancelado.", vibra "Hecho" y va a Inicio |
| C8 | Órdenes sin ruta | Inicio → "guarda este lugar", "cancela" o "¿cuánto falta?" | Vibra "Error", pantalla de error y la voz dice por qué |
| C9 | Sin internet | Sin red, dar una orden | Vibra "Error" y dice "Sin conexión. Revisa el internet del reloj" |

### Ruta y navegación

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| R1 | Iniciar ruta | Desde Confirmar tocar "Ir" | "En ruta" con el anillo, la maniobra siguiente, "En X m" y "Faltan X"; voz "Iniciando ruta. ..."; aparece la notificación "Rumbo: navegando"; Logcat `Nav: Estado - → ON_ROUTE` |
| R2 | Giros | Cargar `ruta_parque_narino_a_parque_bolivar.gpx` y Play route | Al empezar un tramo de más de 80 m, solo voz: "Sigue recto por Calle 19. En uno coma tres kilómetros, gira a la izquierda." Antes de cada giro "Giro próximo" (amarillo, 2 pulsos muy cortos, "En cincuenta metros, gira a la izquierda en Carrera 14.") y en el giro "Gira ahora" ("Gira ahora a la izquierda."; izquierda: 2 pulsos medios; derecha: 1 pulso largo): cada giro vibra en esas dos etapas. "Giro próximo" llega a unos 50 m del giro, nunca a más de 40 m aunque el GPS dé un salto. Logcat `Estado ON_ROUTE → TURN_APPROACHING (paso n a ~30 m) → MANEUVER` una vez por giro |
| R3 | Pantalla apagada | En ruta, apagar la pantalla (`adb shell input keyevent KEYCODE_SLEEP`) y seguir el GPX | Logcat sigue mostrando vibraciones ("Recto: 1 pulso suave cada 6 s", o "1 pulso corto" si el reloj no controla la intensidad) y cambios de estado; al encender la pantalla se ve el estado actual |
| R4 | Pantalla encendida y atenuada | En ruta, no tocar nada 15 s | La pantalla no se apaga sola; baja a ~30 % de brillo. Al tocarla o en el siguiente giro vuelve al brillo normal |
| R5 | Volver a la app | En ruta, salir con el botón de inicio del reloj y abrir Rumbo (o tocar la notificación) | Abre directo en "En ruta", con la ruta que seguía |
| R6 | El proceso muere | En ruta, `adb shell am force-stop com.rumbo.watchapp` y abrir Rumbo | Inicio muestra primero "Continuar ruta anterior" con el destino y la voz lo dice. Al tocarlo vuelve a "En ruta" desde donde esté el usuario, sin un falso "Fuera de ruta" |
| R7 | Terminar | En ruta, tocar "Terminar" una vez | Vuelve a Inicio tras los 300 ms de espera del doble toque; Logcat `Vibracion: Detenida`, `Nav: Navegación terminada` y `Servicio detenido`; desaparece la notificación; `adb shell dumpsys location` muestra `gps provider request = ProviderRequest[OFF]`; no se ofrece "Continuar ruta anterior" |
| R8 | Volver en ruta | En ruta, gesto de volver | Pregunta "¿Terminar la ruta?" con "Seguir navegando" (primero) y "Terminar"; volver otra vez sigue navegando |
| R9 | Repetir | En ruta, tocar "Repetir" | Vuelve a vibrar el último patrón y dice la indicación de la etapa actual con la distancia de ahora ("Sigue recto por ... En ... metros, gira ..."); Logcat `Nav: Repetir indicación`. Después de doblar nunca dice "Gira ahora" |
| R10 | Doble toque | En ruta, dos toques rápidos en cualquier parte (con `adb`: `adb shell 'input tap 227 300 & sleep 0.12; input tap 227 300; wait'`) | Una sola vez `Nav: Repetir indicación`. Un toque suelto no repite |
| R11 | Doble toque sobre "Terminar" | Bajar hasta "Terminar" y dar dos toques rápidos encima | Repite y la ruta sigue, sin `Nav: Navegación terminada` |
| R12 | Destino | Dejar correr el GPX hasta el final | Antes de llegar, una vez, "Tu destino está en ochenta metros, a la derecha." (el último tramo mide 83 m; en uno más largo, a cien metros) y al llegar "Llegaste a tu destino." |
| R13 | Precarga | Con voz Gemini y frases sin caché, mirar `adb logcat -s Red` al tocar "Ir" | Dos `POST /speech` seguidos: la frase de inicio y la de la siguiente etapa. Al sonar la siguiente etapa no hay otro `POST /speech` con esa frase, ni más de 2 precargas por minuto. Si llega un 429, la voz vuelve sola tras la espera (`Red` lo muestra), no 30 minutos después |
| R14 | TalkBack en ruta (solo reloj real) | Con TalkBack, tocar la indicación para enfocarla y dar doble toque en cualquier parte | Anuncia "repetir la indicación" y repite. Con el foco en un botón, activa ese botón |
| R15 | Paso desconocido | Pedir una ruta con escaleras con `GEOAPIFY_ROUTE_TYPE=short` en el backend (p. ej. en Barcelona, de 41.4145, 2.1527 a 41.4120, 2.1500) y caminarla o simularla | Antes del paso: "Atención: en cincuenta metros, tome las escaleras.", pantalla "Atención" con el ícono de advertencia y 4 pulsos cortos; a 12 m, "Atención: ahora, tome las escaleras." y otra vez 4 pulsos. Ninguna escalera de la ruta queda sin avisar |

### Mapa

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| M1 | Abrir y volver | En ruta, bajar hasta "Ver mapa" y tocarlo; luego "Atrás" o el gesto de volver | Abre "Mapa" y vuelve a En ruta sin preguntar "¿Terminar la ruta?"; la voz y las vibraciones no se cortan (`adb logcat -s Nav Vibracion`) |
| M2 | Recorrido | Con el mapa abierto, Play route en el GPX | La persona sigue al centro con el rumbo hacia arriba; lo recorrido queda azul, lo que falta claro y el próximo giro con un aro amarillo |
| M3 | Vistas | Tocar "Ruta" y luego "Cerca" | Toda la ruta con el norte arriba, destino en verde y la flecha con su rumbo; vuelve a la vista de cerca. TalkBack lee "Ver toda la ruta" y "Ver de cerca" |
| M4 | Desviado | Con `ruta_con_desvio.gpx` y el mapa abierto | Persona naranja, ruta con borde naranja y "Fuera de ruta"; al recalcular aparece la ruta nueva |
| M5 | Sin señal | Con el mapa abierto, apagar la ubicación 15 s | Persona gris en la última posición y "Sin señal"; al volver la señal sigue normal |
| M6 | Llegada | Dejar correr el GPX hasta el final con el mapa abierto | "Llegaste" sobre el destino; "Atrás" vuelve a la pantalla de llegada |
| M7 | Sin internet | Activar modo avión con la ruta ya calculada y abrir el mapa | Se dibuja igual: no pide nada a la red (`adb logcat -s Red`) |
| M8 | Batería | Con el mapa abierto y sin moverse | Nada se mueve en pantalla; tras 15 s se atenúa, y al tocar vuelve el brillo |

### Lugares guardados

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| L1 | Guardar | En Confirmar tocar "Guardar" | "Guardando..." → "Guardado ✓". Sin internet: "No pude guardar el lugar" en pantalla y voz |
| L2 | Elegir | Inicio → "Lugares guardados" → tocar un lugar | Pide ubicación si falta, "Procesando" y luego Confirmar con el lugar ya marcado "Guardado ✓" |
| L3 | Borrar | Mantener pulsado un lugar → "Borrar" | Pregunta "¿Borrar «X»?" (Cancelar / Borrar); al borrar, la lista se actualiza. Volver desde la pregunta regresa a la lista |
| L4 | Lista vacía | Borrar todos | "Aún no tienes lugares guardados" |

### Vibraciones

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| B1 | Guía | Inicio → "Vibraciones" → tocar cada patrón | Recto: 1 pulso suave cada 6 s; Giro próximo: 2 pulsos muy cortos; Izquierda: 2 pulsos medios; Derecha: 1 pulso largo; Atención: 4 pulsos cortos seguidos; Desviado: 3 pulsos largos con pausas cortas; Sin señal: 1 pulso muy largo cada 5 s; Llegada: 5 pulsos crecientes; Hecho: 2 pulsos cortos; Error: 1 pulso extra largo. Cada uno se nota distinto (en el reloj) y en el emulador se ve en Logcat y en las barras animadas |
| B3 | Reloj sin control de intensidad | Abrir la app y mirar `adb logcat -s Vibracion` | Al arrancar: `Motor de vibración: ...; control de intensidad: sí/no → patrones con amplitud / solo con ritmo y duración`. Sin control, la guía muestra "Recto: 1 pulso corto cada 6 s" y "Llegada: 5 pulsos cada vez más largos", y cada patrón se distingue solo por cuántos pulsos tiene y cuánto duran |
| B4 | Soporte de intensidad | Abrir "Vibraciones" | Arriba "Intensidad: sí" con "El reloj varía la fuerza" (emulador) o "Intensidad: no" con "Se distinguen por ritmo y duración". Coincide con la línea `control de intensidad` de Logcat |
| B5 | Barras con nombre | Tocar "Izquierda" | Vibra 2 pulsos medios; debajo del botón aparecen "Izquierda" y 2 barras que se encienden al ritmo del patrón. Logcat `Vibracion: Izquierda: 2 pulsos medios tiempos=[0, 300, 200, 300] ...` |
| B6 | Secuencia de demostración | Tocar "Secuencia de demostración" | Suenan los 10 patrones en orden (Recto, Giro próximo, Izquierda, Derecha, Atención, Desviado, Sin señal, Llegada, Hecho, Error); cada uno muestra "n de 10", su nombre en grande y sus barras; entre uno y otro, 2 s de pausa tras terminar (en Logcat, duración del patrón + 2 s entre líneas). Al final vuelve a la lista |
| B7 | Detener la demostración | Durante la demo, tocar "Detener" (o gesto de volver) | La vibración se corta (`Vibracion: Detenida`), no suena ningún patrón más y se vuelve a la lista; volver otra vez va a Inicio |
| B8 | Ajustes de vibración | Tocar "Intensidad: normal" y luego "Duración: normal" | Pasan a "Intensidad: fuerte" y "Duración: larga" ("Pulsos 30 % más largos"); cada toque vibra "Izquierda" con el ajuste nuevo (Logcat `tiempos=[0, 390, 200, 390] amplitudes=[0, 200, 0, 200]`). Otro toque en Intensidad pasa a "suave" y luego a "normal". Sin control de intensidad el botón dice "Este reloj no la controla" |
| B9 | Ajustes guardados | Cambiar los ajustes, `adb shell am force-stop com.rumbo.watchapp` y volver a abrir "Vibraciones" | Los ajustes siguen como se dejaron; en ruta las vibraciones salen con ellos (Logcat `Vibracion`) |
| B2 | Salir de la guía | Tocar un patrón largo y volver | La vibración se corta (`Vibracion: Detenida`) |

### Permisos denegados

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| P1 | Micrófono | Revocar el micrófono, tocar el micrófono y elegir "No permitir" | Pantalla "Permiso necesario": "Necesito el micrófono para escucharte" (también en voz), "Abrir ajustes", "Inicio" y la pista "En Ajustes, toca Permisos y actívalo" |
| P2 | Ubicación | Revocar la ubicación, tocar un lugar guardado y negar (o elegir solo "aproximada") | "Necesito tu ubicación para guiarte" con "Abrir ajustes" e "Inicio" |
| P3 | Notificaciones | Revocar notificaciones, tocar "Ir" y negar | "Necesito mostrar una notificación para guiarte con la pantalla apagada" con "Abrir ajustes", "Ir sin notificación" e "Inicio". "Ir sin notificación" navega igual |
| P4 | Dar el permiso en Ajustes | En cualquiera de las anteriores, "Abrir ajustes" → Permisos → permitir → volver | Abre la página de Rumbo en Ajustes; al volver sigue sola: con notificaciones arranca la ruta; con micrófono o ubicación vuelve a Inicio y el siguiente toque ya funciona |
| P5 | Cada permiso a su tiempo | Instalar de cero y abrir la app | No pide nada al abrir: micrófono y ubicación al tocar el micrófono, ubicación al elegir un lugar, notificaciones al tocar "Ir" |

### Sin internet, servidor dormido y GPS

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| N1 | Sin internet | `adb shell cmd connectivity airplane-mode enable` (o `svc wifi disable` y `svc data disable`) y pedir una ruta o abrir Lugares guardados | Reintenta solo una vez (Logcat `Red: ... Reintento en 2 s`) y luego error "Sin conexión. Revisa el internet del reloj" con Reintentar e Inicio. Nunca pantalla en blanco ni cierre |
| N2 | Servidor dormido | Abrir la app tras más de 15 min sin uso del backend (Render lo duerme) y pedir una ruta enseguida | Al abrir se hace `GET /health`. Si tarda más de 5 s, Procesando cambia a "El servidor está despertando, espera un momento" (y lo dice); cuando despierta sigue a Confirmar. Si no despierta tras el reintento, error con ese mismo mensaje |
| N3 | Navegar sin internet | Con la ruta ya calculada, activar modo avión y seguir el GPX | La navegación sigue igual (giros, vibraciones, llegada); solo se pierden la voz de Gemini y el log de vibraciones del backend |
| N4 | GPS lento | Apagar la ubicación del reloj (`adb shell cmd location set-location-enabled false`; el emulador siempre tiene alguna posición si está encendida) y elegir un lugar guardado | Procesando muestra y dice "Buscando señal GPS" con el indicador; al encenderla (`... set-location-enabled true`) sigue sola. Si en 40 s no llega: "No encuentro señal GPS. Sal a un lugar abierto e intenta de nuevo" |
| N5 | Muy lejos | `adb emu geo fix -74.0721 4.7110` (Bogotá) y elegir un lugar guardado de Pasto | "Estás muy lejos del destino", sin pedir la ruta |

### Desvío

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| D1 | Desvío con internet | Cargar `ruta_con_desvio.gpx`, Ir y Play route | A mitad de ruta: "Fuera de ruta" (naranja, ícono de advertencia, 3 pulsos largos), voz "Te saliste de la ruta. Recalculando." y "Recalculando ruta..."; a los 10 s Logcat `Recalculando ruta (intento 1 de 5)` y `Ruta recalculada`; la pantalla vuelve a "En ruta" con la ruta nueva |
| D2 | Desvío sin internet | Igual que D1 con modo avión antes del desvío | Cada 10 s un intento que falla (`No se pudo recalcular (intento n de 5)`), 3 pulsos largos de "desviado" en cada uno, como mucho 5 intentos; se sigue con la ruta anterior y al volver a la calle pasa a "En ruta" / "Gira ahora" |
| D3 | Sin parpadeo | Durante D1/D2 mirar el badge | El estado no salta entre "En ruta" y "Fuera de ruta" con el ruido del GPS: sale tras 3 lecturas a más de 25 m y vuelve a menos de 15 m |
| D4 | Sin señal | En ruta con cualquier GPX, apagar la ubicación 15 s (`adb shell cmd location set-location-enabled false` y luego `true`). En el emulador el hueco de `ruta_sin_senal.gpx` no basta: el emulador repite la última posición | A los 10 s "Sin señal GPS" (gris, ubicación tachada, 1 pulso muy largo cada 5 s, "Perdí la señal de GPS.") y al volver los puntos "Señal recuperada." y el estado anterior, sin "Fuera de ruta" |

### Llegada

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| A1 | Llegar | Reproducir cualquier GPX hasta el final | A menos de 15 m del destino: "Llegaste" (verde, check, anillo lleno), "Llegaste a Parque Bolívar", 5 pulsos crecientes y voz "Llegaste a tu destino." Logcat `Estado ... → ARRIVED` una sola vez |
| A2 | Después de llegar | Gesto de volver o "Terminar" | Vuelve a Inicio sin preguntar; todo se detiene (como R7) |
| A3 | No ofrecer continuar | Llegar, `adb shell am force-stop com.rumbo.watchapp` y abrir | Inicio sin "Continuar ruta anterior": una ruta terminada no se ofrece |
