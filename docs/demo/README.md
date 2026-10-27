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
3. En la app, toca el micrófono y di "llévame al Parque Bolívar" (acepta micrófono, ubicación
   y notificaciones). Confirma con **Ir**.
4. Vuelve a Extended controls y pulsa **Play route**. Puedes subir la velocidad de reproducción
   para no esperar 23 min; a velocidades altas los avisos llegan más seguidos.

El GPX empieza en el mismo punto que el origen por defecto de la app (centro de Pasto), así que
la ruta calculada coincide aunque el emulador aún no tenga ubicación.

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

La pantalla "En ruta" también muestra el último patrón (barras y texto, p. ej. "2 pulsos cortos").

## Generar otras rutas

```
backend\venv\Scripts\python tools\route_to_gpx.py 1.2136,-77.2811 1.2056,-77.2703 "Parque Bolívar, Pasto" docs\demo\ruta.gpx [--desvio] [--sin-senal] [--backend URL]
```

El backend en Render puede tardar hasta un minuto en despertar la primera vez.
