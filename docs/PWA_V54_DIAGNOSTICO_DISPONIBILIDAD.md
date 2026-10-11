# Traslados — diagnóstico disponibilidad / ETA y tarjeta premium v54 (11/10/2026)

## Hechos de producción comprobados sin datos personales
Ante captura Samsung 16:36 con tarjeta «Recogida no confirmada por ahora» se siguió el flujo **Mapa → Supabase → RPC disponibilidad → Edge pickup-eta → PWA**.

- Consulta SQL a las 16:41–16:43 UYT: `driver_live_presence`: enabled/shift_active verdaderos, no pausa/ocupación manual/viaje, coordenadas presentes **sin divulgarlas**, heartbeat 11 s, GPS 14 s y exactitud ~3,8 m. **Mapa funciona y envía posición.**
- `public_driver_availability_v1` devuelve `available=false`, porque `availability_has_conflict(...,60,...,true)=true`, incluso con ventana 1 minuto.
- Reserva ACEPTADA (sin nombre, direcciones ni tokens) del 11/10 15:33; al no disponer de ruta/duración en esa comprobación, el motor aplica duración predeterminada **60 min**, que da final previsto 16:33 más **30 min de margen posterior** = ~17:03. La agenda se respeta; no se elimina ni altera la reserva. Otro viaje aceptado a las 10:23 ya no bloqueaba.
- En logs HTTP 19:25–19:50 UTC: 41 `mapa_presence_ping_v1` 200 y 22 `public_driver_availability_v1` 200, **ninguna llamada a `pickup-eta`**: la PWA no calcula ruta si el RPC público indica no disponible, por diseño. Las llamadas 200 NO significan que el negocio contestó disponible.
- No atribuir a fallo del GPS, ni mostrar cifras de ETA cuando existe un conflicto operativo real. Una reserva ACEPTADA no FINALIZADA seguirá ocupando la agenda según las reglas actuales; revisar en Conductor al terminar cada traslado.

## Cambio visual y de comportamiento v54
- Estado real **busy** cuando el servidor responde `available=false`: «Recogida inmediata no disponible» y posibilidad de enviar solicitud para revisión manual.
- Estado **issue** cuando falló la lectura RPC, distinto de agenda no disponible. Estado **checking** al consultar y **routing** exclusivamente durante la petición real de cálculo de ruta; estado **ready** solo al recibir km/min validados.
- Animación compacta de recorrido, dorado suave sobre petróleo; animar únicamente `checking/routing`; demás estados inmóviles. `prefers-reduced-motion` apaga toda animación.
- Mantener refresh de disponibilidad **15s**, ETA **25s en movimiento / 45s detenido**, límites backend, privacidad GPS y experiencia Cliente v15 WebView idéntica a PWA. No alterar Mapa R24.8 ni Agenda/RPC.
- Nuevo módulo `web-pasajero/pickup-observability.js`: historial local en RAM de **hasta 30 eventos** (`step`,`outcome`,`latencyMs`,`at`). Solo códigos permitidos y milisegundos acotados; nunca envía coordenadas, PIN, session tokens, teléfono, IDs, origen/destino ni IP. El parámetro optativo `?diagnostico=1` habilita `console.info` local; `window.TrasladosPickupHealth.summary()` muestra los últimos eventos desde DevTools. No hay persistencia ni telemetría a terceros. En Samsung hay que abrir inspección remota Chrome/WebView para consultar estos datos: NO es un panel administrador ni una notificación pública.
- El backend `pickup-eta` v5 permanece intacto; al bloquear antes de Edge, no deben esperarse entradas de esa función. En otro incidente, empezar por disponibilidad, agenda, GPS y solo después mirar Edge/ORS.

## Pruebas y publicación
**Actualización de despliegue y diagnóstico posterior (19:57 UYT):** Pages build/deploy exitoso en [run 38169989200](https://github.com/marcelofgx-ctrl/traslados-android/actions/runs/38169989200). La agenda ya no presentaba conflictos, pero `shift_active=false`, heartbeat ~35 min atrasado y no había GPS vigente; por ello seguía correctamente no disponible. El test de regresión ahora cubre además HTTP 503 de disponibilidad (`issue`) y recuperación (`ready`). Matriz Samsung y límites de verificación: [QA_SAMSUNG_V54_Y_VERIFICACION_PRODUCCION.md](QA_SAMSUNG_V54_Y_VERIFICACION_PRODUCCION.md). Prueba física pendiente.

- `tests/pickup-health-v54-smoke.cjs` valida privacidad, límites, estados y estilos.
- Regresiones de presencia, ETA, reservas y Cliente Premium v15 en CI antes de publicar. CI no prueba físicamente GPS/Samsung ni reemplaza el manejo de reservas ACEPTADAS.
- Confirmar visualmente en Samsung que no haya superposición de cabecera y tarjeta, que animación solo se active al comprobar y que el estado pueda pasar a ETA válida cuando la agenda lo permita. No crear viajes falsos para simular disponibilidad.

## Métricas recomendadas para investigar de nuevo
- Booleans/edad de heartbeat, GPS, accuracy: nunca consultar lat/lng en diagnóstico compartido.
- Resultado de `availability_has_conflict` y reservas aceptadas próximas sin exponer PII.
- `edge_logs`: recuentos por ruta HTTP y código (sin copiar IP, token o payload del registro).
- Cliente: códigos `availability`/ `eta`, latencia, `rate_limited`/ `route_unavailable`/ `network_error` desde resumen local privado.
