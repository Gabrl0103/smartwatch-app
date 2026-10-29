# Rutas de demostración (GPX)

Recorridos a pie reales (calculados por el backend con Geoapify) para probar la navegación en el
emulador de Wear OS sin salir a la calle. Todos van del centro de Pasto (1.2136, -77.2811) al
Parque Bolívar (1.2056, -77.2703): unos 1 970 m, un punto cada ~5 m a 1,4 m/s (~23 min).

| Archivo | Qué prueba |
|---|---|
| `ruta_parque_narino_a_parque_bolivar.gpx` | Recorrido normal: "En ruta", "Giro próximo", "Gira ahora" y "Llegaste". |
| `ruta_con_desvio.gpx` | A mitad de ruta se aleja 35 m durante ~40 m y vuelve: "Fuera de ruta" y recálculo. |
| `ruta_sin_senal.gpx` | A mitad de ruta hay un hueco de ~15 s sin puntos: "Sin señal GPS" y recuperación. |

## Cargar una ruta en el emulador

1. En la barra lateral del emulador abre **⋯ (Extended controls) → Location → Routes**.
2. Pulsa **Import GPX/KML** y elige uno de los archivos de esta carpeta.
3. Pon el reloj en el inicio del recorrido **antes de pedir la ruta**: la app ya no inventa el
   origen y, sin posición, se queda en "Buscando señal GPS" (40 s y luego avisa). En
   **Location → Single points** escribe `1.2135070, -77.2813010` y pulsa **Set location**, o
   desde la terminal: `adb emu geo fix -77.2813010 1.2135070` (primero la longitud).
4. En la app, toca el micrófono y di "llévame al Parque Bolívar" (acepta micrófono, ubicación
   y notificaciones). Confirma con **Ir**.
5. Vuelve a Extended controls y pulsa **Play route**. Puedes subir la velocidad de reproducción
   para no esperar 23 min; a velocidades altas los avisos llegan más seguidos.

El GPX empieza junto al centro de Pasto, así que la ruta calculada coincide con el recorrido.

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

La pantalla "En ruta" también muestra el último patrón (barras y texto, p. ej. "2 pulsos cortos").

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
backend\venv\Scripts\python tools\route_to_gpx.py 1.2136,-77.2811 1.2056,-77.2703 "Parque Bolívar, Pasto" docs\demo\ruta.gpx [--desvio] [--sin-senal] [--backend URL]
```

El backend en Render puede tardar hasta un minuto en despertar la primera vez.
