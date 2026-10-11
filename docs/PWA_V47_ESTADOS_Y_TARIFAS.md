# PWA v47 — Estados seleccionados y claridad de estimaciones (11/10/2026)

## Origen del cambio
La captura real en Samsung mostró una franja oscura/retención de hover entre la cabecera «Tu recorrido» y la primera dirección consolidada. La tarjeta verde y la acción Editar también quedaron visualmente demasiado pegadas a los títulos. El usuario preguntó además por un presupuesto de $790 desde Arenal Grande 2274 a Aeropuerto de Carrasco y por la independencia del ETA de recogida.

## UI aplicada (sin cambios de arquitectura)
- En dispositivos sin hover, se elimina el estado :hover retenido del encabezado.
- La sección expandida obtiene separación visual positiva; las etiquetas «Origen»/«Destino» se separan de la tarjeta de dirección consolidada.
- Tarjeta de dirección seleccionada: borde refinado, fondo integrado al petróleo, acento verde de 3 px, más espacio interno, nombre largo que se ajusta, subtítulo tenue, botón Editar contenido. Se respeta la estética y no se modifica el buscador editable (v46).
- Al editar se quita la marca `is-selected` y se reabre el buscador sin borrar el resto del recorrido.
- Métricas renombradas «Distancia del viaje» y «Duración del viaje», con texto explicativo de que el valor orientativo no incluye el acercamiento del conductor.

## Motor real de precio
- La PWA solo muestra `referenceFareUyu` cuando `route-engine.js` verifica `distanceKm` y `durationMin` de ORS/HeiGIT o caché Supabase ROAD, según coordenadas de origen, paradas opcionales y destino seleccionados. Nunca presenta km ficticios en línea recta.
- El Worker `traslados-web/src/routes/api/public/route-estimate.ts` obtiene `referenceFareUyu` de `public_reference_quote_v1` en Supabase, cuya tarifa procede de `driver_settings.reference_rate_uyu_per_km`, configurable por RPC protegida. Comprobación read-only 11/10/2026: **40 UYU/km**.
- SQL aplica `greatest(10,round(p_distance_km * rate / 10)*10)`, por lo que $790 equivale aproximadamente a 19,75 km (no determina la distancia precisa sin el payload concreto). La distancia de aproximación del conductor NO integra este importe; tampoco peajes ni extras, ni tráfico en vivo.

## ETA de recogida independiente
- `eta-engine.js` llama a `public_driver_availability_v1` y a Edge `pickup-eta` con lat/lng del **origen del pasajero**. Solo el backend autorizado compara la posición reciente del conductor de Mapa y la agenda, devuelve km/ETA gruesos sin divulgar las coordenadas exactas al pasajero.
- Mantener disponibilidad 15 s / ETA 25 s en movimiento y 45 detenido / Mapa 15/25/30 s, Edge v5. No tocar Supabase, backend, credenciales, firmas, permisos ni Mapa R24.8.
- PWA `app.js?v=47`, `styles.css?v=47`, service worker `traslados-cliente-pwa-v47`; Cliente Premium Android v15 continúa siendo WebView del mismo sitio.

## Verificaciones
- `web-pasajero/tests/route-selected-v47-smoke.cjs` más todas las suites PWA existentes; revisión visual física Samsung todavía pendiente.
- No crear reservas reales para validar CSS, no prometer precisión de tránsito en vivo.
- PR y publicación aún por comprobar; actualizar el estado al recibir CI.
