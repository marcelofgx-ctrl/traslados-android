# PWA v57 — Journey Atelier: rediseño premium del formulario (11/10/2026)

## Encargo
El usuario aprobó rediseñar profesionalmente la experiencia del formulario de Traslados desde una captura real de Samsung: las tres etapas plegadas, recogida inmediata, CTA y textura, priorizando que **la tarjeta plegada muestre TODAS las direcciones completas y las tres métricas (km, duración y valor orientativo)**, no solo kilómetros.

## Cambios visuales
- **Tu recorrido plegado:** diseño de dos estaciones ORIGEN / DESTINO con línea vertical y puntos de orientación, nombres íntegros en varias líneas sin elipsis y contador de paradas intermedias. No se exponen nuevos datos; solo se muestran las direcciones que el cliente ya seleccionó.
- **Trío de métricas siempre a la vista si el recorrido está seleccionado:** distancia, duración, valor estimado, cada uno con su propio rótulo, sin desplazamiento horizontal. Si no hay ruta por carretera verificada muestra «—» / «A confirmar», nunca valores ficticios. Si se edita una parada, se invalida de inmediato cualquier tarifa anterior.
- **Tarjetas:** reducción del ruido visual, menor altura de las etapas 02 y 03, tonalidades petróleo con iluminación suave, checks completados como puntos discretos y números dorados con contraste.
- **Acción final (Ahora / En 10 min):** cabecera con icono vectorial integrado (sin dependencia de terceros), identidad dorado satinado, tarjeta contextual de km/min/valor y CTA más proporcionado. El servidor sigue fijando el horario y exigiendo confirmación humana.
- **Acción programada:** tarjeta equivalente de resumen de ruta antes de «Revisar y confirmar», sin sustituir el paso de revisión.
- **Responsivo:** diseño de tres columnas sobre el ancho disponible, nombres con salto de línea, ajustes a 360px y soporte de movimiento reducido. Conserva menú, pestañas, logotipo, marca y controles existentes.

## Contrato técnico protegido
- El JS `refreshJourneyComposition` alimenta tanto el recorrido plegado como los dos recuadros de acción, usando solo `latestRoad` si la llave exacta de ruta coincide y `TrasladosRoutes.verified(data)` es verdadera.
- No modifica backend, proveedores, base de datos, disponibilidad, método de pago, PIN, precio por km ni sincronización GPS.
- Mantiene AJAX, Google Maps, edición de puntos, hasta ocho paradas, inicio de sesión, autocompletado Uruguay, revisión y envío tanto urgente como programado.
- Son **resúmenes visuales**, no presupuestos finales ni confirmaciones. Tarifa orientativa se etiqueta como tal.
- `index.html` app/CSS v57; `sw.js` caché v57, para Chrome/PWA y APK Cliente v15 WebView usando URL compartida.
- Test `web-pasajero/tests/journey-composition-v57-smoke.cjs` verifica uso de direcciones largas sin truncar, km/min/precio, ambos modos, actualización por paradas y ausencia de cifras viejas.
- CI PWA y revisión compilan este test además de todas las regresiones existentes. Las pruebas visuales físicas en Samsung siguen pendientes.

## Operación de publicación
Seguir PR, comprobar Actions y Pages públicos. Solo luego declarar disponible v57. Un Chrome/PWA previo puede necesitar cerrar/reabrir o refrescar. No se reconstruye APK Cliente porque WebView consume la PWA. No se toca Mapa R24.9.

## Criterios de aceptación en Samsung
1. Elegir Arenal Grande 2274 → Aeropuerto Internacional de Carrasco y plegar etapa 01.
2. Ver ambos nombres enteros y las tres métricas separadas (sin elipsis ni desplazamiento lateral).
3. Abrir etapa 01 y editar destino, comprobar que las cifras antiguas desaparecen; elegir nuevo destino y esperar verificación vial real.
4. Cambiar Ahora / En 10 min / Programar y confirmar que el bloque final muestra el mismo recorrido y precio solo cuando están verificados.
5. Comprobar en pantallas pequeñas que el botón ENVIAR SOLICITUD y los elementos del menú son pulsables sin superposición de la web; las burbujas flotantes de Android pertenecen al sistema operativo.
6. No enviar una reserva real durante la prueba visual sin consentimiento expreso.
