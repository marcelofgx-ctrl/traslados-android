# Traslados v52 / Conductor R4 — mensajes y avisos interactivos

## Objetivo
Dentro de **Mis traslados**, el pasajero recibe presupuestos, responde aceptar/rechazar, sigue eventos En camino/Llegué, y ahora también puede conversar directamente con el conductor en el contexto de cada reserva. Se preservan los diseños petróleo/dorado y la semántica de estados validados por el servidor.

## Implementación real
- Migración backend `traslados-web/supabase/migrations/20261011_reservation_chat_v1.sql` con tabla privada `reservation_chat_messages`, RLS habilitado y permisos de tabla revocados; el acceso ocurre únicamente por funciones `SECURITY DEFINER` con validación de sesión/token invitado o PIN.
- `customer_trip_chat_v1` / `customer_trip_chat_send_v1`: lectura y envío de mensajes de la propia reserva; límites 800 caracteres / 15 mensajes por 10 minutos, identificador UUID idempotente, se prohíbe escribir en reservas cerradas.
- `driver_trip_chat_v1` / `driver_trip_chat_send_v1`: lectura y envío de conductor con PIN válido; antispam y respuestas repetidas idempotentes.
- `customer_list_reservations_v15` y `get_reservation_by_token_v4` enriquecen la respuesta sin romper versiones previas con el último ID/fecha de mensaje del conductor. `driver_trip_chat_feed_v1` alimenta monitor nativo de mensajes de pasajeros con PIN.
- PWA v52 agrega conversación plegable **dentro de cada reserva**, burbujas diferenciadas y botón Enviar. Textos insertados exclusivamente con `textContent` para impedir inyección HTML, conservando texto escrito durante refrescos de lista. La bandeja se actualiza al abrir el desplegable y al renovar la lista cuando está visible.
- Popup foreground existente ahora alerta cambios de último mensaje desde el backend; la vibración es opcional, sonido breve con Web Audio **solo al habilitarlo voluntariamente y si el navegador autoriza reproducción**. Primera carga establece línea base sin alertar mensajes antiguos.
- Conductor v11.5-R4 / versionCode 126 agrega «Mensajes del pasajero», diálogo nativo de lectura y respuesta dentro de la reserva, y un canal de notificación Android `Mensajes de pasajeros` con sonido/vibración gestionados por los ajustes del teléfono. El monitor actual revisa novedades cada 15 segundos **mientras su servicio está activo**. Si está apagado/cerrado no se garantiza recepción.
- No se exponen coordenadas del conductor, PIN, tokens ni credenciales en mensajes o notificaciones de pantalla bloqueada.

## Diferencia con push auténtico
- **PWA y Cliente Premium v15 WebView:** el popup/audio/vibración v52 funcionan con app abierta; una WebView sin FCM integrado no puede garantizar push cerrado. Mantener texto visible indicando ese límite.
- **Conductor R4:** usa el servicio activo de reservas y canal nativo; NO es un servidor push FCM.
- Para completar push verdadero: infraestructura Web Push para PWA Chrome y/o Firebase Cloud Messaging para APK Android, claves VAPID/FCM exclusivas del backend, registro de dispositivos autorizado, consentimiento Android 13+, reintentos, revocación y verificación real con pantalla apagada. No guardar secretos privados en GitHub ni ponerlos en JavaScript público. No declarar activo hasta disponer de claves y confirmar entrega en Samsung.
- Estados presupuestarios y aceptación siguen sobre `customer_quote_decision_v13`; `pickup_phase` llega de Conductor y no se infiere con GPS. La conversación no modifica estados del viaje.

## QA
- No generar reservas ficticias ni mensajes reales en producción para pruebas de CSS.
- Tests: `customer-chat-v52-smoke.cjs` más suites PWA; Conductor R4 DEBUG antes de RELEASE firmado y revisión APKSigner; migración SQL verificada con introspección read-only.
- Validación física pendiente con dos dispositivos: cliente autenticado/invitado, conductor PIN, ida/vuelta de mensajes, lectura tras reiniciar, popup/sonido/vibración habilitados, bloqueo, conexión inestable, duplicación de envíos y navegación al presupuesto.
- Estado de implementación, CI y publicación se debe completar tras verificar workflows, no asumir `SUCCESS` por editar el repositorio.
