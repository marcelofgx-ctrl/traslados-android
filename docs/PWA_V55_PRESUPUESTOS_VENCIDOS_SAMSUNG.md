# PWA v55 — presupuestos vencidos y mensajes legibles (capturas Samsung 11/10/2026)

## Diagnóstico del sistema existente, sin alterar reservas
- Capturas reales de Mis traslados: una solicitud inmediata del 11/10 a las 08:15 ofrecía un presupuesto UYU 973 a las 20:07; reservas programadas del 09/10 mantenían presupuestos antiguos con botones de aceptar/rechazar.
- Consulta exclusivamente de lectura a `reservations`: existen `status=PENDIENTE`, `quote_status=ENVIADO` y `quote_includes='null'` **como texto**, con importes intactos. No divulgar PII ni IDs.
- Función Supabase existente `customer_quote_decision_v13`: aceptar una solicitud `now/10` creada hace más de **90 minutos** provoca excepción «La solicitud inmediata venció…»; aceptar un horario sin disponibilidad provoca `HORARIO_YA_NO_DISPONIBLE`. Rechazar sigue permitido si status/quote_status lo permiten. **El backend continúa siendo la autoridad**, sin modificaciones de agenda, ventanas, cuota, firma, reservas ni backend en esta versión.
- `customer_list_reservations_v15` y `get_reservation_by_token_v4` no garantizan `pickup_mode` en su respuesta: una PWA no puede deducir con certeza el modo a partir de una hora antigua.

## Implementación PWA v55
- `web-pasajero/trip-board.js`: método puro `expiredQuote(r, nowMs)`. Con `pickup_mode=now/10` conocido, utiliza exactamente el corte del backend de 90 min desde `created_at`. Con `pickup_mode=schedule` conocido, presenta como vencido el horario ya pasado. **Compatibilidad con API antigua sin pickup_mode**: solo marca vencido si el horario del viaje pasó hace al menos 90 minutos, margen conservador que evita clasificar anticipadamente una solicitud inmediata todavía admisible; fechas incompletas/no verificadas nunca expiran por suposición. La validación de fecha aplica huso horario UYT `-03:00`.
- Las tarjetas vencidas se distinguen con `Horario vencido`, conservan el presupuesto y movimientos, no cuentan como nuevos presupuestos por responder ni generan alertas nuevas. Figuran en Historial **solo como clasificación visual**: no se escribe `FINALIZADA`, `CANCELADA`, ni estado alguno al backend. Etiqueta explícita «la solicitud aún figura pendiente de cierre».
- Ocultar **Aceptar** y **Rechazar** presupuesto vencido; ofrecer «Solicitar nuevo traslado» sin envío automático ni cambio de reserva; «Cancelar solicitud» existente se conserva con confirmación y validaciones backend.
- No mostrar `Incluye: null`, `undefined`, `n/a` o guiones aislados, aunque estén serializados como texto; notas reales continúan visibles.
- `showError` traduce `HORARIO_YA_NO_DISPONIBLE`, solicitud inmediata vencida y presupuesto ya respondido a mensajes comprensibles. Tras error de decisión se relee Mis traslados. No se repiten envíos automáticos de reserva.
- Estilo petróleo y dorado intacto; una tarjeta vencida presenta aviso suave, botones compactos en Samsung y un toast legible. `index.html` (app/CSS v55, modelo v4) y service worker `traslados-cliente-pwa-v55` renuevan la caché para Chrome/PWA y **APK Cliente Premium v15 WebView existente**.
- Mapa R24.8, Conductor 11.5-R4, GPS/presencia/ETA y chat con sonido/vibración consentidos quedan intactos. Sin FCM nuevo ni supuesto push con app cerrada.

## Pruebas automatizadas sin tocar producción
- Nuevo `tests/expired-quotes-v55-smoke.cjs`: fechas de las capturas, condiciones desconocidas, horarios futuros, expiración efectiva 90 min, clasificación Historial/Atención, suppression de alertas, UI sin literales `null`, errors traducidos, versiones de assets.
- Integración en `deploy-web-pasajero-pwa.yml` y `check-web-pasajero-review.yml`. Las regresiones previas de presupuesto, chat, presencia/ETA, formulario y paridad Cliente v15 deben seguir exitosas.
- Al fusionar confirmar por separado GitHub Pages desde `main`, no inferir publicación únicamente por CI PR.

## QA física pendiente (Samsung)
1. Actualizar Chrome y Cliente v15 para cargar PWA v55, comprobar paleta premium, filtros/plegables y sin scroll lateral.
2. Abrir Historial: cotizaciones de 9/10 y 11/10 deben figurar «Horario vencido», conservar UYU 980/973 y no mostrar «Incluye: null» ni «Aceptar».
3. En un horario futuro **real y autorizado**, comprobar que hay CTA Aceptar/Rechazar cuando corresponda; si backend marca agenda ocupada, mostrar texto legible y refrescar sin duplicar solicitudes.
4. Confirmar conversaciones, etapa de viaje, notificaciones opcionales con página abierta y presupuestos nuevos; la PWA no controla la burbuja superpuesta nativa de Android. Reubicar esa burbuja corresponde a Mapa y no se interviene desde esta página.
5. No crear viajes de prueba ni cancelar registros históricos sin autorización. Las reservas antiguas quedan abiertas administrativamente hasta resolverlas mediante mecanismos existentes.

## Limitación consciente
Al no incluir `pickup_mode` en listados heredados, un presupuesto programado con horario ya pasado hace menos de 90 minutos podría permanecer visible transitoriamente. El servidor seguirá rechazando su aceptación si no pasa las verificaciones de horario. Un contrato de listado versionado que incluya `pickup_mode` permitirá cerrar esa brecha sin heurísticas en una mejora posterior.
