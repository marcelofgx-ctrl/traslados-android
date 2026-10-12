# PWA v66 · Solicitudes inmediatas sin confirmación vencidas

**12 de octubre de 2026 · rama en revisión.** Continuidad del caso Arenal Grande → Aeropuerto, solicitud creada a las 13:38.

## Problema verificado

Las RPC autorizadas de lista Cliente v15 e invitado v4 no incluyen `pickup_mode`. La aceptación de una recogida inmediata en `customer_quote_decision_v13` tiene un plazo de **90 minutos desde que se creó la solicitud**, pero `PENDIENTE` sin presupuesto podía seguir mostrándose visualmente como «Pendiente» indefinidamente. «En 10 min» podría mostrar un presupuesto inaceptable por diez minutos adicionales al tener solamente la hora de recogida.

## Corrección

- `trip-board.js` clasifica de forma conservadora como inmediata la petición cuya hora de recogida (fijada por Supabase) está a 0–12 minutos de su creación, tolerando -2 minutos por precisión temporal. `pickup_mode` explícito tiene prioridad cuando está presente.
- Al vencer los 90 minutos, `PENDIENTE` sin presupuesto aparece como **«Solicitud vencida»** y pasa a Historial visual (sin cancelar ni modificar nada en Supabase). El detalle aclara que no fue confirmado y muestra **Solicitar nuevo traslado**.
- Un presupuesto inmediato `ENVIADO` vencido tampoco ofrece Aceptar/Rechazar, porque el servidor ya rechazaría una aceptación.
- «Solicitar nuevo traslado» prepara el formulario en modalidad **Ahora**, conserva las direcciones y nunca envía automáticamente. Las reservas programadas, canceladas, aceptadas y finalizadas mantienen su comportamiento.
- `app.js?v=66`, `styles.css?v=66` (mismo CSS), `trip-board.js?v=6`, service worker `traslados-cliente-pwa-v66`. `owned-pickup.js` de v65, el diseño orbital, el chat y la ETA siguen intactos.
- Prueba `expired-urgent-v66-smoke.cjs` incorporada a CI; se preservan las pruebas anteriores. No se crea ni altera ninguna reserva real.

## Límites

El estado `PENDIENTE` del servidor permanece como estaba; esta mejora únicamente clasifica el vencimiento en la vista del pasajero. La APK **Conductor 11.5-R5** continúa leyendo el estado operativo real y puede requerir una mejora posterior de advertencia de vencimiento al preparar presupuestos. **No se modificó ni recompiló la APK firmada**, tampoco Mapa R25.0, Cliente Premium v15 WebView, ni RPC o GPS.

## QA Samsung

Ver una reserva inmediata sin respuesta de más de 90 minutos: «Solicitud vencida» en Historial; «Solicitar nuevo traslado» abre Reservar con origen/destino anteriores, sin producir un pedido nuevo hasta tocar Enviar. Comprobar una inmediata de menos de 90 minutos y una programada futura; asegurar que no se muestren vencidas. Las pruebas físicas todavía no están completadas. Las notificaciones con Cliente completamente cerrado siguen pendientes de FCM.
