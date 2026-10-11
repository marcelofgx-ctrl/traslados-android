# PWA v44 — Identificación visible y acción de solicitud sin ambigüedad (11/10/2026)

## Motivo
Captura Samsung en modalidad **Ahora** mostraba nombre y teléfono manuales y un gran enlace de WhatsApp donde el cliente esperaba un botón de envío. La sesión autenticada, cuando existía, ocultaba por completo los campos de contacto y no mostraba un resumen de los datos de la cuenta.

## Cambios
- Paso 3: con sesión válida, tarjeta **DATOS DE MI CUENTA** con nombre y teléfono tomados de `session.customer` y acceso «Ver mi cuenta». No almacena valores en HTML ni divulga la sesión; se limpia de pantalla al cerrar sesión.
- Invitado: los mismos campos manuales, sin cambios del contrato de `bookingData` ni del número de pasajeros.
- Ahora/En 10 min: tarjeta de **RECOGIDA INMEDIATA**, explica que hoy el backend no registra esas consultas como reservas y conserva el enlace WhatsApp. Ofrece otra vía clara: ir al modo Programar y completar una solicitud desde la aplicación.
- Programar: se conserva la revisión de datos y el botón real **CONFIRMAR SOLICITUD DE RESERVA**. Solo este camino llama a las RPC de reservas autenticadas/invitado; no se inventa un registro al cambiar de pestaña.
- PWA `app.js?v=44`, CSS `v=44`, Service Worker `v44`; misma PWA en la APK Cliente Premium v15 WebView.
- Tests añadidos `account-summary-smoke.cjs`; regresiones de urgentes, formulario y caché actualizadas. No cambia Supabase, Mapa, GPS, ETA, firma o apps Android.

## Requisito funcional futuro: envío urgente dentro de Traslados
La diferencia actual es arquitectónica: `customer_create_reservation_v12` usa `customer_create_reservation_v11_4` con agenda y antelación mínima; `public_driver_availability_v1` y `pickup-eta` solo informan disponibilidad/ETA. Una solicitud urgente interna necesita un **contrato/RPC propio**, verificación de disponibilidad y conflictos en servidor, protección antiabuso e idempotencia, notificación y manejo de aceptación o rechazo desde Mapa, más pruebas E2E sin crear reservas reales de prueba. No modificar silenciosamente el horario para simular una reserva ahora ni llamar a una RPC programada fuera de sus reglas.

## QA necesario
1. Usuario logueado: paso 3 muestra nombre/teléfono en panel no editable; al salir, ocultar panel y borrar campos temporales; regresar a Mis cuenta desde tarjeta.
2. Invitado: nombre/teléfono editables y datos opcionales, sin acceder a un perfil ajeno.
3. Ahora y En 10 min: la consulta WhatsApp no afirma guardar reservas. El CTA Programar cambia modo, muestra fecha/hora y habilita envío solo luego de revisión.
4. Programar: RPC original sin cambios, respuesta pendiente, precio referencial y validación de campos.
5. Samsung físico: teclado, plegado, desplazamiento, sesión real, disponibilidad/ETA, navegación y cache/actualización de WebView. Tests automatizados no sustituyen QA móvil.

**Estado:** pendiente de CI/Pages en PR; actualizar después de comprobar resultados.
