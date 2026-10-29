# Pruebas de Rumbo

Dos partes: las **automáticas** (se corren en cada cambio) y la **lista de pruebas manuales** para
la demo, en el emulador Wear OS Large Round o en un reloj. Cada prueba manual dice cómo hacerla y
qué se espera; si algo no coincide, es un error.

## 1. Pruebas automáticas

| Qué | Comando | Qué cubren |
|---|---|---|
| Backend | `cd backend` y `pytest` | Casos de uso, routers, repositorios (SQLite en memoria), Gemini y Geoapify con dobles, límite de 50 km en `POST /routes` |
| Reloj (JUnit) | `cd watchapp` y `gradlew testDebugUnitTest` | Geometría, filtro GPS, motor de navegación, vibraciones, frases, casos de uso, red (reintento y mensajes), ViewModel |
| Recorridos GPX | incluidas en el comando anterior | `DemoGpxNavigationTest` y `DemoGpxTrackingTest` recorren los tres GPX de `docs/demo` segundo a segundo |
| Capturas | `bash tools/capture_screens.sh` (emulador abierto) | Dibuja todas las pantallas en `docs/capturas` |

Las pruebas con GPX usan la ruta real del backend (`docs/demo/ruta_parque_narino_a_parque_bolivar.json`,
Plaza de Nariño → Parque Bolívar, 1 791 m, 5 giros), leída con el mismo código que el reloj, y
recorridos realistas (una lectura por segundo con ruido de GPS de σ ≈ 3 m, velocidad de 1,0 a
1,6 m/s, pausas en los cruces, precisión de 5 a 15 m, saltos de ~40 m y cortes de 2–3 s; ver
`docs/demo/README.md`). Comprueban que:

- **Normal**: cada giro avisa una vez en sus dos etapas (Giro próximo → Gira ahora; vibra su lado
  una vez) y termina en Llegaste, sin salirse nunca pese al ruido y los saltos.
- **Ruido con 30 semillas**: el mismo modelo generado en el test (`testing/RealisticWalk.kt`) con 30
  semillas distintas: ningún desvío ni giro falso, ningún aviso de más ni de menos y siempre llega.
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
- El emulador no trae voz propia: habla Gemini por el backend; si responde 429 se agotó la cuota
  gratuita y la app sigue sin voz (solo vibraciones). En un reloj con TextToSpeech habla el reloj.
- Para volver a ver los diálogos de permisos:
  `adb shell pm revoke com.rumbo.watchapp android.permission.RECORD_AUDIO` (o
  `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`) y
  `adb shell pm clear-permission-flags com.rumbo.watchapp android.permission.RECORD_AUDIO user-set user-fixed`.

## 3. Lista de pruebas manuales

### Voz

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| V1 | Pedir destino por voz | Inicio → tocar el micrófono → decir "llévame al Parque Bolívar" | "Escuchando" con las barras moviéndose con la voz; al callarse, ~1,2 s después pasa solo a "Procesando" ("Buscando ruta..."), luego "Confirmar destino" con el nombre, la distancia y los minutos a pie; la voz dice "Parque Bolívar, a ..., ... minutos a pie. ¿Vamos?" |
| V2 | Orden sin destino | Tocar el micrófono y decir "¿qué hora es?" | Error "No entendí el destino, intenta de nuevo" (en pantalla y en voz) con Reintentar e Inicio |
| V3 | Lugar inexistente | Decir "llévame a Xyzzy" | Error "No encontré «Xyzzy», intenta con otro nombre" |
| V4 | Voz apagada | Inicio → "Voz: sí" → pasa a "Voz: no" → hacer V1 e Ir | No se oye nada; las vibraciones siguen. El estado queda guardado al reabrir la app |
| V5 | Volver mientras escucha | Tocar el micrófono y hacer el gesto de volver (o tocar "Cancelar") | Vuelve a Inicio, la grabación se cancela y no se pide ruta |
| V6 | No decir nada | Tocar el micrófono y quedarse callado | A los 8 s deja de grabar solo y avisa que no entendió |

