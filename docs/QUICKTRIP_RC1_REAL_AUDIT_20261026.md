# Quick Trip RC1 real — auditoría de integración aislada

Base: main@53dd427d5bda65e84f585b4db2c565227aff673c
Rama: rc/quicktrip-rc1-real-20261026

ESTADO: candidata de código. No publicar ni compilar APK. No representa una
validación real de Samsung ni de la conexión física a Conductor.

## Compatibilidad auditada

- app.js, route-engine.js, address-engine.js, eta-engine.js, booking-contracts.js,
  rpc-client.js, premium-route.js y todos los módulos Android: preservados
  idénticos a main, con verificación estricta de blob SHA.
- quicktrip-qa-route-v90.js calculaba precio ilustrativo mediante OSRM demo.
  Excluido. RC1 depende exclusivamente de la ruta y tarifa oficial verificadas.
- quicktrip-qa-stage-b-v90.js creaba una reserva LOCAL-QA y estados ficticios.
  Excluido. No hay conductor virtual en RC1.
- quicktrip-qa-samsung-v87.js podía desactivar el CTA y ocultar disponibilidad.
  Excluido. El envío real continúa en el motor de main.
- Integradas solo las presentaciones aprobadas: tres accesos circulares,
  hoja canónica visual v86, diseño móvil compacto y botón GPS v89 (sin
  geolocalización automática). La propiedad CSS está en quicktrip-rc1.css.
- Tu Paquete se mantiene como próximamente. No se ofrece una APK separada
  para cliente o conductor. Solo Mapa QT con Conductor integrado.
- Precache PWA actualizado a versión RC1. Reservas offline deshabilitadas
  por el motor original; no se añaden reintentos de reservas.

## Contratos operativos que deben permanecer

- Tu Viaje inmediato: customer_request_pickup_fixed_v1, cuenta verificada,
  precio oficial route/quote, clave de idempotencia y recibo PENDIENTE;
  aceptar/rechazar pertenece al conductor.
- Tu Viaje Programado: customer_check_availability_v11_4 y
  customer_create_reservation_v12; no mezclar con solicitud inmediata.
- Disponibilidad y ETA del conductor: public_driver_availability_v1 y
  pickup-eta; nunca revelar coordenadas privadas ni inventar km/min.
- Mis traslados y seguimiento de estado: customer_list_reservations_v16,
  reservas autorizadas, estado de la DB; sin cambios en RPC ni sesiones.

## Pruebas previas a cualquier entrega

1. Compuerta Git diff y hashes de motores con respecto a main.
2. Tests Node de rutas, ETA, precio cerrado, autenticación, idempotencia,
   estados, mapa y reservas.
3. Chromium en localhost con TODOS los accesos externos interceptados:
   solicitud, login, recibo pendiente, visibilidad desde segundo almacén,
   aceptación/rechazo sintéticos y evidencia visual. Nunca POST real.
4. Contratos Java puros Mapa/Conductor, sin Android SDK, Gradle ni APK.
5. Posteriormente, antes de publicar: URL estable, CORS vivo, credenciales
   de ensayo autorizadas, reserva real de prueba explícitamente aprobada,
   Android físico que reciba la solicitud, aceptación y rechazo,
   PWA y Samsung. Este punto no está validado en el lote de código.
6. No merge, no publicación, no Supabase real, no cambios en main ni Android.

RIESGO CONOCIDO: las capturas originales exactas del diseño no están
almacenadas con hash accesible; la fidelidad final y el flujo Android físico
quedan pendientes de comprobación.
