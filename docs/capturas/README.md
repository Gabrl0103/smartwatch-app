# Capturas del reloj

Pantallas de Rumbo en el emulador **Wear OS Large Round** (454 × 454 px, 320 dpi, API 34),
recortadas en círculo como se ven en el reloj. Se dibujan con datos de ejemplo desde
`watchapp/app/src/androidTest/.../ScreenshotsTest.kt`; las que terminan en `_abajo` muestran la
misma pantalla desplazada hasta el final.

| Archivo | Pantalla |
|---|---|
| `01_inicio.png`, `02_inicio_abajo.png` | Inicio: "Listo para navegar", micrófono, Lugares guardados, Vibraciones, Voz: sí/no |
| `03_escuchando.png`, `26_escuchando_abajo.png` | Escuchando (diseño A2): anillo, badge relleno, barras con el nivel de la voz, "Te escucho"; abajo "Di a dónde quieres ir" y Cancelar |
| `04_procesando.png` | Procesando (el indicador gira en la app; en la captura queda quieto) |
| `05_confirmar.png`, `06_confirmar_abajo.png` | Confirmar destino: Ir / Cambiar / Guardar |
| `27_confirmar_hablando.png` | Confirmar mientras la app dice el destino: el badge se funde en barras que se mueven |
| `07_error.png` | Error: Reintentar / Inicio |
| `08_en_ruta.png`, `09_en_ruta_abajo.png` | En ruta, con distancias, vibración, Repetir y Terminar |
| `10_giro_proximo.png` | Giro próximo |
| `11_gira_ahora.png` | Gira ahora |
| `12_fuera_de_ruta.png` | Fuera de ruta |
| `13_sin_senal.png` | Sin señal GPS |
| `14_llegaste.png` | Llegaste |
| `15_terminar_ruta.png` | "¿Terminar la ruta?" tras el gesto de volver en ruta |
| `16_lugares_guardados.png`, `17_borrar_lugar.png` | Lugares guardados y borrar |
| `18_vibraciones.png` | Probar vibraciones: "Intensidad: sí", ajustes de intensidad y duración, Secuencia de demostración y cada patrón con su descripción |
| `19_componentes.png` | StateBadge, ProgressRing, StateIcon (todos), VibrationIndicator y botón |
| `20_permiso_ubicacion.png` | Permiso necesario (ubicación negada): Abrir ajustes / Inicio |
| `21_buscando_gps.png` | Procesando mientras espera una lectura GPS buena |
| `22_continuar_ruta.png` | Inicio con "Continuar ruta anterior" tras cerrarse la app a mitad de ruta |
| `23_vibraciones_sin_intensidad.png` | Probar vibraciones en un reloj sin control de intensidad ("Intensidad: no", patrones solo por ritmo) |
| `24_demostracion_vibraciones.png` | Secuencia de demostración: "3 de 7", el nombre en grande y sus barras |
| `25_indicacion_larga.png` | Giro próximo con la indicación real más larga de la demo: baja a 16 sp para leerse entera en el círculo |

## Regenerarlas

Con el emulador abierto, desde la raíz del repo:

```
bash tools/capture_screens.sh
```

Instala la app y el test con `adb install -r` (no borra los datos de la app), corre
`ScreenshotsTest` y baja las imágenes a esta carpeta. Si `adb` no está en el PATH:
`ADB=/ruta/a/platform-tools/adb bash tools/capture_screens.sh`.
