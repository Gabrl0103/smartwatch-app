# Actualizaciones del 21 de noviembre de 2026

Resumen de lo que se hizo en esta sesión, de `REQUIREMENTS.md` v0.52 a v0.61. El detalle de cada cambio está en el changelog de `docs/REQUIREMENTS.md`.

## 1. Mapa de cerca tipo GPS con calles reales

La pantalla "Ver mapa" pasó de una línea y un punto a un mapa con calles. Es solo presentación: el motor de navegación, los umbrales, las vibraciones, la guía hablada y los ViewModels no cambiaron.

- **Vista de cerca:** zoom 17 fijo, con el rumbo arriba (el rumbo del GPS suavizado) y la flecha fija un poco abajo del centro.
- **Encima del mapa:** la ruta en azul grueso con borde claro, lo recorrido atenuado, el destino, el próximo giro y la distancia al giro ("Giro en 40 m").
- **Atribución:** "© OpenStreetMap", "© OpenMapTiles" y "Geoapify", en tres líneas abajo a la derecha.
- **Vista "Ruta":** sigue siendo la ruta entera sin calles, con el norte arriba.
- **Tiles en el reloj:** caché en memoria y en disco (hasta 200 tiles), como mucho 4 peticiones por segundo y 2 a la vez. Al empezar cada ruta se precarga el corredor.
- **Sin red:** si un tile no llega, esa zona se ve con el fondo oscuro y la ruta se sigue dibujando. El mapa nunca frena la guía.

## 2. Nombres de calle siempre derechos

Los tiles de Geoapify traen los nombres dibujados dentro de la imagen. Al girar el mapa, caminando hacia el sur se leían al revés.

- **Backend:** dibuja sus propios tiles oscuros, sin nombres y con el fondo de la app. Los arma a partir de los datos vectoriales de Geoapify, con un lector propio y Pillow.
- **Nuevo endpoint `GET /map/streets/14/{x}/{y}.json`:** devuelve las calles con su nombre.
- **Reloj:** escribe cada nombre girado como su calle, pero nunca más de 90°. Lo pone en el lugar libre más cercano a la persona, sin tapar la flecha, la etiqueta, la atribución ni los botones.
- **Créditos:** un tile vectorial cubre 64 tiles del mapa, así que la ruta de la demo bajó de unos 4 créditos de Geoapify a unos 0,75.

## 3. Destinos por tipo de lugar ("la droguería más cercana")

- **Antes:** "Llévame a la droguería más cercana" terminaba en "Estás muy lejos del destino", porque el buscador devolvía una droguería de Bogotá.
- **Ahora:** una frase que es solo un tipo de lugar busca el más cercano a menos de 2 km, con la búsqueda de lugares de Geoapify (1 crédito por búsqueda). Reconoce 15 tipos: droguería, cajero, banco, hospital, clínica, supermercado, tienda, panadería, restaurante, cafetería, baño, parque, parada de bus, policía y gasolinera.
- **Lugares con nombre:** se buscan solo a 50 km o menos.
- **Nombres que no existen:** si no se encuentra el nombre pedido, la app ya no lleva a otro lugar del mismo tipo. "Droguería Alemana" ya no da "Droguería Belalcazar": responde "No encontré «Droguería Alemana» cerca de ti".
- **Asistente:** ante "¿Dónde hay una droguería cerca?" ofrece llevar al lugar más cercano, en vez de aconsejar el bastón.

## 4. Pruebas

- **Automáticas:** 256 pytest y 512 JUnit, todas en verde, y lint sin errores.
- **Manuales del mapa:** M1 a M9, todas hechas contra Render. Incluyen desvío con recálculo, sin señal, llegada, sin internet y pantalla atenuada.
- **Órdenes de voz C3 y C9:** hechas. Guardar el destino en ruta muestra "Guardado ✓", que sigue tras recalcular y tras reabrir la app. Sin internet, la orden vibra "Error" y explica que no hay conexión.
- **En el reloj, contra Render:** probados el mapa con calles, los nombres derechos, la droguería más cercana y un nombre inexistente.

## 5. Demo en otro PC

- **ZIP:** `C:\Users\Administrator\Desktop\Rumbo-demo.zip` (10 MB). Trae la app ya compilada, accesos numerados (despertar el servidor, abrir Rumbo, simular la ruta) y la guía `LEEME.txt`.
- **Lanzador:** ahora funciona con cualquier emulador de Wear OS, aunque no se llame `Wear_OS_Large_Round`.
- **Sonido:** si el emulador no suena aunque la app hable, se perdió la salida de audio de Windows. Pasa al cambiar de parlantes con el emulador abierto. Hay que cerrar el emulador del todo y volver a abrirlo.

## 6. Pendiente

- **Voz de las órdenes:** escuchar en voz los resultados de C1 a C9 cuando haya cuota de Gemini TTS (10 frases al día).
- **Reloj real:** probar la voz propia del reloj y la pausa al grabar (A3 y A6), y la accesibilidad con TalkBack (R14).
- **Entrega:** grabar el video de sustentación y confirmar los requisitos del profesor.
