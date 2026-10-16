# Tu viaje: precio cerrado y decisión directa del conductor (16-10-2026)

**Scope**: únicamente `Tu viaje` inmediato / recogida en 1–120 min. `Tu reserva` sigue con solicitud → presupuesto → aprobación del pasajero. Nunca mezclar ambos.

## Contratos
- La PWA PR99 y Cliente Premium **v26 / versionCode 214** verifican ruta por carretera y tarifa central, y envían `customer_request_pickup_fixed_v1`; si faltan ruta y precio reales, NO envían. Tarifa de referencia actual 40 UYU/km, parametrizable, no hardcoded.
- Nueva RPC devuelve `fare_mode=fixed`, `quote_status=PRECIO_FIJO`, `quote_final_total`, `route_distance_km`, `route_duration_min`, `public_token` para invitado. Congela tarifa del momento y evita reenviar con otros datos. El pasajero ve el mismo precio.
- Conductor **R9 / versionCode 131**, `driver_list_reservations_v4`, muestra nombre, origen, destino, km, duración, precio cerrado y acciones **ACEPTAR** / **RECHAZAR**. `driver_decide_fixed_pickup_v1` valida PIN, horario, agenda y anticolisión transaccional.
- ACEPTAR → `status=ACEPTADA`, `quote_status=ACEPTADO`, `confirmed_at`: el pasajero NO acepta segunda cotización. Seguidamente se ofrece **IR A RECOGER** y se conserva `driver_set_pickup_phase_v1` para comunicar `EN_CAMINO`, `EN_PUNTO`, `EN_VIAJE` y `FINALIZADA`. **Aceptar no implica que ya esté en camino**.
- RECHAZAR → `status=RECHAZADA`, `quote_status=RECHAZADO`. Las RPC antiguas de presupuesto/agenda permanecen para programados y APKs antiguas; el trigger impide aceptar por status legado un viaje con precio cerrado.
- Si el precio configurado cambió entre vista y envío, rechazar y exigir refrescar; si cambia luego del envío, reintento UUID devuelve el importe original.

## Backend y publicación
- Backend [PR #18](https://github.com/marcelofgx-ctrl/traslados-web/pull/18), migración `supabase/migrations/20261016070000_fixed_fare_immediate_driver_decision_v1.sql`. Verificar que esté aplicada realmente en proyecto `zetaudvvutlouiqxopvg` antes de instalar Cliente v26.
- Android/PWA frontend [PR #99](https://github.com/marcelofgx-ctrl/traslados-android/pull/99) **DRAFT**, mantenerlo separado de la web pública PWA v74, Conductor R8, Mapa R25.25.
- Compilación CI Android firmada no sustituye prueba física en Samsung. QA de SQL con `BEGIN/ROLLBACK` comprobó creación sintética, tarifa, protección contra modificación, idempotencia aun cambiando tarifa, PIN inválido y rechazo de transición de estado heredada. **Sin datos de pruebas persistentes**.
- Para probar de verdad, instalar **primero Conductor R9**, luego Cliente v26, crear una sola solicitud controlada y recorrer Conductor aceptar → Cliente confirmado → En camino → En punto → inicio → fin; verificar que no se pida aprobar presupuesto otra vez. No borrar datos de Cliente que contengan comprobantes de invitado.
- No usar `driver_set_status_v2` para aceptar/rechazar `fare_mode=fixed`. No presentar kilómetros en línea recta como distancia del traslado. Extras/peajes deben acordarse por separado: nunca modifican el precio cerrado sin consentimiento.

## Pendientes
- E2E físico bidireccional y calidad visual final. Si conductor no tiene última R9, la solicitud directa queda pendiente: desplegar de forma coordinada.
- El backend no obtiene por sí solo geometría de OpenRouteService para cada solicitud; valida source/ruta declarados por cliente, plausibilidad geográfica y calcula tarifa autoritativa. El conductor ve los kilómetros y precio y debe aceptarlos. Considerar firmado/vinculación de geometría a cache confiable antes de uso masivo.
