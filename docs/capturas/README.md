# Capturas del reloj

Pantallas de Rumbo en el emulador **Wear OS Large Round** (454 × 454 px, 320 dpi, API 34),
recortadas en círculo como se ven en el reloj. Se dibujan con datos de ejemplo desde
`watchapp/app/src/androidTest/.../ScreenshotsTest.kt`; las que terminan en `_abajo` muestran la
misma pantalla desplazada hasta el final.

| Archivo | Pantalla |
|---|---|
| `01_inicio.png`, `02_inicio_abajo.png` | Inicio: dial en reposo, "¿A dónde vamos?", micrófono en disco azul, "Toca y di tu destino"; abajo Lugares guardados, Vibraciones, Voz: sí/no |
| `03_escuchando.png`, `26_escuchando_abajo.png` | Escuchando: anillo lleno y bisel encendidos, insignia, barras con el nivel de la voz, "Te escucho", "Di a dónde quieres ir"; abajo Cancelar |
| `04_procesando.png` | Procesando (el indicador gira en la app; en la captura queda quieto) |
| `05_confirmar.png`, `06_confirmar_abajo.png` | Confirmar destino: insignia "Destino", nombre, distancia y minutos con íconos; abajo Ir / Hablar / Cambiar / Guardar |
| `27_confirmar_hablando.png` | Confirmar mientras la app dice el destino: la píldora "Leyendo destino" con las barras de voz |
| `07_error.png` | Error: Reintentar / Inicio |
| `08_en_ruta.png`, `09_en_ruta_abajo.png` | En ruta: anillo que se llena hacia el giro con el punto en la punta, marcas encendidas, disco con la maniobra e indicación; abajo Repetir, Preguntar, Ver mapa y Terminar |
| `10_giro_proximo.png` | Giro próximo: disco con la maniobra y "30 m" en grande |
| `11_gira_ahora.png` | Gira ahora |
| `12_fuera_de_ruta.png` | Fuera de ruta: anillo a trazos y fondo teñido |
| `13_sin_senal.png` | Sin señal GPS: anillo punteado y disco con aros |
| `14_llegaste.png` | Llegaste: anillo lleno, disco verde con el check, destino y largo de la ruta; fondo teñido |
| `38_llegaste_nombre_largo.png` | Llegaste con un destino real largo: el nombre, en dos líneas, espera abajo en vez de pisar el anillo |
| `15_terminar_ruta.png` | "¿Terminar la ruta?" tras el gesto de volver en ruta |
| `16_lugares_guardados.png`, `17_borrar_lugar.png` | Lugares guardados y borrar |
| `18_vibraciones.png` | Probar vibraciones: "Intensidad: sí", ajustes de intensidad y duración, Secuencia de demostración y cada patrón con su descripción |
| `19_componentes.png` | Fondo radial, Dial (bisel y anillo al 60 %), StatusPill, GlassDisc, RoundAction, VibrationPill y todos los StateIcon |
| `20_permiso_ubicacion.png` | Permiso necesario (ubicación negada): Abrir ajustes / Inicio |
| `21_buscando_gps.png` | Procesando mientras espera una lectura GPS buena |
| `22_continuar_ruta.png` | Inicio con "Continuar ruta anterior" tras cerrarse la app a mitad de ruta |
| `23_vibraciones_sin_intensidad.png` | Probar vibraciones en un reloj sin control de intensidad ("Intensidad: no", patrones solo por ritmo) |
| `24_demostracion_vibraciones.png` | Secuencia de demostración: "3 de 10", el nombre en grande y sus barras |
| `25_indicacion_larga.png` | Giro próximo con la indicación real más larga de la demo: se lee entera en el círculo |
| `28_asistente.png`, `30_asistente_abajo.png` | Asistente en ruta: insignia, la pregunta en burbuja gris y la respuesta en burbujas moradas, una por frase; abajo "Preguntar otra vez" y "Volver a la ruta" |
| `29_asistente_hablando.png` | Asistente mientras dice la respuesta: "Hablando" con las barras de voz |
| `31_pensando_respuesta.png` | Procesando mientras el asistente responde |
| `32_atencion_escaleras.png` | Paso desconocido: "Atención" con el ícono de advertencia y el texto de Geoapify |
| `33_mapa_en_ruta.png` | Mapa de cerca: la persona al centro con el rumbo arriba, lo recorrido en azul, lo que falta en claro y el próximo giro con un aro |
| `34_mapa_desviado.png` | Mapa, desviado: persona naranja, ruta resaltada y "Fuera de ruta" |
| `35_mapa_sin_senal.png` | Mapa, sin señal: persona gris en la última posición y "Sin señal" |
| `36_mapa_toda_la_ruta.png` | Mapa con toda la ruta: norte arriba, destino en verde y botón "Cerca" |
| `37_mapa_llegada.png` | Mapa, llegada: la persona sobre el destino y "Llegaste" |

## Regenerarlas

Con el emulador abierto, desde la raíz del repo:

```
bash tools/capture_screens.sh
```

Instala la app y el test con `adb install -r` (no borra los datos de la app), corre
`ScreenshotsTest` y baja las imágenes a esta carpeta. Si `adb` no está en el PATH:
`ADB=/ruta/a/platform-tools/adb bash tools/capture_screens.sh`.
