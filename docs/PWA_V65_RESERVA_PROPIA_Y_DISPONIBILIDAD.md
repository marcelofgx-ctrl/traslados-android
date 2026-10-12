# PWA v65 — Solicitud ya registrada y disponibilidad contextual

**12/10/2026, 14:24 UYT · Cambio en revisión.**

## Hallazgo verificado en la base operativa de Traslados

Captura Samsung: Arenal Grande 2274 → Aeropuerto Internacional de Carrasco mostraba «Recogida inmediata no disponible» a las 13:47, pese a jornada activa y GPS reciente.

Verificación SQL **de solo lectura** (proyecto `zetaudvvutlouiqxopvg`):
- `public_driver_availability_v1()` a las 13:48: **available=false**. La presencia era válida (`enabled`, jornada activa, sin pausa, ni viaje, ni ocupado manual, heartbeat 10s, GPS 12s, precisión 3.79m). `availability_has_conflict(...,60,null,true)` sí devolvía conflicto.
- La reserva REAL `TR-261012-C112E`, del mismo recorrido, fue creada a las **13:38:37**, `PENDIENTE`, sin presupuesto. La agenda aplica `pending_hold_min=30` como reserva provisional hasta las **14:08:37**; duración provisional 60 minutos, con márgenes 15/30 minutos. No hay bloqueo manual en `driver_schedule_blocks`.
- Nueva consulta 14:24: la misma reserva seguía `PENDIENTE`, pero la respuesta pública ya era **available=true**; además `availability_route_check_v11_4` para **esa misma reserva**, excluyéndose a sí misma, devolvió `reason:AVAILABLE`. El backend no impedía preparar ese presupuesto en Conductor; no hay un error de RPC de Conductor reproducido por estos datos.
- Conductor 11.5-R5: `PREPARAR PRESUPUESTO` para `PENDIENTE` ejecuta `driver_check_route_fit_v11_4`, que excluye la reserva propia y autoriza el diálogo cuando `available=true`. Enviar presupuesto sigue siendo una acción explícita del conductor y **nunca** se ejecuta automáticamente.

## Intervención v65 — solo cliente web

1. Nuevo módulo puro `web-pasajero/owned-pickup.js`: examina **únicamente** las reservas de la sesión autenticada o guest token de ESTE dispositivo ya obtenidas por RPC autorizadas, jamás consulta reservas de terceros. Detecta el mismo origen/destino sin paradas y solicitudes inmediatas recientes que realmente sigan pendientes (≤90 min) o aceptadas (≤6 horas). No usa el GPS ni el teléfono como identidad. No crea ni muta reservas.
2. Si coincide, la tarjeta de recogida muestra **«Tu solicitud está en revisión»**, **«Tenés un presupuesto para responder»** o **«Tu traslado ya está confirmado»**, según el estado real. Botón **Seguir mi solicitud / Revisar presupuesto** abre Mis traslados y la tarjeta correspondiente; durante esta coincidencia se oculta el envío de una duplicación idéntica.
3. La validación previa de **Enviar solicitud** vuelve a comprobar la coincidencia para bloquear dobles envíos por una pantalla rezagada. Distinto origen/destino o solicitud realmente finalizada: flujo de reserva existente intacto.
4. Ante disponibilidad pública `false`, el título ahora es **«Recogida inmediata sin confirmar»**: evita atribuir falsamente el resultado a una desconexión, sin exponer si hay otros pasajeros. Mantiene la posibilidad de enviar **una solicitud pendiente**, nunca promete confirmación.
5. Sin reservas propias conocidas, sin cuenta/token, con ruta distinta, fecha de solicitud vencida, sesión cerrada o sin origen/destino: se conserva la tarjeta de disponibilidad normal.
6. PWA v65: parámetros de caché `app.js?v=65`, `styles.css?v=65`, nuevo `owned-pickup.js?v=1` y service worker `traslados-cliente-pwa-v65`. Pruebas unitarias `owned-pickup-v65-smoke.cjs` más toda la suite anterior.

## Riesgos prevenidos

- El backend conserva **todos** los límites de duplicación/idempotencia, control de agenda, aceptación de presupuestos y restricciones del cliente. No se tocan RPC, esquema, datos operativos, reservas históricas ni las APK firmadas.
- **No se aumenta** la frecuencia de consultas de ETA o Supabase; `loadTrips` continúa 20 s con aplicación visible y solo si ya se cargó la lista.
- No se inventa ETA ni estado de conductor. La animación verde v64 se mantiene en los estados legítimos de espera y llegada confirmada; se oculta cuando se muestra una reserva propia.
- Mapa Trayectos R25.0 y Conductor 11.5-R5 no se recompilan. La APK Cliente Premium v15 WebView usa la misma PWA.
- Nunca crear, cancelar ni cambiar una reserva productiva durante pruebas automáticas.

## QA Samsung y extremo a extremo pendientes

1. En el mismo navegador que creó la reserva reciente, ingresar al mismo origen/destino: debe mostrarse estado de solicitud propia y su código; CTA abre Mis traslados.
2. Otro navegador / visitante sin el token: solo disponibilidad pública, nunca nombres ni códigos de terceros.
3. Cambiar destino: desaparece el aviso de solicitud propia; la capacidad de crear otra solicitud permanece sujeta a backend.
4. Enviar un presupuesto desde Conductor mediante intervención explícita y verificar actualización a «Tenés un presupuesto para responder». Aceptar o rechazar desde cliente y observar cambios solo luego de respuesta real del servidor.
5. Conductor ocupado por agenda y GPS válido: mensaje «Recogida inmediata sin confirmar», sin atribuir la causa técnica ni prometer viaje confirmado.
6. Desconexión, cierre de sesión, regreso y caché de Samsung Internet/WebView, fuentes grandes, accesibilidad, sin desplazamiento lateral, estados archivados.
7. Avisos push con la app totalmente cerrada siguen pendientes de FCM y **no se consideran implementados**.
