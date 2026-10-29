# Rutas de demostración (GPX)

Recorridos a pie para probar la navegación en el emulador de Wear OS sin salir a la calle, y que
usan los tests del reloj. Todos siguen la ruta real que devuelve el backend (Geoapify,
`less_maneuvers`) de la **Plaza de Nariño al Parque Bolívar**: 1 791 m y 5 giros, guardada en
`ruta_parque_narino_a_parque_bolivar.json` (la respuesta de `POST /routes`).

Los recorridos son **realistas**, como el GPS de un reloj en la calle: una lectura por segundo,
ruido de σ ≈ 3 m por eje que cambia poco de un segundo al siguiente, velocidad entre 1,0 y
1,6 m/s, pausas de 2 a 5 s en cada cruce, precisión informada entre 5 y 15 m (en `<hdop>`:
precisión ≈ 5 m × HDOP), un salto de ~40 m cada ~200 lecturas y una pérdida de señal de 2 a 3 s.
Unos 23–24 minutos cada uno.

| Archivo | Semilla | Qué prueba |
|---|---|---|
| `ruta_parque_narino_a_parque_bolivar.gpx` | 11 | Recorrido normal: "En ruta", "Giro próximo", "Gira ahora" y "Llegaste", sin falsos desvíos ni giros pese al ruido. |
| `ruta_con_desvio.gpx` | 12 | A mitad de ruta se aleja 35 m durante ~40 m y vuelve: "Fuera de ruta" y recálculo. |
| `ruta_sin_senal.gpx` | 13 | A mitad de ruta hay un hueco de 15 s sin puntos: "Sin señal GPS" y recuperación en los tests. En el emulador no basta (ver abajo). |
| `ruta_parque_narino_a_parque_bolivar.json` | — | La ruta del backend (pasos, calles y polilínea) con la que los tests recorren los GPX. |

## Cargar una ruta en el emulador

1. En la barra lateral del emulador abre **⋯ (Extended controls) → Location → Routes**.
2. Pulsa **Import GPX/KML** y elige uno de los archivos de esta carpeta.
3. Pon el reloj en el inicio del recorrido **antes de pedir la ruta**: la app no inventa el
   origen y, sin posición, se queda en "Buscando señal GPS" (40 s y luego avisa). En
   **Location → Single points** escribe `1.214997, -77.278419` y pulsa **Set location**, o
   desde la terminal: `adb emu geo fix -77.278419 1.214997` (primero la longitud). Para repetir
   la demo, cierra y vuelve a abrir la app antes de poner la posición: con la app en Inicio el
   GPS del emulador no se actualiza y la ruta sale desde donde terminó la anterior ("A 2 metros").
4. En la app, toca el micrófono y di "llévame al Parque Bolívar" (acepta micrófono, ubicación
   y notificaciones). Confirma con **Ir**.
5. Vuelve a Extended controls y pulsa **Play route**. Puedes subir la velocidad de reproducción
   para no esperar 23 min; a velocidades altas los avisos llegan más seguidos.

El GPX empieza donde empieza la ruta del backend (el punto de la calle más cercano a la plaza),
así que la ruta que pide la app coincide con el recorrido. El emulador ignora la precisión de
cada punto (`<hdop>`); la usan los tests, que pasan cada lectura por el mismo filtro del reloj.
Si el backend en Render aún no tiene la preferencia `less_maneuvers`, la ruta que pide la app es
otra y el recorrido se ve "fuera de ruta": despliega el backend antes de la demo.

## Probar "Sin señal" en el emulador

El emulador sigue informando la última posición cuando el GPX no trae puntos, así que el hueco de
`ruta_sin_senal.gpx` no corta la señal (en los tests sí). Para verlo, durante cualquier ruta
apaga la ubicación unos 15 s y vuelve a encenderla:

```
adb shell cmd location set-location-enabled false
adb shell cmd location set-location-enabled true
```

A los 10 s pasa a "Sin señal GPS" (gris, "Buscando señal GPS", 1 pulso muy largo cada 5 s) y, al
encenderla, en unos 3 s vuelve a "En ruta" con "Señal recuperada.".

## Ver las vibraciones en Logcat

El emulador no vibra; cada patrón se registra con la etiqueta `Vibracion`. En Android Studio,
en **Logcat**, filtra con:

```
tag:Vibracion
```

O desde la terminal:

```
adb logcat -s Vibracion
```

Las peticiones al backend (y si el servidor está despertando o no hay internet) salen con la
etiqueta `Red`, y cada cambio de estado de la navegación (En ruta → Giro próximo → ..., recálculos,
inicio y fin) con `Nav`. Todo junto:

```
adb logcat -s Nav Vibracion Red
```

La pantalla "En ruta" también muestra el último patrón (barras y texto, p. ej. "2 pulsos muy cortos").

## Ver las rutas en un mapa

`previews/` tiene 5 rutas de prueba desde la Plaza de Nariño (Parque Bolívar, Plaza del Carnaval,
Terminal de Transportes, Universidad de Nariño y Hospital Departamental) dibujadas sobre
OpenStreetMap con cada paso numerado: abre `previews/index.html` y compáralas con Google Maps (cada
una tiene el enlace). Para regenerarlas o ver otra ruta:

```
backend\venv\Scripts\python tools\preview_route.py --demo [--backend URL]
backend\venv\Scripts\python tools\preview_route.py 1.214628,-77.278301 1.205584,-77.270279 --nombre "Parque Bolívar"
```

## Generar otras rutas

```
backend\venv\Scripts\python tools\route_to_gpx.py 1.214628,-77.278301 1.205584,-77.270279 "Parque Bolívar" docs\demo\ruta.gpx [--desvio] [--sin-senal] [--perfecto] [--semilla N] [--ruta ruta.json] [--backend URL]
```

- `--perfecto`: sin ruido, pausas, saltos ni cortes; 1,25 m/s constantes y precisión de 5 m.
- `--semilla N`: el mismo recorrido cada vez (sin ella se elige una al azar y se imprime).
- `--ruta archivo.json`: guarda también la ruta del backend.

Los tres GPX de esta carpeta se generaron así, con el backend local:

```
backend\venv\Scripts\python tools\route_to_gpx.py 1.214628,-77.278301 1.205584,-77.270279 "Parque Bolívar" docs\demo\ruta_parque_narino_a_parque_bolivar.gpx --semilla 11 --ruta docs\demo\ruta_parque_narino_a_parque_bolivar.json --backend http://127.0.0.1:8765
backend\venv\Scripts\python tools\route_to_gpx.py 1.214628,-77.278301 1.205584,-77.270279 "Parque Bolívar" docs\demo\ruta_con_desvio.gpx --semilla 12 --desvio --backend http://127.0.0.1:8765
backend\venv\Scripts\python tools\route_to_gpx.py 1.214628,-77.278301 1.205584,-77.270279 "Parque Bolívar" docs\demo\ruta_sin_senal.gpx --semilla 13 --sin-senal --backend http://127.0.0.1:8765
```

El backend en Render puede tardar hasta un minuto en despertar la primera vez.
