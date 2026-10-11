# Traslados PWA v49 — Solicitud de recogida ahora / en 10 minutos

## Motivo y comportamiento
Captura Samsung 11/10/2026, 02:11: al seleccionar «Ahora», la PWA mostraba una tarjeta de WhatsApp y «Elegir fecha y hora»; el usuario esperaba **«Enviar solicitud»** sin calendario. No era un problema visual sino de contrato de backend.

## Arquitectura implementada
- **Ahora**: desde las direcciones verificadas, pasajeros y datos del cliente, botón **ENVIAR SOLICITUD**. La hora local la fija el **servidor Supabase** al registrar la petición; nunca se toma del navegador ni de los campos ocultos.
- **En 10 min**: mismo botón, el servidor suma diez minutos a su reloj Uruguay en el momento del envío.
- **Programar**: conserva sus reglas existentes, selección de fecha y hora, validación de agenda, revisión y RPC `customer_create_reservation_v12` o `create_reservation`, sin atajos.
- Se retiraron los enlaces de WhatsApp del formulario, la tarjeta ETA y pie de página. Se conservó contacto telefónico y el buscador.
- `booking-contracts.js` incorpora la función pura `immediatePickup()`, separada del contrato programado.
- `customer_request_pickup_v1` en Supabase valida coordenadas/datos, sesión autenticada o invitado, límites por contacto y contra repetición, ID UUID idempotente y modo. La nueva reserva ingresa con estado **PENDIENTE** a la misma tabla que lee Conductor; el monitor actual de Conductor consulta novedades cada 15 segundos si está activo.
- Tras respuesta válida, muestra **Solicitud enviada**, código de reserva y la hora **servidor** de recogida; acceso a Mis traslados. Una consulta ambigua se puede reintentar con el mismo UUID, sin duplicar reservas por segundo toque.
- Si el pasajero no inició sesión, conserva `public_token` en el almacenamiento privado local de reservas invitado. Con sesión, el backend vincula directamente `customer_id`.
- La tarjeta de disponibilidad/ETA continúa consultando por separado y **NO bloquea** solicitudes cuando hay un estado transitorio desconocido: enviar solo significa solicitar; no asegura conductor disponible ni viaje confirmado.

## Compatibilidad
- Conductor existente usa `driver_list_reservations_v2`, que ya incluye PENDIENTE, y su servicio de monitorización registra solicitudes nuevas; no necesita migrar otro backend.
- Mapa Trayectos R24.8, GPS 15/25/30 s, Edge pickup-eta v5, disponibilidad 15 s, ETA 25/45 s y autenticación previa: intactos.
- Cliente Premium v15 WebView consume JS/CSS/PWA v49 publicados; sin cambios de firma.
- Mantener estética petróleo/dorado, 3 tarjetas plegables, lectura accesible y un solo CTA de envío para urgentes.

## Seguridad y alcance real
- El backend exige coordenadas válidas, nombre/teléfono, límites por cliente y token de sesión si se presenta; no permite fechas aportadas por el cliente en urgencias y evita duplicados de `request_id`.
- La petición puede llegar estando el conductor desconectado; **no es una aceptación**, no se promete aviso push garantizado si la app Conductor está cerrada o el monitor no está en ejecución.
- Los pasajeros deben ver «pendiente» hasta que el conductor actúe. La creación de un presupuesto y aceptación/rechazo sigue el circuito actual de Conductor.
- La observabilidad de fluctuaciones GPS sigue como issue #43; no confundir con este cambio.
- No se crearon reservas ficticias ni se probaron envíos reales con teléfonos/clientes de producción.

## Migración y entrega
- Migración rastreable en `marcelofgx-ctrl/traslados-web/supabase/migrations/20261011_customer_request_pickup_v1.sql`, aplicada al proyecto Supabase en transacción con resultado SUCCESS; esquema y permisos verificados con consultas read-only.
- Tests de contrato sin red, workflow PWA, revisión y compilación Android DEBUG. QA manual Samsung pendiente hasta que el usuario pruebe un recorrido real autorizado.
- **PUBLICADA:** [PR #44](https://github.com/marcelofgx-ctrl/traslados-android/pull/44) fusionada (`35b8bd1`); PWA [38114631548](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38114631548), revisión [38114631562](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38114631562), Android Cliente v15 DEBUG [38114631559](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38114631559), Pages [38114710058](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38114710058) y backend CI [38114641373](https://github.com/marcelofgx-ctrl/traslados-web/actions/runs/38114641373): **SUCCESS**. Contrato backend PR #9 fusionada (`09822e0`) y migración Supabase aplicada con éxito. **No se ejecutó envío real físico en Samsung.**