### Ruta y navegación

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| R1 | Iniciar ruta | Desde Confirmar tocar "Ir" | "En ruta" con el anillo, la maniobra siguiente, "En X m" y "Faltan X"; voz "Iniciando ruta. ..."; aparece la notificación "Rumbo: navegando"; Logcat `Nav: Estado - → ON_ROUTE` |
| R2 | Giros | Cargar `ruta_parque_narino_a_parque_bolivar.gpx` y Play route | Antes de cada giro "Giro próximo" (amarillo, 2 pulsos muy cortos, "En treinta metros, gire...") y en el giro "Gira ahora" (izquierda: 2 pulsos medios; derecha: 1 pulso largo): cada giro vibra en esas dos etapas. "Giro próximo" llega a unos 30 m del giro, nunca a más de 40 m aunque el GPS dé un salto. Logcat `Estado ON_ROUTE → TURN_APPROACHING (paso n a ~30 m) → MANEUVER` una vez por giro |
| R3 | Pantalla apagada | En ruta, apagar la pantalla (`adb shell input keyevent KEYCODE_SLEEP`) y seguir el GPX | Logcat sigue mostrando vibraciones ("Recto: 1 pulso suave cada 6 s", o "1 pulso corto" si el reloj no controla la intensidad) y cambios de estado; al encender la pantalla se ve el estado actual |
| R4 | Pantalla encendida y atenuada | En ruta, no tocar nada 15 s | La pantalla no se apaga sola; baja a ~30 % de brillo. Al tocarla o en el siguiente giro vuelve al brillo normal |
| R5 | Volver a la app | En ruta, salir con el botón de inicio del reloj y abrir Rumbo (o tocar la notificación) | Abre directo en "En ruta", con la ruta que seguía |
| R6 | El proceso muere | En ruta, `adb shell am force-stop com.rumbo.watchapp` y abrir Rumbo | Inicio muestra primero "Continuar ruta anterior" con el destino y la voz lo dice. Al tocarlo vuelve a "En ruta" desde donde esté el usuario, sin un falso "Fuera de ruta" |
| R7 | Terminar | En ruta, tocar "Terminar" | Vuelve a Inicio al instante; Logcat `Vibracion: Detenida`, `Nav: Navegación terminada` y `Servicio detenido`; desaparece la notificación; `adb shell dumpsys location` muestra `gps provider request = ProviderRequest[OFF]`; no se ofrece "Continuar ruta anterior" |
| R8 | Volver en ruta | En ruta, gesto de volver | Pregunta "¿Terminar la ruta?" con "Seguir navegando" (primero) y "Terminar"; volver otra vez sigue navegando |
| R9 | Repetir | En ruta, tocar "Repetir" | Vuelve a vibrar el último patrón y dice la indicación completa ("En ... metros, gire ...") |

### Lugares guardados

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| L1 | Guardar | En Confirmar tocar "Guardar" | "Guardando..." → "Guardado". Sin internet: "No pude guardar el lugar" en pantalla y voz |
| L2 | Elegir | Inicio → "Lugares guardados" → tocar un lugar | Pide ubicación si falta, "Procesando" y luego Confirmar con el lugar ya marcado "Guardado" |
| L3 | Borrar | Mantener pulsado un lugar → "Borrar" | Pregunta "¿Borrar «X»?" (Cancelar / Borrar); al borrar, la lista se actualiza. Volver desde la pregunta regresa a la lista |
| L4 | Lista vacía | Borrar todos | "Aún no tienes lugares guardados" |

### Vibraciones

| # | Prueba | Pasos | Resultado esperado |
|---|---|---|---|
| B1 | Guía | Inicio → "Vibraciones" → tocar cada patrón | Recto: 1 pulso suave cada 6 s; Giro próximo: 2 pulsos muy cortos; Izquierda: 2 pulsos medios; Derecha: 1 pulso largo; Desviado: 3 pulsos largos con pausas cortas; Sin señal: 1 pulso muy largo cada 5 s; Llegada: 5 pulsos crecientes. Cada uno se nota distinto (en el reloj) y en el emulador se ve en Logcat y en las barras animadas |
| B3 | Reloj sin control de intensidad | Abrir la app y mirar `adb logcat -s Vibracion` | Al arrancar: `Motor de vibración: ...; control de intensidad: sí/no → patrones con amplitud / solo con ritmo y duración`. Sin control, la guía muestra "Recto: 1 pulso corto cada 6 s" y "Llegada: 5 pulsos cada vez más largos", y cada patrón se distingue solo por cuántos pulsos tiene y cuánto duran |
| B4 | Soporte de intensidad | Abrir "Vibraciones" | Arriba "Intensidad: sí" con "El reloj varía la fuerza" (emulador) o "Intensidad: no" con "Se distinguen por ritmo y duración". Coincide con la línea `control de intensidad` de Logcat |
| B5 | Barras con nombre | Tocar "Izquierda" | Vibra 2 pulsos medios; debajo del botón aparecen "Izquierda" y 2 barras que se encienden al ritmo del patrón. Logcat `Vibracion: Izquierda: 2 pulsos medios tiempos=[0, 300, 200, 300] ...` |
| B6 | Secuencia de demostración | Tocar "Secuencia de demostración" | Suenan los 7 patrones en orden (Recto, Giro próximo, Izquierda, Derecha, Desviado, Sin señal, Llegada); cada uno muestra "n de 7", su nombre en grande y sus barras; entre uno y otro, 2 s de pausa tras terminar (en Logcat, duración del patrón + 2 s entre líneas). Al final vuelve a la lista |
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
