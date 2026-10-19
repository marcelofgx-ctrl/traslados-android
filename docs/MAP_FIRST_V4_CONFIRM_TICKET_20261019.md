# V4 — Confirmación como pase de viaje, no formulario largo
Fecha: 19/10/2026. Solo `web-pasajero/pruebas/map-first/` y tests asociados.

## Por qué V1–V3 se sentían iguales
V1, V2 y V3 reutilizaron prácticamente la misma estructura visual del formulario e introdujeron más reglas CSS. Eso mejoró legibilidad y mapa, pero mantuvo el mismo patrón de múltiples tarjetas, bloques de texto e información repetida. El problema era **arquitectura visual**, no gama cromática. La referencia aprobada por el usuario colocaba una decisión por pantalla y contenido contextual compacto.

## V4: cambio real de composición
La pantalla **Confirmar** se presenta ahora como un único billete/pase de traslado (ticket) que contiene:
- cabecera editorial premium con estado «SIN ENVIAR», sin simular aceptación;
- el precio de servidor protagonista junto a los kilómetros y tiempo exactos del viaje actual;
- separador visual tipo perforación (no decoraciones excesivas);
- itinerario real con origen, paradas y destino, editables mediante botón existente;
- horario elegido y pasajeros desde controles actuales;
- el control original de solicitar viaje (pero bloqueado por el modo de pruebas);
- letra secundaria plegable y notas de aceptación, sin texto duplicado.

La composición se construye **moviendo** los elementos existentes `#urgent-route-recap`, `.premium-confirm-route`, `#urgent-send` y sus notas; no se clonan ni reescriben estados de servidor.

## Estilo V4
Base petróleo profundo y verde calmo, superficies con textura muy tenue, champagne para importe principal y botón, bordes limpios, sombras suaves, resalte legible. A diferencia de V3, es un componente propio y no un enésimo retoque de las tarjetas heredadas.

## Seguridad e invariantes
- Operación opt-in con `?mapfirst=1` + meta `traslados-preview`.
- No editar `web-pasajero/index.html`, `app.js`, `premium-journey.js`, `premium-route.js`, Supabase, APK Mapa, GPS, precios ni reservas.
- Service worker aislado exclusivamente a `web-pasajero/pruebas/map-first/`.
- Sin reservas reales ni mensajes externos desde la vista de ensayo.
- Precio y tiempo salen del mismo motor que antes; jamás inventar montos ni posiciones GPS.
- QA local adicional `tests/map-first-v4-review.cjs` en CI PWA.
- PWA de ensayo identificada como «Traslados V4» manteniendo el mismo scope de preview; no sustituye PWA productiva.

## QA requerida y pendientes
- Recorrido aeropuerto → destino con 0/1/2 paradas, verificar que ticket refleje exactamente valores y nombres originales.
- Confirmación con horario ahora/10 min/otra hora y varios pasajeros.
- Regresar a editar desde confirmar: las direcciones, paradas y precio permanecen consistentes.
- Pantallas Samsung 360, 393 y 412px sin reboses; lector de pantalla/teclado.
- Verificar expresamente «NO ENVÍA RESERVAS» en la vista experimental.
- Evaluación comparativa en móvil V3 frente a V4 y aprobación visual del usuario antes de incorporar a la PWA productiva.
- Seguimiento del conductor en «Mis traslados» sigue como fase separada; aquí no se declara reparado.

**Estado:** PR experimental; no producción.